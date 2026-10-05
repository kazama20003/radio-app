package com.syemape.radio.data

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import com.syemape.radio.BuildConfig
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
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
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
    var lastVoiceNote by mutableStateOf<RadioTransmission?>(null); private set // última nota de voz del canal
    var netOnline by mutableStateOf(true); private set       // hay internet (ConnectivityManager)

    private val ui = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val worker = Executors.newSingleThreadExecutor()
    // Dispatcher sobre el MISMO hilo worker: permite leer stats nativos serializados
    // con produce/consume/close (nunca dos hilos tocando WebRTC a la vez → sin crash).
    private val workerDispatcher = worker.asCoroutineDispatcher()

    private var initialized = false
    private var factory: PeerConnectionFactory? = null
    private var audioManager: RTCLocalAudioManager? = null
    private var constraints: MediaConstraintsOption? = null
    private var device: Device? = null
    private var sendTransport: SendTransport? = null
    private var recvTransport: RecvTransport? = null
    private var producer: Producer? = null
    private val consumers = ConcurrentHashMap<String, Consumer>()
    var channelId: String? = null; private set
    @Volatile private var remoteProducerId: String? = null
    @Volatile private var consumingProducerId: String? = null
    @Volatile private var reservationsSupported: Boolean? = null
    @Volatile private var openChatChannelId: String? = null
    private var appRef: Application? = null
    private var sysAudio: AudioManager? = null

    private val socket get() = Realtime.socket("/radio")

    fun hasMicPermission(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Inicializa WebRTC/mediasoup y el micrófono (una vez). */
    private fun initWebrtc(app: Application) {
        if (initialized) return
        val t0 = System.currentTimeMillis()
        MediasoupClient.initialize(app, DefaultLogHandler)
        val opt = MediaConstraintsOption().apply {
            enableAudioDownstream()
            enableAudioUpstream()
            audioProcessingEchoCancellation = true
            // Supresión de ruido fuerte: quita el ruido de fondo/externo para que
            // el AGC no lo amplifique y solo suba la voz.
            audioProcessingNoiseSuppression = true
            // AGC reactivado: da volumen (voz fuerte, como walkie-talkie). Junto con
            // la supresión de ruido, sube la voz sin amplificar el ruido de fondo.
            audioProcessingAutoGainControl = true
            audioCodec = MediaConstraintsOption.AudioCodec.OPUS
        }
        val comp = RTCComponentFactory(opt)
        val f = comp.createPeerConnectionFactory(app) { _, _ -> }
        val am = comp.createAudioManager()
        am?.initTrack(f, opt)
        runCatching { am?.enabled = false } // micro apagado hasta transmitir (half-duplex)
        factory = f; audioManager = am; constraints = opt
        initialized = true
        android.util.Log.d(TAG, "initWebrtc listo en ${System.currentTimeMillis() - t0}ms")
    }

    @Volatile private var started = false
    @Volatile private var audioSessionActive = false // true SOLO mientras hay voz (foco + modo llamada)
    @Volatile private var consuming = false           // recibiendo voz de alguien

    /** Mi propio usuario como MiniUser (para mostrarme conectado al instante). */
    private fun meUser(): MiniUser? {
        val u = SessionManager.user ?: return null
        return MiniUser(id = u.id, name = u.name, nickname = u.nickname)
    }

    /** Arranca la radio: entra al canal rápido y prepara WebRTC/mediasoup en 2º plano. */
    fun start(app: Application) {
        if (started) return // ya corriendo (sigue vivo entre pestañas / en 2º plano)
        started = true
        appRef = app
        sysAudio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        registerConnectivity(app) // estado de internet para el indicador de señal
        // Listeners + conexión del socket
        socket.off("ms:newProducer"); socket.off("ms:producerClosed"); socket.off("connect"); socket.off("disconnect"); socket.off("channel:presence")
        socket.on("channel:presence", Emitter.Listener { args ->
            val o = args.firstOrNull() as? JSONObject ?: return@Listener
            if (o.optString("channelId") == channelId) {
                val count = o.optInt("count", members)
                val arr = o.optJSONArray("users")
                val list = if (arr != null) (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.let { obj ->
                        // Android convierte el JSON null al texto "null": lo tratamos como null real.
                        fun str(k: String): String? =
                            if (obj.isNull(k)) null else obj.optString(k).takeIf { s -> s.isNotBlank() && s != "null" }
                        MiniUser(id = obj.optString("id"), name = str("name"), nickname = str("nickname"))
                    }
                } else null
                ui.launch { members = count; if (list != null) connectedUsers = list }
            }
        })
        socket.on("ms:newProducer", Emitter.Listener { args ->
            val o = args.firstOrNull() as? JSONObject ?: return@Listener
            // El mismo socket también entra temporalmente a salas desde el chat del canal.
            // Nunca consumas audio que no pertenezca al canal sintonizado.
            val eventChannel = o.optString("channelId")
            if (eventChannel.isNotBlank() && eventChannel != channelId) return@Listener
            if (eventChannel.isBlank() && openChatChannelId != null && openChatChannelId != channelId) return@Listener
            val producerId = o.optString("producerId", "")
            val label = speakerAliasFrom(o)
            if (producerId.isNotEmpty()) {
                showRemoteSpeaker(producerId, label)
                worker.execute { consume(producerId, label) }
            }
        })
        socket.on("ms:producerClosed", Emitter.Listener { args ->
            val o = args.firstOrNull() as? JSONObject ?: return@Listener
            val producerId = o.optString("producerId")
            val eventChannel = o.optString("channelId")
            val channelMatches = eventChannel == channelId ||
                (eventChannel.isBlank() && (openChatChannelId == null || openChatChannelId == channelId))
            if (channelMatches && remoteProducerId != null && (producerId.isBlank() || producerId == remoteProducerId)) {
                remoteProducerId = null
                worker.execute { closeConsumers() }
            }
        })
        // Última nota de voz grabada en el canal (para el botón de "escuchar última nota").
        socket.on("ptt:ended", Emitter.Listener { args ->
            val o = args.firstOrNull() as? JSONObject ?: return@Listener
            if (o.optString("channelId") != channelId) return@Listener
            if (!o.has("transmission")) return@Listener
            val t = runCatching { Realtime.gson.fromJson(o.getJSONObject("transmission").toString(), RadioTransmission::class.java) }.getOrNull()
                ?: return@Listener
            if (!t.audioKey.isNullOrBlank()) ui.launch { lastVoiceNote = t }
        })
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
                runCatching { setupMediasoup() } // rearma transportes tras reconectar
                ui.launch { connected = isChannelReady() } // En vivo solo si el audio quedó listo
            }
        })
        // El socket se cayó: refleja "desconectado" en la UI (ya no mentimos "En vivo").
        socket.on("disconnect", Emitter.Listener { ui.launch { connected = false } })
        if (!socket.connected()) socket.connect()

        speakerOn = Prefs.speakerOn
        callVolume = Prefs.callVolume
        registerAudioCallback()
        autoSelectOutput() // elige salida según haya BT/audífono (incluso si ya estaba conectado)

        // INMEDIATO: entra al ÚLTIMO canal guardado sin esperar la lista REST. Así la
        // presencia, "conectado" y la última nota aparecen al instante (antes todo esto
        // esperaba a que terminara una llamada REST lenta → 10-15s de "Conectando…").
        Prefs.lastChannelId?.let { savedId ->
            enterChannelNow(app, savedId, Prefs.lastChannelName ?: "Canal")
        }

        // Bucles de fondo (watchdog de conexión/audio, nivel de voz, WebRTC) — una sola vez.
        startBackgroundLoops(app)

        // EN PARALELO: lista de canales (chips + nombre real). Si no había canal guardado,
        // elige uno y entra ahora; si ya entramos, solo corrige el nombre mostrado.
        ui.launch {
            val list = runCatching { Backend.api.radioChannels() }.getOrNull().orEmpty()
            channels = list
            if (channelId == null) {
                val ch = list.firstOrNull { it.joined } ?: list.firstOrNull() ?: return@launch
                enterChannelNow(app, ch.id, listOfNotNull(ch.name, ch.description).joinToString(" · ").ifBlank { "Canal" })
            } else {
                list.firstOrNull { it.id == channelId }?.let { ch ->
                    val nm = listOfNotNull(ch.name, ch.description).joinToString(" · ").ifBlank { "Canal" }
                    channelName = nm
                    Prefs.lastChannelName = nm
                    RadioService.update(appRef, nm)
                }
            }
        }
    }

    /** Entra a un canal YA: fija estado, se une por socket, marca EN LÍNEA y carga la última nota. */
    private fun enterChannelNow(app: Application, id: String, name: String) {
        channelId = id
        channelName = name
        Prefs.lastChannelId = id
        Prefs.lastChannelName = name
        val me = meUser()
        connectedUsers = me?.let { listOf(it) } ?: emptyList()
        members = if (me != null) 1 else 0
        socket.emit("channel:join", id)
        // OJO: NO marcamos connected=true aquí. "connected" (En vivo) solo es true cuando
        // el canal de audio está REALMENTE listo (socket + transportes), para que si dice
        // "En vivo" hablar funcione y se grabe. Lo pone true el armado de WebRTC/watchdog.
        refreshLastVoiceNote(id)
        RadioService.start(app, channelName)
        // En el primer arranque no hay canal guardado y el setup inicial ocurre antes
        // de cargar la lista REST. Arranca los transports en cuanto ya conocemos el id.
        worker.execute {
            runCatching { initWebrtc(app) }
            runCatching { setupMediasoup() }
            ui.launch { connected = isChannelReady() }
        }
    }

    /** Lanza una sola vez: watchdog de conexión/audio, loop de nivel y armado de WebRTC. */
    private fun startBackgroundLoops(app: Application) {
        ui.launch {
            while (started) {
                delay(2000)
                // "connected" (En vivo) = canal REALMENTE listo (socket + transportes de
                // audio). Así, si dice En vivo, hablar funciona y se graba.
                val ready = isChannelReady()
                if (connected != ready) connected = ready
                if (ready) keepAudioAlive()
                ensureSocketAlive() // si el socket se cayó en 2º plano, lo levanta
                // Auto-cura el "canal muerto": socket arriba pero sin transportes → rearmar.
                if (socket.connected() && (sendTransport == null || recvTransport == null)) {
                    worker.execute {
                        runCatching { setupMediasoup() }
                        ui.launch { connected = isChannelReady() }
                    }
                }
            }
        }
        startLevelLoop()
        worker.execute {
            runCatching { initWebrtc(app) }.onFailure { return@execute }
            runCatching { setupMediasoup() }
            ui.launch { connected = isChannelReady() } // En vivo cuando el audio quedó listo
        }
    }

    private var connectivityCb: android.net.ConnectivityManager.NetworkCallback? = null

    /** Observa si hay internet (para el indicador de señal) y reconecta al volver la red. */
    private fun registerConnectivity(app: Application) {
        if (connectivityCb != null) return
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return
        netOnline = runCatching {
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        }.getOrDefault(true)
        val cb = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                ui.launch { netOnline = true }
                ensureSocketAlive() // al volver la red, reengancha el socket de inmediato
            }
            override fun onLost(network: android.net.Network) { ui.launch { netOnline = false } }
        }
        connectivityCb = cb
        runCatching { cm.registerDefaultNetworkCallback(cb) }
    }

    /** Reconecta el socket si se cayó (el listener 'connect' rearma todo). Barato. */
    private fun ensureSocketAlive() {
        if (!started) return
        runCatching { if (!socket.connected()) socket.connect() }
    }

    /**
     * Resincroniza al volver la app a primer plano: reconecta si hace falta, re-entra
     * al canal y reconsume al que esté hablando (por si nos perdimos un `ms:newProducer`
     * mientras el SO nos tuvo suspendidos). Seguro de llamar siempre.
     */
    fun ensureAlive() {
        if (!started) return
        worker.execute {
            if (!socket.connected()) {
                runCatching { socket.connect() } // 'connect' rearma join + transports
                return@execute
            }
            val cid = channelId ?: return@execute
            socket.emit("channel:join", cid)
            if (consumers.isEmpty()) {
                val cur = ack("ms:getProducer", JSONObject().put("channelId", cid)) as? JSONObject
                val pid = cur?.optString("producerId")?.takeIf { it.isNotEmpty() }
                if (pid != null) consume(pid, cur?.let { speakerAliasFrom(it) } ?: "Alguien del canal")
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
            Prefs.lastChannelName = channelName
            // Yo, al instante; la presencia del canal nuevo completa el resto.
            val me = meUser()
            connectedUsers = me?.let { listOf(it) } ?: emptyList()
            members = if (me != null) 1 else 0
        }
        val wasTalking = talking
        remoteProducerId = null
        consumingProducerId = null
        talking = false; remoteSpeaking = false; speakerLabel = null; txFailed = false
        lastVoiceNote = null // la nota es por-canal; se recarga la del canal nuevo
        RadioService.update(appRef, channelName)
        // Salir/entrar al canal YA (NO dentro del hilo de audio): así la presencia y la
        // última nota del canal nuevo llegan al instante, sin esperar a un ack que puede
        // tardar. Antes esto iba en el worker y, si estaba ocupado, el cambio tardaba.
        runCatching { prev?.let { socket.emit("channel:leave", it) } }
        runCatching { socket.emit("channel:join", id) }
        refreshLastVoiceNote(id)
        // Audio (cerrar lo viejo + consumir al hablante del nuevo) en 2º plano: no bloquea
        // la sensación de "conectado" (los transportes persisten → seguimos EN VIVO).
        worker.execute {
            closeConsumers()
            runCatching { producer?.close() }; producer = null
            if (wasTalking) runCatching { socket.emit("ms:closeProducer") }
            val cur = ack("ms:getProducer", JSONObject().put("channelId", id)) as? JSONObject
            val curProducer = cur?.optString("producerId")?.takeIf { it.isNotEmpty() }
            if (curProducer != null) consume(curProducer, cur?.let { speakerAliasFrom(it) } ?: "Alguien del canal")
        }
    }

    fun setOpenChatChannel(id: String?) {
        openChatChannelId = id
    }

    @Volatile private var setupDone = false
    @Volatile private var lastSetupAttemptAt = 0L

    private fun setupMediasoup() {
        // Listo solo si ambos transports existen; si no, reintenta lo que falte.
        if (setupDone && sendTransport != null && recvTransport != null) return
        val now = System.currentTimeMillis()
        // El watchdog y los eventos de socket pueden pedirlo a la vez. Coalescer
        // duplicados evita llenar el executor con montajes/acks obsoletos.
        if (now - lastSetupAttemptAt < 1200) return
        lastSetupAttemptAt = now
        val t0 = System.currentTimeMillis()
        val cid = channelId ?: return
        val f = factory ?: return
        val caps = ack("ms:rtpCapabilities", timeoutMs = 1500) as? JSONObject
        if (caps == null) { android.util.Log.w(TAG, "setupMediasoup: sin rtpCapabilities (socket.connected=${socket.connected()})"); return }
        if (!caps.optBoolean("ready", true)) {
            android.util.Log.i(TAG, "setupMediasoup: SFU aún inicia; se reintentará")
            return
        }
        val routerCaps = caps.optJSONObject("rtpCapabilities") ?: caps
        val dev = device ?: Device(f).also { device = it }
        if (!dev.loaded) {
            val loaded = runCatching { dev.load(routerCaps.toString()) }
            if (loaded.isFailure) {
                android.util.Log.e(TAG, "setupMediasoup: no se pudieron cargar capacidades RTP", loaded.exceptionOrNull())
                return
            }
        }

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
        android.util.Log.d(TAG, "setupMediasoup ${System.currentTimeMillis() - t0}ms send=${sendTransport != null} recv=${recvTransport != null}")
        // EN VIVO apenas los transportes están listos: ya se puede oír/hablar. No esperamos
        // al ms:getProducer de abajo (ver quién habla), que es extra y podría tardar.
        if (setupDone) ui.launch { connected = isChannelReady() }

        // consumir al hablante actual si hay
        val current = ack("ms:getProducer", JSONObject().put("channelId", cid)) as? JSONObject
        val curProducer = current?.optString("producerId")?.takeIf { it.isNotEmpty() }
        if (curProducer != null) {
            val label = speakerAliasFrom(current ?: JSONObject())
            showRemoteSpeaker(curProducer, label)
            consume(curProducer, label)
        }
    }

    /** Publica de inmediato el estado del hablante, sin esperar a crear el consumer. */
    private fun showRemoteSpeaker(producerId: String, label: String) {
        remoteProducerId = producerId
        ui.launch {
            remoteSpeaking = true
            speakerLabel = label
            RadioService.refresh(appRef)
        }
    }

    private fun clearRemoteSpeaker(producerId: String) {
        if (remoteProducerId != producerId) return
        remoteProducerId = null
        ui.launch {
            if (remoteProducerId == null) {
                remoteSpeaking = false
                speakerLabel = null
                RadioService.refresh(appRef)
            }
        }
    }

    /** Extrae el alias del hablante del payload (requiere que el backend incluya `user`). */
    private fun speakerAliasFrom(o: JSONObject): String {
        val u = o.optJSONObject("user") ?: return "Alguien del canal"
        val nick = u.optString("nickname").takeIf { it.isNotBlank() && it != "null" }
        val name = u.optString("name").takeIf { it.isNotBlank() && it != "null" }
        return nick ?: name ?: "Alguien del canal"
    }

    private fun consume(producerId: String, speaker: String) {
        if (consumingProducerId == producerId) return
        showRemoteSpeaker(producerId, speaker)
        consumingProducerId = producerId
        try {
            val recv = recvTransport ?: run { consumingProducerId = null; return }
            val dev = device ?: run { consumingProducerId = null; return }
            val info = ack("ms:consume", JSONObject().put("producerId", producerId).put("rtpCapabilities", JSONObject(dev.rtpCapabilities))) as? JSONObject
            if (info == null || info.has("error")) {
                consumingProducerId = null
                clearRemoteSpeaker(producerId)
                return
            }
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
        } catch (t: Throwable) {
            // No tumbar la app si falla crear/arrancar el consumer (p.ej. al entrar
            // varios a la vez): la radio sigue viva y se reintenta en el próximo evento.
            clearRemoteSpeaker(producerId)
            if (consumingProducerId == producerId) consumingProducerId = null
        }
    }

    private fun closeConsumers() {
        // Saca del mapa ANTES de cerrar (y copia) para que el loop de nivel no toque
        // un consumer que se está cerrando. Todo serializado en el worker.
        val snapshot = consumers.values.toList()
        consumers.clear()
        consumingProducerId = null
        remoteProducerId = null
        snapshot.forEach { runCatching { it.close() } }
        consuming = false
        releaseAudioSession() // dejé de recibir: libera el audio si tampoco estoy hablando
        ui.launch { remoteSpeaking = false; speakerLabel = null; RadioService.refresh(appRef) }
    }

    /** Pitido corto tipo walkie-talkie (inicio/fin de transmisión). */
    private fun beep(tone: Int, durationMs: Int) {
        // Hilo propio (NO el worker): así el pitido no bloquea ni retrasa el produce.
        Thread {
            runCatching {
                val tg = android.media.ToneGenerator(AudioManager.STREAM_VOICE_CALL, 90)
                tg.startTone(tone, durationMs)
                Thread.sleep((durationMs + 60).toLong())
                tg.release()
            }
        }.start()
    }

    /** El canal está realmente listo para transmitir (socket + ambos transportes). */
    fun isChannelReady(): Boolean =
        socket.connected() && sendTransport != null && recvTransport != null

    /** Reconecta el socket y rearma los transportes de audio (SIN transmitir). */
    private fun reconnectAndSetup() {
        runCatching { if (!socket.connected()) socket.connect() }
        worker.execute {
            if (sendTransport == null || recvTransport == null) {
                appRef?.let { runCatching { initWebrtc(it) } }
                runCatching { setupMediasoup() }
            }
            ui.launch { connected = isChannelReady() }
        }
    }

    fun startTalking() {
        if (talking) return
        if (remoteSpeaking || remoteProducerId != null) {
            val who = speakerLabel ?: "Alguien"
            appRef?.let { android.widget.Toast.makeText(it, "$who está hablando", android.widget.Toast.LENGTH_SHORT).show() }
            return
        }
        val app = appRef
        if (app == null || !hasMicPermission(app)) {
            txFailed = true
            app?.let { android.widget.Toast.makeText(it, "Activa el permiso de micrófono para hablar", android.widget.Toast.LENGTH_SHORT).show() }
            RadioService.refresh(app)
            return
        }
        // NO transmitir "en falso": si el canal no está realmente listo (socket + audio
        // armado), no sonamos beep ni marcamos transmitiendo —si no, se perdería la voz
        // (el síntoma "decía conectado pero no se guardó"). Forzamos reconexión/rearme;
        // cuando quede "En vivo" (connected=true) ya se puede hablar y se graba.
        if (!isChannelReady()) {
            reconnectAndSetup()
            return
        }
        // Feedback inmediato (el botón se pone rojo). TODO el audio nativo va al worker
        // (un solo hilo) para no tocar el track nativo desde dos hilos (evita crashes).
        talking = true
        txFailed = false
        RadioService.refresh(appRef) // notificación → "Cortar"
        worker.execute {
            fun fail() {
                channelId?.let { socket.emit("ms:releaseReservation", JSONObject().put("channelId", it)) }
                runCatching { audioManager?.enabled = false }
                ui.launch {
                    talking = false
                    txFailed = true
                    releaseAudioSession()
                    RadioService.refresh(appRef)
                }
            }
            try {
                val cid = channelId ?: return@execute fail()
                var reservation: JSONObject? = null
                if (reservationsSupported != false) {
                    reservation = ack(
                        "ms:reserve",
                        JSONObject().put("channelId", cid),
                        if (reservationsSupported == true) 4000 else 800,
                    ) as? JSONObject
                    if (reservation != null) reservationsSupported = true
                }
                if (reservation == null) {
                    // Compatibilidad con un backend todavía no actualizado: comprueba
                    // primero si ya hay alguien hablando antes de invocar mediasoup.
                    val current = ack("ms:getProducer", JSONObject().put("channelId", cid), 1200) as? JSONObject
                    if (current == null) return@execute fail()
                    if (reservationsSupported == null) reservationsSupported = false
                    val activeId = current.optString("producerId").takeIf { it.isNotBlank() }
                    if (activeId != null) {
                        val label = speakerAliasFrom(current)
                        showRemoteSpeaker(activeId, label)
                        ui.launch {
                            talking = false
                            remoteSpeaking = true
                            speakerLabel = label
                            RadioService.refresh(appRef)
                            appRef?.let {
                                android.widget.Toast.makeText(it, "$label está hablando", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                        consume(activeId, label)
                        return@execute
                    }
                    reservation = JSONObject().put("ok", true) // ruta compatible con backend anterior
                }
                if (reservation?.optBoolean("ok") != true) {
                    val producerId = reservation?.optString("producerId")?.takeIf { it.isNotBlank() }
                    val label = reservation?.let { speakerAliasFrom(it) } ?: "Alguien del canal"
                    if (producerId != null) showRemoteSpeaker(producerId, label)
                    else remoteProducerId = "reserved:$cid"
                    ui.launch {
                        talking = false
                        remoteSpeaking = true
                        speakerLabel = label
                        RadioService.refresh(appRef)
                        appRef?.let {
                            android.widget.Toast.makeText(it, "$label está hablando", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    if (producerId != null) consume(producerId, label)
                    return@execute
                }
                if (!talking) {
                    socket.emit("ms:releaseReservation", JSONObject().put("channelId", cid))
                    return@execute
                }
                acquireAudioSession() // foco + modo llamada + ruta (en el worker)
                if (!talking) { // el usuario soltó enseguida
                    runCatching { audioManager?.enabled = false }
                    releaseAudioSession()
                    return@execute
                }
                beep(android.media.ToneGenerator.TONE_PROP_BEEP, 150) // beep porque SÍ transmitimos
                runCatching { audioManager?.enabled = true } // micro en el MISMO hilo que produce
                val send = sendTransport ?: return@execute fail()
                val track = audioManager?.track ?: return@execute fail()
                producer = send.produce(object : Producer.Listener {
                    override fun onTransportClose(producer: Producer) {}
                }, track)
            } catch (t: Throwable) {
                // Atrapa cualquier error (incluidos los no-Exception) para NO tumbar la app.
                fail()
            }
        }
    }

    fun stopTalking() {
        if (!talking) return
        talking = false // instantáneo en UI
        beep(android.media.ToneGenerator.TONE_PROP_BEEP2, 120) // pitido "roger" al soltar
        RadioService.refresh(appRef) // notificación → "Hablar"
        worker.execute {
            runCatching { audioManager?.enabled = false } // silencia el micro en el worker
            runCatching { producer?.close() }
            producer = null
            runCatching { socket.emit("ms:closeProducer") }
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

    // ── Reproducción de notas de voz del chat ────────────────────
    // Antes sonaban por el stream de MEDIA (volumen multimedia del teléfono) y
    // se oían más bajas que la radio. Ahora usan el MISMO enrutado que la radio
    // (STREAM_VOICE_CALL + altavoz + volumen de la radio) para que suenen igual.
    private var notePlayer: android.media.MediaPlayer? = null
    private var notePlayingId: String? = null

    private fun audioMgr(): AudioManager? =
        sysAudio ?: (appRef?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)

    /** Origen del backend (sin /api) para armar URLs de archivos. */
    private val mediaOrigin: String by lazy { BuildConfig.API_BASE_URL.substringBefore("/api") }

    private fun mediaUrlOf(key: String?): String? = when {
        key.isNullOrBlank() -> null
        key.startsWith("http") -> key
        key.startsWith("/") -> mediaOrigin + key
        else -> null
    }

    /** Carga la última nota de voz del canal desde el historial (más nuevo primero). */
    private fun refreshLastVoiceNote(cid: String) {
        ui.launch {
            val hist = runCatching { Backend.api.radioHistory(cid) }.getOrNull().orEmpty()
            val note = hist.firstOrNull { !it.audioKey.isNullOrBlank() }
            if (channelId == cid) lastVoiceNote = note
        }
    }

    /** Reproduce la última nota de voz del canal (con el enrutado de la radio). */
    fun playLastVoiceNote(onState: (String?) -> Unit) {
        val note = lastVoiceNote ?: return
        val url = mediaUrlOf(note.audioKey) ?: return
        playVoiceNote(url, note.id, onState)
    }

    /**
     * Reproduce una nota de voz del chat como audio multimedia. [onState] avisa a
     * la UI qué id suena (o null al parar). Es toggle: mismo id => detiene.
     */
    fun playVoiceNote(url: String, id: String, onState: (String?) -> Unit) {
        if (notePlayingId == id) { stopVoiceNote(onState); return }
        stopVoiceNote { } // corta cualquier otra nota en curso
        val am = audioMgr()
        runCatching {
            if (am != null) {
                // Las notas son reproducción multimedia: así Android usa perfil A2DP
                // y volumen de medios (icono de música), sin abrir la ruta del micrófono.
                // Si hay radio en vivo, conservamos su sesión de comunicación.
                if (audioSessionActive || talking || consuming) acquireAudioSession()
                else {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        runCatching { am.clearCommunicationDevice() }
                    }
                    am.mode = AudioManager.MODE_NORMAL
                }
            }
            val mp = android.media.MediaPlayer()
            mp.setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(
                        if (audioSessionActive || talking || consuming)
                            android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION
                        else android.media.AudioAttributes.USAGE_MEDIA,
                    )
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.setDataSource(url)
            mp.setOnPreparedListener { it.start(); notePlayingId = id; ui.launch { onState(id) } }
            mp.setOnCompletionListener { stopVoiceNote(onState) }
            mp.setOnErrorListener { _, _, _ -> stopVoiceNote(onState); true }
            mp.prepareAsync()
            notePlayer = mp
        }.onFailure { stopVoiceNote(onState) }
    }

    /** Detiene la nota de voz y libera la sesión cuando no hay otra voz de radio activa. */
    fun stopVoiceNote(onState: (String?) -> Unit) {
        runCatching { notePlayer?.stop() }
        runCatching { notePlayer?.release() }
        notePlayer = null
        notePlayingId = null
        ui.launch { onState(null) }
        if (audioSessionActive || talking || consuming) releaseAudioSession()
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
                // Lee el stats nativo SOLO en el worker (mismo hilo que produce/consume/
                // close): nunca se toca WebRTC desde dos hilos → sin crash al entrar varios.
                val statsJson = when {
                    talking -> withContext(workerDispatcher) { runCatching { producer?.stats }.getOrNull() }
                    remoteSpeaking -> withContext(workerDispatcher) { runCatching { consumers.values.firstOrNull()?.stats }.getOrNull() }
                    else -> null
                }
                // audioLevel de WebRTC es RMS (voz ≈ 0..0.3): lo amplificamos y suavizamos.
                val raw = statsJson?.let { runCatching { parseAudioLevel(it) }.getOrNull() } ?: 0f
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
            override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>?) {
                autoSelectOutput() // conectaron audífono/BT → úsalo
            }
            override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>?) {
                autoSelectOutput() // quitaron audífono/BT → vuelve al teléfono
                if (!audioSessionActive && !talking && !consuming && !isHeadsetConnected()) {
                    sysAudio?.let { resetCommMode(it) } // self-heal del modo llamada
                }
            }
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
                // El enlace SCO del BT es asíncrono: limpiar antes de fijar ayuda a que
                // el cambio "pegue". Si no, el primer setCommunicationDevice devuelve true
                // pero el audio se queda en el auricular.
                if (!speakerOn && bt != null) runCatching { am.clearCommunicationDevice() }
                val setOk = target?.let { am.setCommunicationDevice(it) } ?: false
                android.util.Log.d(
                    TAG,
                    "applyAudioRoute speakerOn=$speakerOn bt=${bt != null} wired=${wired != null} " +
                        "target=${target?.type} setCommunicationDevice=$setOk current=${am.communicationDevice?.type}",
                )
                // Re-aplicar el BT tras establecerse el SCO (asíncrono): si a los ~1.2s el
                // dispositivo activo aún no es el BT, lo volvemos a fijar.
                if (!speakerOn && bt != null) {
                    ui.launch {
                        delay(1200)
                        if (audioSessionActive && !speakerOn) runCatching {
                            val b2 = am.availableCommunicationDevices.firstOrNull {
                                it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                                    it.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET
                            }
                            if (b2 != null && am.communicationDevice?.type != b2.type) {
                                am.setCommunicationDevice(b2)
                                android.util.Log.d(TAG, "reapply BT -> current=${am.communicationDevice?.type}")
                            }
                        }
                    }
                }
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

    /**
     * Detecta y muestra la salida de audio (Bluetooth/Auricular/Teléfono) SIN tocar
     * el modo ni el foco, para que la etiqueta sea correcta ya al conectar (no solo
     * al hablar).
     */
    private fun detectOutputLabel() {
        val am = sysAudio ?: return
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return
        runCatching {
            val devices = am.availableCommunicationDevices
            fun has(vararg types: Int) = devices.any { it.type in types }
            val label = when {
                has(android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO, android.media.AudioDeviceInfo.TYPE_BLE_HEADSET) -> "Bluetooth"
                has(
                    android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
                ) -> "Auricular"
                else -> "Teléfono"
            }
            ui.launch { normalDeviceLabel = label }
        }
    }

    private var focusRequest: android.media.AudioFocusRequest? = null

    /** Pide foco "a la par": la radio suena ENCIMA sin PAUSAR otras apps (solo las baja un poco). */
    private fun requestAudioFocus() {
        val am = sysAudio ?: return
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        if (focusRequest != null) return
        runCatching {
            val attrs = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            // MAY_DUCK: las otras apps (música, etc.) SIGUEN sonando (un poco más bajo) en
            // vez de pausarse. Así se escucha la radio a la par con lo demás.
            val req = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
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

    /**
     * Salida AUTOMÁTICA: si hay audífono/Bluetooth conectado se usa ese; si no, el altavoz
     * del teléfono. Es lo esperado: BT conectado → suena en BT; sin BT → suena en el celular.
     */
    private fun autoSelectOutput() {
        val wantSpeaker = !isHeadsetConnected()
        if (speakerOn != wantSpeaker) {
            speakerOn = wantSpeaker
            Prefs.speakerOn = wantSpeaker
        }
        if (audioSessionActive) applyAudioRoute() else detectOutputLabel()
    }

    /** ¿Es un audífono (cable/USB) o Bluetooth? */
    private fun isHeadsetType(t: Int): Boolean = t in intArrayOf(
        android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        android.media.AudioDeviceInfo.TYPE_BLE_HEADSET,
        android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
        android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
    )

    /** ¿Hay audífono por cable o Bluetooth conectado como salida de comunicación? */
    private fun isHeadsetConnected(): Boolean {
        val am = sysAudio ?: return false
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return false
        return runCatching { am.availableCommunicationDevices.any { isHeadsetType(it.type) } }.getOrDefault(false)
    }

    /** Libera el audio del sistema cuando ya no hay voz (ni hablo ni recibo). */
    private fun releaseAudioSession() {
        if (talking || consuming || notePlayer != null) return // sigue habiendo voz
        if (!audioSessionActive) return
        audioSessionActive = false
        abandonAudioFocus() // soltamos el foco: otras apps vuelven a sonar
        val am = sysAudio ?: return
        // SIEMPRE salimos del modo llamada y soltamos el dispositivo de comunicación en
        // silencio. Antes lo manteníamos "caliente" con BT, pero eso DEJABA el teléfono en
        // modo llamada y bloqueaba el audio Bluetooth de las demás apps (música, etc.).
        // Al hablar/recibir se vuelve a tomar el BT (acquireAudioSession → applyAudioRoute).
        resetCommMode(am)
    }

    /** Devuelve el audio a modo normal (quita modo llamada + dispositivo de comunicación). */
    private fun resetCommMode(am: AudioManager) {
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) am.clearCommunicationDevice()
            if (am.mode == AudioManager.MODE_IN_COMMUNICATION) am.mode = AudioManager.MODE_NORMAL
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
        val radioSocket = socket
        worker.execute {
            runCatching { producer?.close() }; producer = null
            closeConsumers()
            runCatching { sendTransport?.close() }; sendTransport = null
            runCatching { recvTransport?.close() }; recvTransport = null
            runCatching { device?.dispose() }; device = null
            channelId?.let { radioSocket.emit("channel:leave", it) }
            radioSocket.off("ms:newProducer"); radioSocket.off("ms:producerClosed"); radioSocket.off("connect")
            abandonAudioFocus()
            runCatching {
                audioCallback?.let { sysAudio?.unregisterAudioDeviceCallback(it) }; audioCallback = null
                connectivityCb?.let { cb ->
                    (appRef?.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager)
                        ?.unregisterNetworkCallback(cb)
                }
                connectivityCb = null
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
    private fun ack(event: String, payload: Any? = null, timeoutMs: Long = 4000): Any? {
        val t0 = System.currentTimeMillis()
        val latch = CountDownLatch(1)
        val holder = arrayOfNulls<Any>(1)
        val cb = Ack { args -> holder[0] = args.firstOrNull(); latch.countDown() }
        when (payload) {
            null -> socket.emit(event, cb)
            else -> socket.emit(event, payload, cb)
        }
        val got = latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        val dt = System.currentTimeMillis() - t0
        if (!got) android.util.Log.w(TAG, "ack '$event' TIMEOUT ${dt}ms (socket.connected=${socket.connected()})")
        else if (dt > 800) android.util.Log.w(TAG, "ack '$event' lento: ${dt}ms")
        return holder[0]
    }

    private const val TAG = "RadioTiming"
}
