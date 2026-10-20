package com.syemape.radio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syemape.radio.data.RadioManager
import com.syemape.radio.ui.MapeIcons
import com.syemape.radio.ui.theme.MapeColors
import com.syemape.radio.ui.theme.Outfit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CallsScreen(
    topPadding: Dp,
    bottomPadding: Dp = 0.dp,
    onCall: (call: com.syemape.radio.data.PrivateCallHistoryEntry) -> Unit = {},
) {
    LaunchedEffect(RadioManager.privateCallHistory.firstOrNull()?.callId) {
        RadioManager.markPrivateCallsRead()
    }

    Column(
        Modifier.fillMaxSize().background(MapeColors.Bg)
            .padding(top = topPadding + 18.dp, bottom = bottomPadding + 82.dp)
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(MapeIcons.Phone, null, tint = MapeColors.Red, modifier = Modifier.size(25.dp))
            Column {
                Text("Llamadas", color = MapeColors.Text, fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 23.sp)
                Text("Historial de radio privada", color = MapeColors.TextMuted, fontFamily = Outfit, fontSize = 13.sp)
            }
        }

        if (RadioManager.privateCallHistory.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(top = 30.dp).clip(RoundedCornerShape(18.dp))
                    .background(MapeColors.Card).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(MapeIcons.Phone, null, tint = MapeColors.TextMuted, modifier = Modifier.size(32.dp))
                Text("Aún no tienes llamadas privadas recibidas", color = MapeColors.Text, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text("Aquí aparecerán el apelativo, la fecha y el resultado de cada llamada.", color = MapeColors.TextMuted, fontFamily = Outfit, fontSize = 13.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(RadioManager.privateCallHistory, key = { it.callId }) { entry ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(MapeColors.Card)
                            .clickable { onCall(entry) }
                            .border(1.dp, MapeColors.Border, RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            MapeIcons.Phone,
                            null,
                            tint = if (entry.outcome == "SIN RESPUESTA") Color(0xFFE05252) else MapeColors.Red,
                            modifier = Modifier.size(20.dp),
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(entry.peerName, color = MapeColors.Text, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            val date = SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.forLanguageTag("es-PE"))
                                .format(Date(entry.timestamp))
                            Text(
                                "${entry.outcome.lowercase().replaceFirstChar { it.uppercase() }} · $date",
                                color = MapeColors.TextMuted,
                                fontFamily = Outfit,
                                fontSize = 12.sp,
                            )
                        }
                        Icon(MapeIcons.Phone, "Volver a llamar a ${entry.peerName}", tint = MapeColors.Red, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}
