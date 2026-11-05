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
enum class DuelIconKind { HOME, GALLERY, SETTINGS, GEM, CHECK, CHEVRON, CLOSE, SWITCH, SWAP, PLAY, PAUSE, CAMERA, VIDEO, CROWN, INFO, PIP, COPY, GRID, SHARE, TRASH, FOLDER, STATS, PLUS }

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
            DuelIconKind.SWAP -> {
                drawPath(pathOf(listOf(p(4.5f, 8.5f), p(17.5f, 8.5f), p(14.5f, 5.5f))), tint, style = stroke)
                drawPath(pathOf(listOf(p(19.5f, 15.5f), p(6.5f, 15.5f), p(9.5f, 18.5f))), tint, style = stroke)
            }
            DuelIconKind.PLAY -> drawPath(pathOf(listOf(p(8f, 5.5f), p(19f, 12f), p(8f, 18.5f)), close = true), tint)
            DuelIconKind.PAUSE -> {
                drawRoundRect(tint, topLeft = p(7.4f, 5f), size = Size(3.6f * s, 14f * s), cornerRadius = CornerRadius(1.2f * s))
                drawRoundRect(tint, topLeft = p(13f, 5f), size = Size(3.6f * s, 14f * s), cornerRadius = CornerRadius(1.2f * s))
            }
            DuelIconKind.PIP -> {
                drawRoundRect(
                    tint, topLeft = p(3f, 5f), size = Size(18f * s, 14f * s),
                    cornerRadius = CornerRadius(3f * s), style = stroke
                )
                drawRoundRect(
                    tint, topLeft = p(11.5f, 10.5f), size = Size(7.5f * s, 6.5f * s),
                    cornerRadius = CornerRadius(1.6f * s)
                )
            }
            DuelIconKind.CAMERA -> {
                drawRoundRect(
                    tint, topLeft = p(3f, 7f), size = Size(18f * s, 13f * s),
                    cornerRadius = CornerRadius(3f * s), style = stroke
                )
                drawPath(pathOf(listOf(p(8f, 7f), p(9.5f, 4.5f), p(14.5f, 4.5f), p(16f, 7f))), tint, style = stroke)
                drawCircle(tint, radius = 3.2f * s, center = p(12f, 13.5f), style = stroke)
            }
            DuelIconKind.VIDEO -> {
                drawRoundRect(
                    tint, topLeft = p(2.5f, 6.5f), size = Size(13f * s, 11f * s),
                    cornerRadius = CornerRadius(2.5f * s), style = stroke
                )
                drawPath(pathOf(listOf(p(15.5f, 10.5f), p(21.5f, 7f), p(21.5f, 17f), p(15.5f, 13.5f))), tint, style = stroke)
            }
            DuelIconKind.CROWN -> drawPath(
                pathOf(listOf(p(3f, 8f), p(7.5f, 12.5f), p(12f, 5f), p(16.5f, 12.5f), p(21f, 8f), p(19f, 19f), p(5f, 19f)), close = true),
                tint, style = stroke
            )
            DuelIconKind.COPY -> {
                drawRoundRect(
                    tint, topLeft = p(9f, 3f), size = Size(12f * s, 12f * s),
                    cornerRadius = CornerRadius(2.5f * s), style = stroke
                )
                drawRoundRect(
                    tint, topLeft = p(3f, 9f), size = Size(12f * s, 12f * s),
                    cornerRadius = CornerRadius(2.5f * s), style = stroke
                )
            }
            DuelIconKind.GRID -> {
                drawRoundRect(
                    tint, topLeft = p(3f, 5f), size = Size(18f * s, 14f * s),
                    cornerRadius = CornerRadius(3f * s), style = stroke
                )
                drawPath(pathOf(listOf(p(9f, 5f), p(9f, 19f))), tint, style = stroke)
                drawPath(pathOf(listOf(p(15f, 5f), p(15f, 19f))), tint, style = stroke)
                drawPath(pathOf(listOf(p(3f, 9.7f), p(21f, 9.7f))), tint, style = stroke)
                drawPath(pathOf(listOf(p(3f, 14.3f), p(21f, 14.3f))), tint, style = stroke)
            }
            DuelIconKind.SHARE -> {
                drawPath(pathOf(listOf(p(12f, 3.5f), p(12f, 14.5f))), tint, style = stroke)
                drawPath(pathOf(listOf(p(8f, 7.5f), p(12f, 3.5f), p(16f, 7.5f))), tint, style = stroke)
                drawPath(
                    pathOf(listOf(p(6f, 12f), p(6f, 20f), p(18f, 20f), p(18f, 12f))),
                    tint, style = stroke
                )
            }
            DuelIconKind.TRASH -> {
                drawPath(pathOf(listOf(p(4f, 7f), p(20f, 7f))), tint, style = stroke)
                drawPath(pathOf(listOf(p(9.5f, 7f), p(9.5f, 4.5f), p(14.5f, 4.5f), p(14.5f, 7f))), tint, style = stroke)
                drawPath(
                    pathOf(listOf(p(6.5f, 7f), p(7.6f, 20f), p(16.4f, 20f), p(17.5f, 7f))),
                    tint, style = stroke
                )
                drawPath(pathOf(listOf(p(10.5f, 10.5f), p(11f, 16.5f))), tint, style = stroke)
                drawPath(pathOf(listOf(p(13.5f, 10.5f), p(13f, 16.5f))), tint, style = stroke)
            }
            DuelIconKind.INFO -> {
                drawCircle(tint, radius = 9f * s, center = p(12f, 12f), style = stroke)
                drawPath(pathOf(listOf(p(12f, 11f), p(12f, 16.5f))), tint, style = stroke)
                drawCircle(tint, radius = 1.2f * s, center = p(12f, 7.8f))
            }
            DuelIconKind.FOLDER -> {
                drawPath(
                    pathOf(
                        listOf(
                            p(3f, 6f), p(10.5f, 6f), p(13f, 8.6f), p(21f, 8.6f),
                            p(21f, 19f), p(3f, 19f)
                        ),
                        close = true
                    ),
                    tint, style = stroke
                )
            }
            DuelIconKind.STATS -> {
                // Uppåtgående trendlinje med noder – raffinerad analytics-glyf.
                drawPath(pathOf(listOf(p(4f, 18f), p(9f, 12.5f), p(13.5f, 15f), p(20f, 4.5f))), tint, style = stroke)
                drawCircle(tint, radius = 2.5f * s, center = p(20f, 4.5f))
                drawCircle(tint, radius = 1.5f * s, center = p(4f, 18f))
            }
            DuelIconKind.PLUS -> {
                drawPath(pathOf(listOf(p(12f, 4f), p(12f, 20f))), tint, style = stroke)
                drawPath(pathOf(listOf(p(4f, 12f), p(20f, 12f))), tint, style = stroke)
            }
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
