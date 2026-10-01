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
    var talking by mutableStateOf(false); private set        // yo estoy transmitiendo
    var remoteSpeaking by mutableStateOf(false); private set // alguien habla
    var speakerLabel by mutableStateOf<String?>(null); private set // alias de quien habla
    var speakerOn by mutableStateOf(true); private set
    var txFailed by mutableStateOf(false); private set
    var channels by mutableStateOf<List<RadioChannel>>(emptyList()); private set
    var normalDeviceLabel by mutableStateOf("Teléfono"); private set

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
            audioProcessingAutoGainControl = true
            audioCodec = MediaConstraintsOption.AudioCodec.OPUS
        }
        val comp = RTCComponentFactory(opt)
        val f = comp.createPeerConnectionFactory(app) { _, _ -> }
        val am = comp.createAudioManager()
        am?.initTrack(f, opt)
        factory = f; audioManager = am; constraints = opt
        initialized = true
    }

    @Volatile private var started = false

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
                ui.launch { members = count }
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
            worker.execute { setupDone = false; channelId?.let { socket.emit("channel:join", it) }; setupMediasoup() }
        })
        if (!socket.connected()) socket.connect()

        // RÁPIDO: cargar canales + unirse + marcar conectado (sin esperar a WebRTC)
        ui.launch {
            val list = runCatching { Backend.api.radioChannels() }.getOrNull().orEmpty()
            channels = list
            speakerOn = Prefs.speakerOn
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
            requestAudioFocus()
            applyAudioRoute()
            RadioService.start(app, channelName)
            // Watchdog: el sistema resetea el modo de audio y baja el volumen;
            // lo re-asentamos periódicamente mientras la radio está activa.
            ui.launch {
                while (started) {
                    delay(2000)
                    if (connected) keepAudioAlive()
                }
            }
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
        if (setupDone) return
        val cid = channelId ?: return
        val f = factory ?: return
        val caps = ack("ms:rtpCapabilities") as? JSONObject ?: return
        setupDone = true
        val dev = device ?: Device(f).also { device = it }
        if (!dev.loaded) dev.load(caps.toString())

        // recvTransport
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

        // sendTransport (pre-armado)
        val sendInfo = ack("ms:createTransport", JSONObject().put("direction", "send")) as? JSONObject
        if (sendInfo != null && !sendInfo.has("error")) {
            sendTransport = dev.createSendTransport(
                object : SendTransport.Listener {
                    override fun onConnect(transport: Transport, dtlsParameters: String) {
                        ack("ms:connectTransport", JSONObject().put("direction", "send").put("dtlsParameters", JSONObject(dtlsParameters)))
                    }
                    override fun onConnectionStateChange(transport: Transport, connectionState: String) {}
                    override fun onProduce(transport: Transport, kind: String, rtpParameters: String, appData: String?): String {
                        val r = ack("ms:produce", JSONObject().put("channelId", cid).put("rtpParameters", JSONObject(rtpParameters))) as? JSONObject
                        return r?.optString("id") ?: throw RuntimeException("produce failed")
                    }
                    override fun onProduceData(transport: Transport, sctpStreamParameters: String, label: String, protocol: String, appData: String?): String = ""
                },
                sendInfo.getString("id"),
                sendInfo.getJSONObject("iceParameters").toString(),
                sendInfo.getJSONArray("iceCandidates").toString(),
                sendInfo.getJSONObject("dtlsParameters").toString(),
            )
        }

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
        // Re-aserta la ruta y el volumen de llamada: evita que el audio baje
        // de volumen tras la primera transmisión (Android degrada la ruta al idle).
        applyAudioRoute()
        ui.launch { remoteSpeaking = true; speakerLabel = speaker }
    }

    private fun closeConsumers() {
        consumers.values.forEach { runCatching { it.close() } }
        consumers.clear()
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
        beep(android.media.ToneGenerator.TONE_PROP_BEEP, 150) // pitido de inicio
        worker.execute {
            val send = sendTransport ?: return@execute
            val track = audioManager?.track ?: return@execute
            try {
                producer = send.produce(object : Producer.Listener {
                    override fun onTransportClose(producer: Producer) {}
                }, track)
                ui.launch { talking = true; txFailed = false }
            } catch (e: Exception) {
                ui.launch { txFailed = true }
            }
        }
    }

    fun stopTalking() {
        beep(android.media.ToneGenerator.TONE_PROP_BEEP2, 120) // pitido "roger" al soltar
        worker.execute {
            runCatching { producer?.close() }
            producer = null
            socket.emit("ms:closeProducer")
            ui.launch { talking = false }
        }
    }

    fun toggleSpeaker() {
        speakerOn = !speakerOn
        Prefs.speakerOn = speakerOn
        applyAudioRoute()
    }

    private var audioCallback: android.media.AudioDeviceCallback? = null

    private fun registerAudioCallback() {
        val am = sysAudio ?: return
        if (audioCallback != null) return
        val cb = object : android.media.AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>?) = applyAudioRoute()
            override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>?) = applyAudioRoute()
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
            // mantenlo al máximo para que el audio no baje de volumen con el tiempo.
            runCatching {
                val stream = AudioManager.STREAM_VOICE_CALL
                am.setStreamVolume(stream, am.getStreamMaxVolume(stream), 0)
            }
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
            val req = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
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
     * Re-asienta modo de comunicación y volumen de llamada SIN re-seleccionar el
     * dispositivo (evita glitches). El sistema resetea el modo a NORMAL entre
     * transmisiones y eso baja el volumen; este watchdog lo mantiene.
     */
    private fun keepAudioAlive() {
        val am = sysAudio ?: return
        runCatching {
            if (am.mode != AudioManager.MODE_IN_COMMUNICATION) am.mode = AudioManager.MODE_IN_COMMUNICATION
            val stream = AudioManager.STREAM_VOICE_CALL
            val max = am.getStreamMaxVolume(stream)
            if (am.getStreamVolume(stream) < max) am.setStreamVolume(stream, max, 0)
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
            ui.launch { connected = false; talking = false; remoteSpeaking = false; speakerLabel = null }
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
