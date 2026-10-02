package com.syemape.radio.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Set de iconos de trazo del diseño "Mape" (viewBox 0 0 24 24), replicados 1:1
 * de components/mape/icons.tsx mediante paths SVG. El color se aplica con el
 * tint de [androidx.compose.material3.Icon], así que aquí el color es un marcador.
 */

private fun circlePath(cx: Float, cy: Float, r: Float): String =
    "M ${cx + r} $cy A $r $r 0 1 0 ${cx - r} $cy A $r $r 0 1 0 ${cx + r} $cy Z"

private fun rrectPath(x: Float, y: Float, w: Float, h: Float, r: Float): String {
    val x2 = x + w
    val y2 = y + h
    return "M ${x + r} $y H ${x2 - r} A $r $r 0 0 1 $x2 ${y + r} " +
        "V ${y2 - r} A $r $r 0 0 1 ${x2 - r} $y2 H ${x + r} " +
        "A $r $r 0 0 1 $x ${y2 - r} V ${y + r} A $r $r 0 0 1 ${x + r} $y Z"
}

private class Part(val d: String, val fill: Boolean = false)

private fun buildIcon(sw: Float, vararg parts: Part): ImageVector {
    val b = ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    for (p in parts) {
        val nodes = PathParser().parsePathString(p.d).toNodes()
        if (p.fill) {
            b.addPath(nodes, fill = SolidColor(Color.Black))
        } else {
            b.addPath(
                nodes,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = sw,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }
    return b.build()
}

object MapeIcons {
    val Pin = buildIcon(
        2f,
        Part("M12 21s-6-5.3-6-11a6 6 0 0 1 12 0c0 5.7-6 11-6 11z"),
        Part(circlePath(12f, 10f, 2.5f)),
    )
    val ChevronRight = buildIcon(2f, Part("M10 7l5 5-5 5"))
    val ArrowRight = buildIcon(2.2f, Part("M5 12h14M13 6l6 6-6 6"))
    val ArrowLeft = buildIcon(2.3f, Part("M19 12H5M11 6l-6 6 6 6"))
    val User = buildIcon(1.8f, Part(circlePath(12f, 8f, 4f)), Part("M4 21c1-5 4.5-7 8-7s7 2 8 7"))
    val Lock = buildIcon(1.8f, Part(rrectPath(5f, 11f, 14f, 10f, 3f)), Part("M8 11V8a4 4 0 0 1 8 0v3"))
    val Eye = buildIcon(
        1.8f,
        Part("M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z"),
        Part(circlePath(12f, 12f, 3f)),
    )
    val Qr = buildIcon(
        1.8f,
        Part(rrectPath(3f, 3f, 7f, 7f, 1.5f)),
        Part(rrectPath(14f, 3f, 7f, 7f, 1.5f)),
        Part(rrectPath(3f, 14f, 7f, 7f, 1.5f)),
        Part("M14 14h3v3M21 14v7h-7"),
    )
    val Fingerprint = buildIcon(
        1.8f,
        Part("M7 4h10a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z"),
        Part(circlePath(12f, 12f, 2.5f)),
    )
    val Menu = buildIcon(2f, Part("M4 8h16M4 16h10"))
    val Search = buildIcon(2f, Part(circlePath(11f, 11f, 6.5f)), Part("M20 20l-4-4"))
    val Locate = buildIcon(
        1.8f,
        Part(circlePath(12f, 12f, 3f)),
        Part(circlePath(12f, 12f, 8f)),
        Part("M12 2v3M12 19v3M2 12h3M19 12h3"),
    )
    val Radio = buildIcon(
        1.8f,
        Part(rrectPath(3f, 8f, 18f, 13f, 3f)),
        Part("M7 8l9-5"),
        Part(circlePath(16f, 14.5f, 2.5f)),
        Part("M7 13h4M7 16.5h4"),
    )
    val Mic = buildIcon(2f, Part(rrectPath(9f, 3f, 6f, 11f, 3f)), Part("M5 11a7 7 0 0 0 14 0M12 18v3"))
    val Chat = buildIcon(1.8f, Part("M20 12a8 8 0 0 1-11.6 7.1L4 20l1-4.2A8 8 0 1 1 20 12z"))
    val Bell = buildIcon(1.8f, Part("M6 16v-5a6 6 0 0 1 12 0v5l2 2H4z"), Part("M10 21h4"))
    val Plus = buildIcon(2.2f, Part("M12 5v14M5 12h14"))
    val Sliders = buildIcon(
        1.8f,
        Part("M5 4v16M12 4v16M19 4v16"),
        Part(circlePath(5f, 10f, 2.2f)),
        Part(circlePath(12f, 15f, 2.2f)),
        Part(circlePath(19f, 8f, 2.2f)),
    )
    val Speaker = buildIcon(1.8f, Part("M4 10v4h4l5 4V6L8 10H4z"), Part("M16 9a4 4 0 0 1 0 6"))
    val UserPlus = buildIcon(
        1.8f,
        Part(circlePath(9f, 8f, 3.5f)),
        Part("M2.5 20c.8-4 3.4-6 6.5-6s5.7 2 6.5 6"),
        Part("M17 8h5M19.5 5.5v5"),
    )
    val Clock = buildIcon(1.8f, Part(circlePath(12f, 12f, 8f)), Part("M12 8v4l3 2"))
    val Play = buildIcon(2f, Part("M8 5v14l11-7z", fill = true))
    val Pause = buildIcon(2f, Part(rrectPath(6f, 5f, 4f, 14f, 1f), fill = true), Part(rrectPath(14f, 5f, 4f, 14f, 1f), fill = true))
    val Truck = buildIcon(
        1.8f,
        Part("M3 7h11v9H3zM14 10h4l3 3v3h-7z"),
        Part(circlePath(7f, 18f, 2f)),
        Part(circlePath(17f, 18f, 2f)),
    )
    val DoubleCheck = buildIcon(3f, Part("M3 12l4 4L15 8M9 16l2 2 8-8"))
    val Send = buildIcon(2f, Part("M22 2L11 13M22 2l-7 20-4-9-9-4z"))
    val Star = buildIcon(1.8f, Part("M12 3l2.6 5.6 6.1.6-4.6 4.1 1.3 6-5.4-3.2-5.4 3.2 1.3-6-4.6-4.1 6.1-.6z"))
    val StarFilled = buildIcon(1f, Part("M12 3l2.6 5.6 6.1.6-4.6 4.1 1.3 6-5.4-3.2-5.4 3.2 1.3-6-4.6-4.1 6.1-.6z", fill = true))
    val Share = buildIcon(1.9f, Part("M12 3v13"), Part("M8 7l4-4 4 4"), Part("M5 12v7a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-7"))
    val SkipBack = buildIcon(2f, Part("M18 6v12l-9-6zM7 6v12"))
    val Prev = buildIcon(2f, Part("M11 6v12l-8-6zM20 6v12l-8-6z"))
    val Next = buildIcon(2f, Part("M4 6v12l8-6zM13 6v12l8-6z"))
    // Medidor / velocímetro — alerta de exceso de velocidad.
    val Gauge = buildIcon(
        1.9f,
        Part("M4 16a8 8 0 0 1 16 0"),
        Part("M12 16l4-4"),
        Part(circlePath(12f, 16f, 1.3f), fill = true),
    )
    // Triángulo de alerta — severidad crítica / fallback.
    val AlertTriangle = buildIcon(
        1.9f,
        Part("M12 4L21 19H3z"),
        Part("M12 10v4"),
        Part(circlePath(12f, 16.5f, 0.8f), fill = true),
    )
}
