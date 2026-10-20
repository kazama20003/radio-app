package com.syemape.radio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.syemape.radio.data.AuthStatus
import com.syemape.radio.data.Backend
import com.syemape.radio.data.SessionManager
import com.syemape.radio.ui.BottomNav
import com.syemape.radio.ui.Tab
import com.syemape.radio.ui.screens.AlertsScreen
import com.syemape.radio.ui.screens.ChatDetailScreen
import com.syemape.radio.ui.screens.ChatsScreen
import com.syemape.radio.ui.screens.CallsScreen
import com.syemape.radio.ui.screens.LoginScreen
import com.syemape.radio.ui.screens.MapScreen
import com.syemape.radio.ui.screens.ProfileScreen
import com.syemape.radio.ui.screens.PrivateRadioScreen
import com.syemape.radio.ui.screens.RadioScreen
import com.syemape.radio.ui.theme.MapeColors
import com.syemape.radio.ui.theme.MapeTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Backend.init(applicationContext)
        com.syemape.radio.data.Prefs.init(applicationContext)
        com.syemape.radio.data.Notifier.init(applicationContext)
        SessionManager.bootstrap()
        handlePrivateCallIntent(intent)
        enableEdgeToEdge()
        setContent {
            var darkMode by remember { mutableStateOf(com.syemape.radio.data.Prefs.darkMode) }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkMode
                    isAppearanceLightNavigationBars = !darkMode
                }
            }
            MapeTheme(darkMode = darkMode) {
                AppRoot(onDarkModeChanged = { darkMode = it })
            }
        }
    }

    /** Al volver a la app, resincroniza la radio (reconecta/reconsume si el SO la
     *  suspendió en 2º plano). Seguro aunque la radio no esté activa. */
    override fun onResume() {
        super.onResume()
        runCatching {
            if (com.syemape.radio.data.Backend.tokens.accessToken != null) {
                com.syemape.radio.data.RadioManager.start(application as android.app.Application)
            } else {
                com.syemape.radio.data.RadioManager.ensureAlive()
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePrivateCallIntent(intent)
    }

    private fun handlePrivateCallIntent(intent: android.content.Intent?) {
        val action = intent?.getStringExtra("private_call_action") ?: return
        val callId = intent.getStringExtra("private_call_id") ?: return
        intent.removeExtra("private_call_action")
        intent.removeExtra("private_call_id")
        lifecycleScope.launch {
            repeat(40) {
                val call = com.syemape.radio.data.RadioManager.privateCall
                if (call?.callId == callId) {
                    when (action) {
                        "answer", "decline" -> if (call.phase == "INCOMING") {
                            com.syemape.radio.data.RadioManager.answerPrivateCall(action == "answer", callId)
                            return@launch
                        }
                        "open" -> if (call.phase == "ACTIVE") {
                            com.syemape.radio.data.RadioManager.requestOpenPrivateRadio(callId)
                            return@launch
                        }
                        "hangup" -> if (call.phase == "ACTIVE") {
                            com.syemape.radio.data.RadioManager.endPrivateCall()
                            return@launch
                        }
                    }
                }
                delay(500)
            }
        }
    }
}

@Composable
private fun AppRoot(onDarkModeChanged: (Boolean) -> Unit) {
    val appContext = androidx.compose.ui.platform.LocalContext.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val scope = rememberCoroutineScope()

    var tab by remember {
        mutableStateOf(com.syemape.radio.data.Prefs.lastTab?.let { runCatching { Tab.valueOf(it) }.getOrNull() } ?: Tab.Mapa)
    }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var openChat by remember { mutableStateOf<Pair<String, String>?>(null) }
    var openChannelChat by remember { mutableStateOf<Pair<String, String>?>(null) }
    var openPrivateRadio by remember { mutableStateOf<Pair<String, String>?>(null) }
    var openSub by remember { mutableStateOf<String?>(null) }
    val privateCall = com.syemape.radio.data.RadioManager.privateCall

    LaunchedEffect(privateCall?.callId, privateCall?.phase) {
        val call = privateCall ?: return@LaunchedEffect
        when (call.phase) {
            "ACTIVE" -> openPrivateRadio = call.conversationId to call.peerName
            "DECLINED", "TIMEOUT", "CANCELLED", "DISCONNECTED", "ENDED", "FAILED" -> {
                if (openPrivateRadio?.first == call.conversationId) openPrivateRadio = null
                call.reason?.takeIf { it.isNotBlank() }?.let { android.widget.Toast.makeText(appContext, it, android.widget.Toast.LENGTH_LONG).show() }
                delay(1800)
                com.syemape.radio.data.RadioManager.clearPrivateCall(call.callId)
            }
        }
    }

    LaunchedEffect(com.syemape.radio.data.RadioManager.openPrivateRadioRequest, privateCall?.callId) {
        val requested = com.syemape.radio.data.RadioManager.openPrivateRadioRequest ?: return@LaunchedEffect
        val active = privateCall?.takeIf { it.callId == requested && it.phase == "ACTIVE" } ?: return@LaunchedEffect
        openPrivateRadio = active.conversationId to active.peerName
        com.syemape.radio.data.RadioManager.consumeOpenPrivateRadioRequest(requested)
    }

    when (SessionManager.status) {
        AuthStatus.Loading -> Box(
            Modifier.fillMaxSize().background(MapeColors.Bg),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(color = MapeColors.Text) }

        AuthStatus.Unauthenticated -> LoginScreen(
            topPadding = topInset,
            bottomPadding = bottomInset,
            loading = loading,
            error = error,
            onLogin = { identifier, password ->
                if (!loading) {
                    scope.launch {
                        loading = true
                        error = null
                        val result = SessionManager.login(identifier, password)
                        loading = false
                        error = result.exceptionOrNull()?.message
                    }
                }
            },
        )

        AuthStatus.Authenticated -> {
          val context = androidx.compose.ui.platform.LocalContext.current
          BackHandler(enabled = true) {
              when {
                  openSub != null -> openSub = null
                  openPrivateRadio != null -> {
                      com.syemape.radio.data.RadioManager.endPrivateCall()
                      openPrivateRadio = null
                  }
                  openChannelChat != null -> openChannelChat = null
                  openChat != null -> openChat = null
                  tab != Tab.Mapa -> {
                      tab = Tab.Mapa
                      com.syemape.radio.data.Prefs.lastTab = Tab.Mapa.name
                  }
                  else -> android.widget.Toast.makeText(
                      context,
                      "MAPE está en inicio",
                      android.widget.Toast.LENGTH_SHORT,
                  ).show()
              }
          }
          val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
              androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
          ) { result ->
              if (result[android.Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                  com.syemape.radio.data.TrackingManager.startLocationUpdates(context)
              }
          }
          androidx.compose.runtime.LaunchedEffect(Unit) {
              com.syemape.radio.data.TrackingManager.start(context)
              com.syemape.radio.data.AppBadges.wireRealtime()
              com.syemape.radio.data.AppBadges.refresh()
              // Pide TODOS los permisos de una sola vez al entrar.
              val wanted = buildList {
                  add(android.Manifest.permission.RECORD_AUDIO)
                  add(android.Manifest.permission.ACCESS_FINE_LOCATION)
                  if (android.os.Build.VERSION.SDK_INT >= 33) add(android.Manifest.permission.POST_NOTIFICATIONS)
                  if (android.os.Build.VERSION.SDK_INT >= 31) add(android.Manifest.permission.BLUETOOTH_CONNECT)
              }
              val missing = wanted.filter {
                  context.checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
              }
              if (missing.isNotEmpty()) permLauncher.launch(missing.toTypedArray())
              // Exención de optimización de batería (clave para no cortarse en 2º plano
              // en Xiaomi/Huawei/Samsung/Oppo…). Se pide UNA SOLA VEZ y solo si NO está
              // ya activada: si ya está exenta no se pide nunca, y si el usuario la vio
              // una vez no se vuelve a molestar (bandera persistente).
              runCatching {
                  val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                  val exempt = pm.isIgnoringBatteryOptimizations(context.packageName)
                  if (!exempt && !com.syemape.radio.data.Prefs.batteryOptAsked) {
                      com.syemape.radio.data.Prefs.batteryOptAsked = true
                      context.startActivity(
                          android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                              .setData(android.net.Uri.parse("package:${context.packageName}"))
                              .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                      )
                  }
              }
          }
          Box(Modifier.fillMaxSize().background(MapeColors.Bg)) {
            val chat = openChat
            val channelChat = openChannelChat
            val privateRadio = openPrivateRadio
            val sub = openSub
            if (sub != null) {
                when (sub) {
                    "account" -> com.syemape.radio.ui.screens.AccountScreen(topInset) { openSub = null }
                    "settings" -> com.syemape.radio.ui.screens.AppSettingsScreen(topInset, onDarkModeChanged) { openSub = null }
                    "notifications" -> com.syemape.radio.ui.screens.NotificationsScreen(topInset) { openSub = null }
                    "admin-users" -> com.syemape.radio.ui.screens.AdminUsersScreen(topInset) { openSub = null }
                    else -> { openSub = null }
                }
            } else if (privateRadio != null) {
                PrivateRadioScreen(
                    conversationId = privateRadio.first,
                    peerName = privateRadio.second,
                    topPadding = topInset,
                    onBack = { com.syemape.radio.data.RadioManager.endPrivateCall(); openPrivateRadio = null },
                    onHangUp = { com.syemape.radio.data.RadioManager.endPrivateCall(); openPrivateRadio = null },
                )
            } else if (channelChat != null) {
                com.syemape.radio.ui.screens.ChannelChatScreen(
                    channelId = channelChat.first,
                    title = channelChat.second,
                    topPadding = topInset,
                    bottomPadding = bottomInset,
                    onBack = { openChannelChat = null },
                )
            } else if (chat != null) {
                ChatDetailScreen(
                    conversationId = chat.first,
                    title = chat.second,
                    topPadding = topInset,
                    bottomPadding = bottomInset,
                    onOpenPrivateRadio = { id, name -> openPrivateRadio = id to name },
                    onBack = { openChat = null },
                )
            } else {
                when (tab) {
                    Tab.Mapa -> MapScreen(topInset, bottomInset)
                    Tab.Radio -> RadioScreen(
                        topInset,
                        bottomInset,
                        onOpenChannelChat = { id, name -> openChannelChat = id to name },
                        onOpenPrivateRadio = { id, peerId, name -> com.syemape.radio.data.RadioManager.startPrivateCall(id, peerId, name) },
                        onOpenSettings = { openSub = "settings" },
                    )
                    Tab.Chats -> ChatsScreen(
                        topInset,
                        onOpenChat = { id, name -> openChat = id to name },
                        onOpenChannelChat = { id, name -> openChannelChat = id to name },
                    )
                    Tab.Llamadas -> CallsScreen(topInset, bottomInset) { entry ->
                        if (entry.peerId.isNotBlank()) scope.launch {
                            runCatching {
                                val conversationId = entry.conversationId.ifBlank {
                                    Backend.api.createConversation(
                                        com.syemape.radio.data.CreateConversationRequest(
                                            type = "DIRECT",
                                            memberIds = listOf(entry.peerId),
                                        ),
                                    ).id
                                }
                                com.syemape.radio.data.RadioManager.startPrivateCall(
                                    conversationId,
                                    entry.peerId,
                                    entry.peerName,
                                )
                            }.onFailure {
                                android.widget.Toast.makeText(
                                    context,
                                    "No se pudo volver a llamar a ${entry.peerName}",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    }
                    Tab.Alertas -> AlertsScreen(topInset, onGoMap = { tab = Tab.Mapa }, onGoRadio = { tab = Tab.Radio })
                    Tab.Perfil -> ProfileScreen(
                        topInset,
                        onLogout = { scope.launch { SessionManager.logout() } },
                        onOpen = { route -> if (route != "help" && route != "logout") openSub = route },
                    )
                }
                BottomNav(
                    active = tab,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (bottomInset > 0.dp) bottomInset + 8.dp else 16.dp),
                    onSelect = { tab = it; com.syemape.radio.data.Prefs.lastTab = it.name },
                )
            }
          }
          when (privateCall?.phase) {
              "INCOMING" -> AlertDialog(
                  onDismissRequest = { com.syemape.radio.data.RadioManager.answerPrivateCall(false, privateCall.callId) },
                  title = { Text("Llamada de radio privada") },
                  text = { Text("${privateCall.peerName} quiere hablar contigo. ¿Contestas?") },
                  confirmButton = { TextButton(onClick = { com.syemape.radio.data.RadioManager.answerPrivateCall(true, privateCall.callId) }) { Text("Contestar") } },
                  dismissButton = { TextButton(onClick = { com.syemape.radio.data.RadioManager.answerPrivateCall(false, privateCall.callId) }) { Text("Rechazar") } },
              )
              "OUTGOING" -> AlertDialog(
                  onDismissRequest = { com.syemape.radio.data.RadioManager.endPrivateCall() },
                  title = { Text("Llamando…") },
                  text = { Text("Esperando que ${privateCall.peerName} conteste") },
                  confirmButton = { TextButton(onClick = { com.syemape.radio.data.RadioManager.endPrivateCall() }) { Text("Cancelar llamada") } },
              )
          }
        }
    }
}
