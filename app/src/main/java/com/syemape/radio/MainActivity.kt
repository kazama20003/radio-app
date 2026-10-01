package com.syemape.radio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.syemape.radio.data.AuthStatus
import com.syemape.radio.data.Backend
import com.syemape.radio.data.SessionManager
import com.syemape.radio.ui.BottomNav
import com.syemape.radio.ui.Tab
import com.syemape.radio.ui.screens.AlertsScreen
import com.syemape.radio.ui.screens.ChatDetailScreen
import com.syemape.radio.ui.screens.ChatsScreen
import com.syemape.radio.ui.screens.LoginScreen
import com.syemape.radio.ui.screens.MapScreen
import com.syemape.radio.ui.screens.ProfileScreen
import com.syemape.radio.ui.screens.RadioScreen
import com.syemape.radio.ui.theme.MapeColors
import com.syemape.radio.ui.theme.MapeTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Backend.init(applicationContext)
        com.syemape.radio.data.Prefs.init(applicationContext)
        SessionManager.bootstrap()
        enableEdgeToEdge()
        setContent {
            MapeTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
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
    var openSub by remember { mutableStateOf<String?>(null) }

    when (SessionManager.status) {
        AuthStatus.Loading -> Box(
            Modifier.fillMaxSize().background(MapeColors.Bg),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(color = MapeColors.Ink) }

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
          val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
              androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
          ) { granted -> if (granted) com.syemape.radio.data.TrackingManager.startLocationUpdates(context) }
          androidx.compose.runtime.LaunchedEffect(Unit) {
              com.syemape.radio.data.TrackingManager.start(context)
              com.syemape.radio.data.AppBadges.wireRealtime()
              com.syemape.radio.data.AppBadges.refresh()
              if (!com.syemape.radio.data.TrackingManager.hasLocationPermission(context)) {
                  permLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
              }
          }
          Box(Modifier.fillMaxSize().background(MapeColors.Bg)) {
            val chat = openChat
            val channelChat = openChannelChat
            val sub = openSub
            if (sub != null) {
                when (sub) {
                    "account" -> com.syemape.radio.ui.screens.AccountScreen(topInset) { openSub = null }
                    "settings" -> com.syemape.radio.ui.screens.AppSettingsScreen(topInset) { openSub = null }
                    "notifications" -> com.syemape.radio.ui.screens.NotificationsScreen(topInset) { openSub = null }
                    "admin-users" -> com.syemape.radio.ui.screens.AdminUsersScreen(topInset) { openSub = null }
                    else -> { openSub = null }
                }
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
                    onBack = { openChat = null },
                )
            } else {
                when (tab) {
                    Tab.Mapa -> MapScreen(topInset)
                    Tab.Radio -> RadioScreen(topInset, onOpenChannelChat = { id, name -> openChannelChat = id to name })
                    Tab.Chats -> ChatsScreen(topInset, onOpenChat = { id, name -> openChat = id to name })
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
        }
    }
}
