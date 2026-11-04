package com.nrworld.duelshot.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Egna linjeikoner – avsiktligt inga standard-mobilikoner. */
enum class DuelIconKind { HOME, GALLERY, SETTINGS, GEM, CHECK, CHEVRON, CLOSE, SWITCH, PLAY }

/** Ritar en ikon på en 24×24-yta, skalad till [size]. */
@Composable
fun DuelIcon(kind: DuelIconKind, tint: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 24f
        val stroke = Stroke(width = 1.9f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        when (kind) {
            DuelIconKind.HOME -> drawPath(
                pathOf(listOf(p(3f, 10.5f), p(12f, 3.5f), p(21f, 10.5f), p(21f, 20f), p(15f, 20f),
                    p(15f, 14.5f), p(9f, 14.5f), p(9f, 20f), p(3f, 20f)), close = true),
                tint, style = stroke
            )
            DuelIconKind.GALLERY -> {
                drawRoundRect(
                    tint, topLeft = p(3f, 5f), size = Size(18f * s, 14f * s),
                    cornerRadius = CornerRadius(3f * s), style = stroke
                )
                drawPath(pathOf(listOf(p(10f, 9.5f), p(15.5f, 12f), p(10f, 14.5f)), close = true), tint)
            }
            DuelIconKind.SETTINGS -> {
                listOf(6f to 8f, 12f to 16f, 18f to 11f).forEach { (y, x) ->
                    drawPath(pathOf(listOf(p(3f, y), p(21f, y))), tint, style = stroke)
                    drawCircle(tint, radius = 2.8f * s, center = p(x, y))
                }
            }
            DuelIconKind.GEM -> {
                drawPath(pathOf(listOf(p(7f, 4f), p(17f, 4f), p(21f, 9f), p(12f, 20f), p(3f, 9f)), close = true), tint, style = stroke)
                drawPath(pathOf(listOf(p(3f, 9f), p(21f, 9f))), tint, style = stroke)
                drawPath(pathOf(listOf(p(9f, 9f), p(12f, 20f), p(15f, 9f))), tint, style = stroke)
            }
            DuelIconKind.CHECK -> drawPath(pathOf(listOf(p(5f, 12.5f), p(10f, 17.5f), p(19f, 7f))), tint, style = stroke)
            DuelIconKind.CHEVRON -> drawPath(pathOf(listOf(p(9f, 5f), p(16f, 12f), p(9f, 19f))), tint, style = stroke)
            DuelIconKind.CLOSE -> {
                drawPath(pathOf(listOf(p(6f, 6f), p(18f, 18f))), tint, style = stroke)
                drawPath(pathOf(listOf(p(18f, 6f), p(6f, 18f))), tint, style = stroke)
            }
            DuelIconKind.SWITCH -> {
                val c = p(12f, 12f)
                val r = 8f * s
                drawArc(tint, startAngle = 200f, sweepAngle = 140f, useCenter = false,
                    topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2), style = stroke)
                drawArc(tint, startAngle = 20f, sweepAngle = 140f, useCenter = false,
                    topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2), style = stroke)
                arrowAt(c, r, 340f, tint, s)
                arrowAt(c, r, 160f, tint, s)
            }
            DuelIconKind.PLAY -> drawPath(pathOf(listOf(p(8f, 5.5f), p(19f, 12f), p(8f, 18.5f)), close = true), tint)
        }
    }
}

private fun pathOf(points: List<Offset>, close: Boolean = false): Path = Path().apply {
    moveTo(points[0].x, points[0].y)
    points.drop(1).forEach { lineTo(it.x, it.y) }
    if (close) close()
}

/** Pilspets i rörelseriktningen vid slutet av en cirkelbåge (för SWITCH). */
private fun DrawScope.arrowAt(center: Offset, r: Float, angleDeg: Float, color: Color, s: Float) {
    val a = Math.toRadians(angleDeg.toDouble())
    val radial = Offset(cos(a).toFloat(), sin(a).toFloat())
    val tangent = Offset(-radial.y, radial.x)
    val end = center + radial * r
    val tip = end + tangent * 3f * s
    drawPath(pathOf(listOf(tip, end + radial * 2.6f * s, end - radial * 2.6f * s), close = true), color)
}
