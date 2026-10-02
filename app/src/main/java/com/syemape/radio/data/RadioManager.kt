package com.syemape.radio.data

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import com.syemape.radio.RadioService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.crow_misia.mediasoup.Consumer
import io.github.crow_misia.mediasoup.Device
import io.github.crow_misia.mediasoup.MediasoupClient
import io.github.crow_misia.mediasoup.Producer
import io.github.crow_misia.mediasoup.RecvTransport
import io.github.crow_misia.mediasoup.SendTransport
import io.github.crow_misia.mediasoup.Transport
import io.github.crow_misia.webrtc.RTCComponentFactory
import io.github.crow_misia.webrtc.RTCLocalAudioManager
import io.github.crow_misia.webrtc.log.DefaultLogHandler
import io.github.crow_misia.webrtc.option.MediaConstraintsOption
import io.socket.client.Ack
import io.socket.emitter.Emitter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.PeerConnectionFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Cliente de radio push-to-talk por mediasoup SFU (interopera con la app RN).
 * Señalización por Socket.IO (/radio): ms:* + channel:join. Un solo hablante por canal.
 */
object RadioManager {
    // ---- Estado observable por Compose ----
    var connected by mutableStateOf(false); private set
    var channelName by mutableStateOf("Canal"); private set
    var members by mutableIntStateOf(0); private set
    var connectedUsers by mutableStateOf<List<MiniUser>>(emptyList()); private set // quién está en vivo
    var talking by mutableStateOf(false); private set        // yo estoy transmitiendo
    var remoteSpeaking by mutableStateOf(false); private set // alguien habla
    var speakerLabel by mutableStateOf<String?>(null); private set // alias de quien habla
    var speakerOn by mutableStateOf(true); private set
    var callVolume by mutableStateOf(1f); private set        // volumen de la radio 0..1
    var txFailed by mutableStateOf(false); private set
    var channels by mutableStateOf<List<RadioChannel>>(emptyList()); private set
    var normalDeviceLabel by mutableStateOf("Teléfono"); private set
    var audioLevel by mutableStateOf(0f); private set        // nivel de voz 0..1 (mueve la onda)

    private val ui = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val worker = Executors.newSingleThreadExecutor()

    private var initialized = false
    private var factory: PeerConnectionFactory? = null
    private var audioManager: RTCLocalAudioManager? = null
    private var constraints: MediaConstraintsOption? = null
    private var device: Device? = null
    private var sendTransport: SendTransport? = null
    private var recvTransport: RecvTransport? = null
    private var producer: Producer? = null
    private val consumers = HashMap<String, Consumer>()
    var channelId: String? = null; private set
    private var appRef: Application? = null
    private var sysAudio: AudioManager? = null

    private val socket get() = Realtime.socket("/radio")

    fun hasMicPermission(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Inicializa WebRTC/mediasoup y el micrófono (una vez). */
    private fun initWebrtc(app: Application) {
        if (initialized) return
        MediasoupClient.initialize(app, DefaultLogHandler)
        val opt = MediaConstraintsOption().apply {
            enableAudioDownstream()
            enableAudioUpstream()
            audioProcessingEchoCancellation = true
            audioProcessingNoiseSuppression = true
            // AGC desactivado: el control automático de ganancia "forzaba" el volumen,
            // amplificando el ruido y saturando la voz. Sin él la voz suena natural.
            audioProcessingAutoGainControl = false
            audioCodec = MediaConstraintsOption.AudioCodec.OPUS
        }
        val comp = RTCComponentFactory(opt)
        val f = comp.createPeerConnectionFactory(app) { _, _ -> }
        val am = comp.createAudioManager()
        am?.initTrack(f, opt)
        runCatching { am?.enabled = false } // micro apagado hasta transmitir (half-duplex)
        factory = f; audioManager = am; constraints = opt
        initialized = true
    }

    @Volatile private var started = false
    @Volatile private var audioSessionActive = false // true SOLO mientras hay voz (foco + modo llamada)
    @Volatile private var consuming = false           // recibiendo voz de alguien

    /** Arranca la radio: entra al canal rápido y prepara WebRTC/mediasoup en 2º plano. */
    fun start(app: Application) {
        if (started) return // ya corriendo (sigue vivo entre pestañas / en 2º plano)
        started = true
        appRef = app
        sysAudio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // Listeners + conexión del socket
        socket.off("ms:newProducer"); socket.off("ms:producerClosed"); socket.off("connect"); socket.off("channel:presence")
        socket.on("channel:presence", Emitter.Listener { args ->
            val o = args.firstOrNull() as? JSONObject ?: return@Listener
            if (o.optString("channelId") == channelId) {
                val count = o.optInt("count", members)
                val arr = o.optJSONArray("users")
                val list = if (arr != null) (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.let {
                        MiniUser(
                            id = it.optString("id"),
                            name = it.optString("name").takeIf { s -> s.isNotBlank() },
                            nickname = it.optString("nickname").takeIf { s -> s.isNotBlank() },
                        )
                    }
                } else null
                ui.launch { members = count; if (list != null) connectedUsers = list }
            }
        })
        socket.on("ms:newProducer", Emitter.Listener { args ->
            val o = args.firstOrNull() as? JSONObject ?: return@Listener
            val producerId = o.optString("producerId", "")
            val label = speakerAliasFrom(o)
            if (producerId.isNotEmpty()) worker.execute { consume(producerId, label) }
        })
        socket.on("ms:producerClosed", Emitter.Listener { worker.execute { closeConsumers() } })
        socket.on("connect", Emitter.Listener {
            worker.execute {
                // En una reconexión los transports/producer del servidor son nuevos:
                // descartamos los viejos (muertos) para que setupMediasoup los rearme.
                setupDone = false
                closeConsumers()
                runCatching { producer?.close() }; producer = null
                runCatching { sendTransport?.close() }; sendTransport = null
                runCatching { recvTransport?.close() }; recvTransport = null
                ui.launch { talking = false }
                channelId?.let { socket.emit("channel:join", it) }
                setupMediasoup()
            }
        })
        if (!socket.connected()) socket.connect()

        // RÁPIDO: cargar canales + unirse + marcar conectado (sin esperar a WebRTC)
        ui.launch {
            val list = runCatching { Backend.api.radioChannels() }.getOrNull().orEmpty()
            channels = list
            speakerOn = Prefs.speakerOn
            callVolume = Prefs.callVolume
            val saved = Prefs.lastChannelId
            val ch = list.firstOrNull { it.id == saved }
                ?: list.firstOrNull { it.joined }
                ?: list.firstOrNull() ?: return@launch
            channelId = ch.id
            channelName = listOfNotNull(ch.name, ch.description).joinToString(" · ").ifBlank { "Canal" }
            members = ch.memberCount
            socket.emit("channel:join", ch.id)
            connected = true
            registerAudioCallback()
            RadioService.start(app, channelName)
            // Watchdog: mientras HAY voz activa, re-asienta modo/volumen que el
            // sistema puede resetear. En silencio no tocamos el audio del sistema
            // (así otras apps conservan su micrófono y su sonido).
            ui.launch {
                while (started) {
                    delay(2000)
                    if (connected) keepAudioAlive()
                }
            }
            startLevelLoop()
            // LENTO: WebRTC + mediasoup en segundo plano
            worker.execute {
                runCatching { initWebrtc(app) }.onFailure { return@execute }
                setupMediasoup()
            }
        }
    }

    /** Cambia de canal: UI instantánea; deja de oír el anterior y se une al nuevo en 2º plano. */
    fun selectChannel(id: String) {
        if (id == channelId || id.isBlank()) return
        val prev = channelId
        // UI instantánea (en el hilo que llama, normalmente Main)
        channelId = id
        Prefs.lastChannelId = id
        channels.firstOrNull { it.id == id }?.let { ch ->
            channelName = listOfNotNull(ch.name, ch.description).joinToString(" · ").ifBlank { "Canal" }
            members = ch.memberCount
            connectedUsers = emptyList() // se repuebla con el channel:presence del canal nuevo
        }
        talking = false; remoteSpeaking = false; speakerLabel = null; txFailed = false
        RadioService.update(appRef, channelName)
        // Trabajo de red/mediasoup en segundo plano (no bloquea la UI)
        worker.execute {
            closeConsumers()
            runCatching { producer?.close() }; producer = null
            prev?.let { socket.emit("channel:leave", it) }
            socket.emit("channel:join", id)
            val cur = ack("ms:getProducer", JSONObject().put("channelId", id)) as? JSONObject
            val curProducer = cur?.optString("producerId")?.takeIf { it.isNotEmpty() }
            if (curProducer != null) consume(curProducer, cur?.let { speakerAliasFrom(it) } ?: "Alguien del canal")
        }
    }

    @Volatile private var setupDone = false

    private fun setupMediasoup() {
        // Listo solo si ambos transports existen; si no, reintenta lo que falte.
        if (setupDone && sendTransport != null && recvTransport != null) return
        val cid = channelId ?: return
        val f = factory ?: return
        val caps = ack("ms:rtpCapabilities") as? JSONObject ?: return
        val dev = device ?: Device(f).also { device = it }
        if (!dev.loaded) runCatching { dev.load(caps.toString()) }

        // recvTransport
        if (recvTransport == null) {
            val recvInfo = ack("ms:createTransport", JSONObject().put("direction", "recv")) as? JSONObject
            if (recvInfo != null && !recvInfo.has("error")) {
                recvTransport = dev.createRecvTransport(
                    object : RecvTransport.Listener {
                        override fun onConnect(transport: Transport, dtlsParameters: String) {
                            ack("ms:connectTransport", JSONObject().put("direction", "recv").put("dtlsParameters", JSONObject(dtlsParameters)))
                        }
                        override fun onConnectionStateChange(transport: Transport, connectionState: String) {}
                    },
                    recvInfo.getString("id"),
                    recvInfo.getJSONObject("iceParameters").toString(),
                    recvInfo.getJSONArray("iceCandidates").toString(),
                    recvInfo.getJSONObject("dtlsParameters").toString(),
                )
            }
        }

        // sendTransport (pre-armado)
        if (sendTransport == null) {
            val sendInfo = ack("ms:createTransport", JSONObject().put("direction", "send")) as? JSONObject
            if (sendInfo != null && !sendInfo.has("error")) {
                sendTransport = dev.createSendTransport(
                    object : SendTransport.Listener {
                        override fun onConnect(transport: Transport, dtlsParameters: String) {
                            ack("ms:connectTransport", JSONObject().put("direction", "send").put("dtlsParameters", JSONObject(dtlsParameters)))
                        }
                        override fun onConnectionStateChange(transport: Transport, connectionState: String) {}
                        override fun onProduce(transport: Transport, kind: String, rtpParameters: String, appData: String?): String {
                            // Usa el canal ACTUAL (no el capturado al crear el transport): si no,
                            // tras cambiar de canal se produciría en el canal viejo y no se transmite.
                            val ch = channelId ?: cid
                            val r = ack("ms:produce", JSONObject().put("channelId", ch).put("rtpParameters", JSONObject(rtpParameters))) as? JSONObject
                            return r?.optString("id")?.takeIf { it.isNotEmpty() } ?: throw RuntimeException("produce failed")
                        }
                        override fun onProduceData(transport: Transport, sctpStreamParameters: String, label: String, protocol: String, appData: String?): String = ""
                    },
                    sendInfo.getString("id"),
                    sendInfo.getJSONObject("iceParameters").toString(),
                    sendInfo.getJSONArray("iceCandidates").toString(),
                    sendInfo.getJSONObject("dtlsParameters").toString(),
                )
            }
        }

        // Marcar listo solo cuando ambos transports quedaron armados (si no, se reintenta).
        setupDone = sendTransport != null && recvTransport != null

        // consumir al hablante actual si hay
        val cur = ack("ms:getProducer", JSONObject().put("channelId", cid)) as? JSONObject
        val curProducer = cur?.optString("producerId")?.takeIf { it.isNotEmpty() }
        if (curProducer != null) consume(curProducer, cur?.let { speakerAliasFrom(it) } ?: "Alguien del canal")
    }

    /** Extrae el alias del hablante del payload (requiere que el backend incluya `user`). */
    private fun speakerAliasFrom(o: JSONObject): String {
        val u = o.optJSONObject("user") ?: return "Alguien del canal"
        val nick = u.optString("nickname").takeIf { it.isNotBlank() }
        val name = u.optString("name").takeIf { it.isNotBlank() }
        return nick ?: name ?: "Alguien del canal"
    }

    private fun consume(producerId: String, speaker: String) {
        val recv = recvTransport ?: return
        val dev = device ?: return
        val info = ack("ms:consume", JSONObject().put("producerId", producerId).put("rtpCapabilities", JSONObject(dev.rtpCapabilities))) as? JSONObject
        if (info == null || info.has("error")) return
        val consumer = recv.consume(
            object : Consumer.Listener {
                override fun onTransportClose(consumer: Consumer) {}
            },
            info.getString("id"),
            info.getString("producerId"),
            info.getString("kind"),
            info.getJSONObject("rtpParameters").toString(),
        )
        consumers[consumer.id] = consumer
        ack("ms:resume", JSONObject().put("consumerId", consumer.id))
        // Toma el audio del sistema (foco + modo llamada + ruta) para reproducir la voz.
        consuming = true
        acquireAudioSession()
        ui.launch { remoteSpeaking = true; speakerLabel = speaker }
    }

    private fun closeConsumers() {
        consumers.values.forEach { runCatching { it.close() } }
        consumers.clear()
        consuming = false
        releaseAudioSession() // dejé de recibir: libera el audio si tampoco estoy hablando
        ui.launch { remoteSpeaking = false; speakerLabel = null }
    }

    /** Pitido corto tipo walkie-talkie (inicio/fin de transmisión). */
    private fun beep(tone: Int, durationMs: Int) {
        worker.execute {
            runCatching {
                val tg = android.media.ToneGenerator(AudioManager.STREAM_VOICE_CALL, 90)
                tg.startTone(tone, durationMs)
                Thread.sleep((durationMs + 60).toLong())
                tg.release()
            }
        }
    }

    fun startTalking() {
        if (remoteSpeaking) return
        // Feedback instantáneo (sin esperar al handshake de produce): UI + micro + pitido.
        talking = true
        txFailed = false
        acquireAudioSession() // toma el audio del sistema solo ahora que voy a hablar
        runCatching { audioManager?.enabled = true } // activa el micrófono ya
        beep(android.media.ToneGenerator.TONE_PROP_BEEP, 150) // pitido de inicio
        worker.execute {
            fun fail() { ui.launch { talking = false; txFailed = true }; runCatching { audioManager?.enabled = false } }
            // Si se presionó HABLAR antes de que WebRTC/mediasoup terminara de armarse
            // (o si un intento previo falló), lo preparamos ahora en vez de rendirnos.
            if (sendTransport == null) {
                appRef?.let { runCatching { initWebrtc(it) } }
                runCatching { setupMediasoup() }
            }
            if (!talking) return@execute // el usuario soltó durante el setup
            val send = sendTransport ?: return@execute fail()
            val track = audioManager?.track ?: return@execute fail()
            try {
                producer = send.produce(object : Producer.Listener {
                    override fun onTransportClose(producer: Producer) {}
                }, track)
            } catch (e: Exception) {
                fail()
            }
        }
    }

    fun stopTalking() {
        if (!talking) return
        talking = false // instantáneo
        runCatching { audioManager?.enabled = false } // silencia el micrófono ya
        beep(android.media.ToneGenerator.TONE_PROP_BEEP2, 120) // pitido "roger" al soltar
        worker.execute {
            runCatching { producer?.close() }
            producer = null
            socket.emit("ms:closeProducer")
            releaseAudioSession() // terminé de hablar: libera el audio a otras apps
        }
    }

    fun toggleSpeaker() {
        speakerOn = !speakerOn
        Prefs.speakerOn = speakerOn
        if (audioSessionActive) applyAudioRoute() // solo si hay voz; si no, basta con guardar la preferencia
    }

    /** Fija la salida: altavoz (true) o el dispositivo "normal" (false). */
    fun setSpeaker(on: Boolean) {
        if (speakerOn == on) return
        speakerOn = on
        Prefs.speakerOn = on
        if (audioSessionActive) applyAudioRoute()
    }

    /** Fija el volumen de la radio (0..1) sobre STREAM_VOICE_CALL. */
    fun setVolume(fraction: Float) {
        val f = fraction.coerceIn(0f, 1f)
        if (f == callVolume) return
        callVolume = f
        Prefs.callVolume = f
        applyStreamVolume()
    }

    /** Aplica [callVolume] al índice real de STREAM_VOICE_CALL. */
    private fun applyStreamVolume() {
        val am = sysAudio ?: return
        runCatching {
            val stream = AudioManager.STREAM_VOICE_CALL
            val max = am.getStreamMaxVolume(stream)
            val idx = Math.round(callVolume * max).coerceIn(0, max)
            am.setStreamVolume(stream, idx, 0)
        }
    }

    @Volatile private var levelPolling = false

    /**
     * Sondea el nivel de audio real (WebRTC getStats → "audioLevel") ~cada 120 ms
     * mientras hay voz, y lo suaviza para alimentar la onda del sintonizador.
     */
    private fun startLevelLoop() {
        if (levelPolling) return
        levelPolling = true
        ui.launch {
            while (started) {
                val raw = when {
                    talking -> withContext(Dispatchers.IO) { runCatching { parseAudioLevel(producer?.stats) }.getOrNull() }
                    remoteSpeaking -> withContext(Dispatchers.IO) { runCatching { parseAudioLevel(consumers.values.firstOrNull()?.stats) }.getOrNull() }
                    else -> 0f
                } ?: 0f
                // audioLevel de WebRTC es RMS (voz ≈ 0..0.3): lo amplificamos y suavizamos.
                val target = (raw * 3.4f).coerceIn(0f, 1f)
                audioLevel += (target - audioLevel) * 0.45f
                delay(120)
            }
            audioLevel = 0f
            levelPolling = false
        }
    }

    /** Extrae el mayor "audioLevel" (0..1) del RTCStatsReport JSON de WebRTC. */
    private fun parseAudioLevel(statsJson: String?): Float? {
        if (statsJson.isNullOrBlank()) return null
        return runCatching {
            val arr = JSONArray(statsJson)
            var level = -1.0
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                if (o.has("audioLevel")) level = maxOf(level, o.optDouble("audioLevel", 0.0))
            }
            if (level < 0) null else level.toFloat()
        }.getOrNull()
    }

    private var audioCallback: android.media.AudioDeviceCallback? = null

    private fun registerAudioCallback() {
        val am = sysAudio ?: return
        if (audioCallback != null) return
        val cb = object : android.media.AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>?) { if (audioSessionActive) applyAudioRoute() }
            override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>?) { if (audioSessionActive) applyAudioRoute() }
        }
        audioCallback = cb
        runCatching { am.registerAudioDeviceCallback(cb, null) }
    }

    /** Enruta el audio de la llamada: altavoz, o el mejor dispositivo "normal" (BT > cable > auricular). */
    private fun applyAudioRoute() {
        val am = sysAudio ?: return
        runCatching {
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val devices = am.availableCommunicationDevices
                fun firstOf(vararg types: Int) = devices.firstOrNull { it.type in types }
                val bt = firstOf(android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO, android.media.AudioDeviceInfo.TYPE_BLE_HEADSET)
                val wired = firstOf(
                    android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
                )
                val earpiece = firstOf(android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)
                val speaker = firstOf(android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
                val target = if (speakerOn) speaker else (bt ?: wired ?: earpiece ?: speaker)
                target?.let { am.setCommunicationDevice(it) }
                val label = when {
                    bt != null -> "Bluetooth"
                    wired != null -> "Auricular"
                    else -> "Teléfono"
                }
                ui.launch { normalDeviceLabel = label }
            } else {
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = speakerOn
            }
            // WebRTC reproduce por STREAM_VOICE_CALL en MODE_IN_COMMUNICATION:
            // aplica el volumen elegido por el usuario (persistido).
            applyStreamVolume()
        }
    }

    private var focusRequest: android.media.AudioFocusRequest? = null

    /** Toma el foco de audio para que otras apps no bajen el volumen de la radio. */
    private fun requestAudioFocus() {
        val am = sysAudio ?: return
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        if (focusRequest != null) return
        runCatching {
            val attrs = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            // TRANSIENT: al soltar el foco, otras apps (música, etc.) pueden reanudar.
            val req = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attrs)
                .setWillPauseWhenDucked(false)
                .build()
            focusRequest = req
            am.requestAudioFocus(req)
        }
    }

    private fun abandonAudioFocus() {
        val am = sysAudio ?: return
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                focusRequest?.let { am.abandonAudioFocusRequest(it) }
            }
        }
        focusRequest = null
    }

    /**
     * Toma el audio del sistema (foco + modo llamada + ruta) SOLO mientras hay voz
     * (al hablar o al recibir). Así, en silencio, el micrófono y el sonido quedan
     * libres para otras apps.
     */
    private fun acquireAudioSession() {
        if (audioSessionActive) { applyAudioRoute(); return }
        audioSessionActive = true
        requestAudioFocus()
        applyAudioRoute() // MODE_IN_COMMUNICATION + ruta + volumen
    }

    /** Libera el audio del sistema cuando ya no hay voz (ni hablo ni recibo). */
    private fun releaseAudioSession() {
        if (talking || consuming) return // sigue habiendo voz
        if (!audioSessionActive) return
        audioSessionActive = false
        abandonAudioFocus()
        val am = sysAudio ?: return
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) am.clearCommunicationDevice()
            am.mode = AudioManager.MODE_NORMAL
        }
    }

    /**
     * Re-asienta modo de comunicación y volumen de llamada SIN re-seleccionar el
     * dispositivo (evita glitches). El sistema resetea el modo a NORMAL entre
     * transmisiones y eso baja el volumen; este watchdog lo mantiene.
     */
    private fun keepAudioAlive() {
        if (!audioSessionActive) return // en silencio no tocamos el audio del sistema
        val am = sysAudio ?: return
        runCatching {
            if (am.mode != AudioManager.MODE_IN_COMMUNICATION) am.mode = AudioManager.MODE_IN_COMMUNICATION
            val stream = AudioManager.STREAM_VOICE_CALL
            val max = am.getStreamMaxVolume(stream)
            val target = Math.round(callVolume * max).coerceIn(0, max)
            if (am.getStreamVolume(stream) != target) am.setStreamVolume(stream, target, 0)
        }
    }

    fun stop() {
        worker.execute {
            runCatching { producer?.close() }; producer = null
            closeConsumers()
            runCatching { sendTransport?.close() }; sendTransport = null
            runCatching { recvTransport?.close() }; recvTransport = null
            runCatching { device?.dispose() }; device = null
            channelId?.let { socket.emit("channel:leave", it) }
            socket.off("ms:newProducer"); socket.off("ms:producerClosed"); socket.off("connect")
            abandonAudioFocus()
            runCatching {
                audioCallback?.let { sysAudio?.unregisterAudioDeviceCallback(it) }; audioCallback = null
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) sysAudio?.clearCommunicationDevice()
                sysAudio?.mode = AudioManager.MODE_NORMAL
            }
            RadioService.stop(appRef)
            setupDone = false
            started = false
            audioSessionActive = false
            consuming = false
            ui.launch { connected = false; talking = false; remoteSpeaking = false; speakerLabel = null; audioLevel = 0f; connectedUsers = emptyList() }
        }
    }

    /** Emite un evento y espera el ack (bloqueante, en hilo worker). */
    private fun ack(event: String, payload: Any? = null, timeoutMs: Long = 8000): Any? {
        val latch = CountDownLatch(1)
        val holder = arrayOfNulls<Any>(1)
        val cb = Ack { args -> holder[0] = args.firstOrNull(); latch.countDown() }
        when (payload) {
            null -> socket.emit(event, cb)
            else -> socket.emit(event, payload, cb)
        }
        latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        return holder[0]
    }
}
