package com.syemape.radio.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syemape.radio.R
import com.syemape.radio.ui.Avatar
import com.syemape.radio.ui.MapeIcons
import com.syemape.radio.ui.avatarColor
import com.syemape.radio.ui.pressScale
import com.syemape.radio.ui.theme.MapeColors
import com.syemape.radio.ui.theme.Outfit

@Composable
fun LoginScreen(
    topPadding: Dp,
    bottomPadding: Dp,
    loading: Boolean = false,
    error: String? = null,
    onLogin: (identifier: String, password: String) -> Unit = { _, _ -> },
) {
    var admin by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var remember2 by remember { mutableStateOf(true) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MapeColors.Bg)
            .verticalScroll(rememberScrollState()),
    ) {
        // ---- Cabecera oscura ----
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp))
                .background(MapeColors.Ink),
        ) {
            Canvas(Modifier.fillMaxWidth().height(120.dp).align(Alignment.BottomStart)) {
                val p = Path().apply {
                    moveTo(-10f, 90f)
                    cubicTo(60f, 90f, 80f, 30f, 140f, 30f)
                    cubicTo(200f, 30f, 210f, 100f, 260f, 92f)
                    cubicTo(310f, 84f, 320f, 20f, 400f, 40f)
                }
                drawPath(
                    p,
                    color = Color.White,
                    alpha = 0.35f,
                    style = Stroke(width = 2f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 7f))),
                )
            }
            Column(
                Modifier.padding(horizontal = 28.dp).padding(top = topPadding + 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Box(Modifier.size(44.dp).pressScale { }, contentAlignment = Alignment.CenterStart) {
                        Icon(MapeIcons.ArrowLeft, null, tint = MapeColors.White, modifier = Modifier.size(26.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(MapeColors.White), contentAlignment = Alignment.Center) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(R.drawable.mape_logo),
                                contentDescription = "Mape",
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        Text("Mape", color = MapeColors.White, fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = (-0.4).sp)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        buildAnnotatedString {
                            withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Medium)) { append("Bienvenido\n") }
                            withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Light)) { append("de nuevo.") }
                        },
                        color = MapeColors.White,
                        fontFamily = Outfit,
                        fontSize = 36.sp,
                        lineHeight = 40.sp,
                        letterSpacing = (-1).sp,
                    )
                    Text(
                        "Ingresa con tu cuenta de Mape para ver tu flota en tiempo real.",
                        color = MapeColors.TextOnDarkSoft,
                        fontFamily = Outfit,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                    )
                }
            }
        }

        // ---- Formulario ----
        Column(
            Modifier.padding(horizontal = 28.dp).padding(top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Segmento
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(27.dp)).background(MapeColors.White).padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SegmentButton("Administrador", MapeIcons.User, admin, Modifier.weight(1f)) { admin = true; email = "" }
                SegmentButton("Supervisor", MapeIcons.UserPlus, !admin, Modifier.weight(1f)) { admin = false; email = "" }
            }

            FieldLabel(if (admin) "Correo" else "DNI")
            InputField(
                value = email,
                onValue = { email = it },
                placeholder = if (admin) "correo@mape.app" else "Ingresa tu DNI",
                leading = MapeIcons.User,
                keyboardType = if (admin) KeyboardType.Email else KeyboardType.Number,
            )

            FieldLabel("Contraseña")
            InputField(
                value = password,
                onValue = { password = it },
                placeholder = "",
                leading = MapeIcons.Lock,
                password = !showPass,
                trailing = MapeIcons.Eye,
                onTrailing = { showPass = !showPass },
            )

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (remember2) MapeColors.Ink else Color.Transparent)
                            .border(1.5.dp, if (remember2) MapeColors.Ink else Color(0xFFB5B5B5), RoundedCornerShape(5.dp))
                            .pressScale { remember2 = !remember2 },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (remember2) Icon(MapeIcons.DoubleCheck, null, tint = MapeColors.White, modifier = Modifier.size(12.dp))
                    }
                    Text("Recordarme", color = Color(0xFF4A4A4A), fontFamily = Outfit, fontSize = 13.sp)
                }
                Text("¿Olvidaste tu contraseña?", color = MapeColors.Ink, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }

            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(29.dp))
                    .background(if (loading) MapeColors.Ink.copy(alpha = 0.6f) else MapeColors.Ink)
                    .pressScale(enabled = !loading) { onLogin(email, password) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (loading) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = MapeColors.White,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(22.dp),
                    )
                } else {
                    Text("Ingresar", color = MapeColors.White, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(Modifier.width(10.dp))
                    Icon(MapeIcons.ArrowRight, null, tint = MapeColors.White, modifier = Modifier.size(18.dp))
                }
            }
            if (error != null) {
                Text(
                    error,
                    color = MapeColors.Red,
                    fontFamily = Outfit,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }

            // Divisor
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.weight(1f).height(1.dp).background(MapeColors.Border))
                Text("o continúa con", color = MapeColors.TextFaint, fontFamily = Outfit, fontSize = 12.sp)
                Box(Modifier.weight(1f).height(1.dp).background(MapeColors.Border))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineButton("Código QR", MapeIcons.Qr, Modifier.weight(1f))
                OutlineButton("Huella", MapeIcons.Fingerprint, Modifier.weight(1f))
            }
        }

        // ---- Pie ----
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(top = 24.dp, bottom = bottomPadding + 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                Modifier.clip(RoundedCornerShape(20.dp)).background(MapeColors.White).padding(start = 10.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row {
                    listOf("Juan", "Luis", "Rosa").forEachIndexed { i, n ->
                        Box(modifier = if (i == 0) Modifier else Modifier.offset(x = (-10 * i).dp)) {
                            Avatar(n.take(2).uppercase(), avatarColor(n), size = 32.dp, border = 2.dp, borderColor = MapeColors.White)
                        }
                    }
                }
                Text(
                    buildAnnotatedString {
                        withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold, color = MapeColors.Ink)) { append("12 operadores") }
                        withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Normal, color = Color(0xFF4A4A4A))) { append(" conectados ahora") }
                    },
                    fontFamily = Outfit,
                    fontSize = 13.sp,
                )
            }
            Text(
                buildAnnotatedString {
                    withStyle(androidx.compose.ui.text.SpanStyle(color = MapeColors.TextFaint)) { append("¿Nuevo en el equipo? ") }
                    withStyle(androidx.compose.ui.text.SpanStyle(color = MapeColors.Ink, fontWeight = FontWeight.SemiBold)) { append("Solicita acceso") }
                },
                fontFamily = Outfit,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, color = Color(0xFF4A4A4A), fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
}

@Composable
private fun InputField(
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    leading: ImageVector,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    trailing: ImageVector? = null,
    onTrailing: () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(MapeColors.White).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(leading, null, tint = MapeColors.TextMuted, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(placeholder, color = Color(0xFF9A9A9A), fontFamily = Outfit, fontSize = 15.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValue,
                singleLine = true,
                textStyle = TextStyle(fontFamily = Outfit, fontSize = 15.sp, color = MapeColors.Ink),
                cursorBrush = SolidColor(MapeColors.Ink),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (trailing != null) {
            Box(Modifier.pressScale { onTrailing() }) {
                Icon(trailing, null, tint = MapeColors.Ink, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun SegmentButton(label: String, icon: ImageVector, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(if (active) MapeColors.Ink else Color.Transparent).pressScale { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = if (active) MapeColors.White else MapeColors.TextMuted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, color = if (active) MapeColors.White else MapeColors.TextMuted, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
    }
}

@Composable
private fun OutlineButton(label: String, icon: ImageVector, modifier: Modifier) {
    Row(
        modifier.height(54.dp).clip(RoundedCornerShape(27.dp)).background(MapeColors.White).border(1.5.dp, MapeColors.Border, RoundedCornerShape(27.dp)).pressScale { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = MapeColors.Ink, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = MapeColors.Ink, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}
