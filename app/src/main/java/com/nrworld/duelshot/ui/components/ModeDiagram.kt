package com.nrworld.duelshot.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nrworld.duelshot.model.RecordingMode

/** Litet layoutdiagram som visar hur ett inspelningsläge sparas. */
@Composable
fun ModeDiagram(mode: RecordingMode, color: Color, width: Dp, height: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = width, height = height)) {
        val w = size.width
        val h = size.height
        val line = 1.5.dp.toPx()
        val radius = CornerRadius(4.dp.toPx())
        fun frame(x: Float, y: Float, fw: Float, fh: Float) =
            drawRoundRect(color, Offset(x, y), Size(fw, fh), radius, style = Stroke(line))
        fun filled(x: Float, y: Float, fw: Float, fh: Float) =
            drawRoundRect(color, Offset(x, y), Size(fw, fh), radius)

        when (mode) {
            RecordingMode.DUAL_VIEW -> {
                frame(0f, 0f, w, h)
                filled(w * 0.6f, h * 0.56f, w * 0.32f, h * 0.32f)
            }
            RecordingMode.SEPARATE -> {
                frame(0f, 0f, w * 0.46f, h)
                frame(w * 0.54f, 0f, w * 0.46f, h)
            }
            RecordingMode.BOTH -> {
                frame(0f, 0f, w * 0.5f, h)
                filled(w * 0.1f, h * 0.56f, w * 0.26f, h * 0.28f)
                frame(w * 0.6f, 0f, w * 0.4f, h * 0.46f)
                frame(w * 0.6f, h * 0.54f, w * 0.4f, h * 0.46f)
            }
        }
    }
}
