package com.nrworld.duelshot.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import com.nrworld.duelshot.ui.theme.DuelOrange
import com.nrworld.duelshot.ui.theme.DuelOrangeLight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nrworld.duelshot.ui.theme.DuelGradient
import com.nrworld.duelshot.ui.theme.DuelGold
import com.nrworld.duelshot.ui.theme.DuelLine
import com.nrworld.duelshot.ui.theme.DuelMagenta
import com.nrworld.duelshot.ui.theme.DuelAccent
import com.nrworld.duelshot.ui.theme.DuelSurface
import com.nrworld.duelshot.ui.theme.DuelSurface2
import com.nrworld.duelshot.ui.theme.DuelText
import com.nrworld.duelshot.ui.theme.DuelTextFaint
import com.nrworld.duelshot.ui.theme.DuelTextMuted
import com.nrworld.duelshot.ui.theme.GoldGradient

/** Logotyp: två linser i en orbital. */
@Composable
fun DuelLogoMark(size: Dp = 64.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f
        val lensR = w * 0.17f
        val gap = w * 0.20f
        drawArc(
            brush = DuelGradient,
            startAngle = 20f, sweepAngle = 320f, useCenter = false,
            topLeft = Offset(cx - w * 0.44f, cy - h * 0.30f),
            size = Size(w * 0.88f, h * 0.60f),
            style = Stroke(width = w * 0.03f)
        )
        listOf(cx - gap, cx + gap).forEachIndexed { i, x ->
            drawCircle(color = Color(0xFF05070D), radius = lensR + w * 0.03f, center = Offset(x, cy))
            drawCircle(
                brush = DuelGradient, radius = lensR, center = Offset(x, cy),
                style = Stroke(width = w * 0.035f)
            )
            drawCircle(color = Color(0xFF0B1024), radius = lensR - w * 0.02f, center = Offset(x, cy))
            drawCircle(
                color = if (i == 0) DuelAccent else DuelMagenta,
                radius = lensR * 0.35f,
                center = Offset(x - lensR * 0.2f, cy - lensR * 0.2f)
            )
        }
    }
}

/** Ord- "Duel"(vit) + "Shots"(gradient). */
@Composable
fun WordmarkText(fontSize: TextUnit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("Duel", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = fontSize)
        Text(
            "Shots",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = fontSize,
                fontWeight = FontWeight.ExtraBold,
                brush = DuelGradient
            )
        )
    }
}

/** Primär gradientknapp. */
@Composable
fun GradientButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(DuelGradient)
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
            .drawWithContent {
                if (enabled) drawContent()
                else {
                    drawContent()
                    drawRect(Color(0xFF05070D).copy(alpha = 0.5f))
                }
            }
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/** Guld-knapp för premium. */
@Composable
fun GoldButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Go Premium"
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(GoldGradient)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DuelIcon(DuelIconKind.CROWN, Color(0xFF04060D), 16.dp)
            Spacer(Modifier.width(8.dp))
            Text(text.uppercase(), color = Color(0xFF04060D), fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        }
    }
}

/** Mörkt kort med kant. */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    borderColor: Color = DuelLine,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DuelSurface)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp)
    ) { content() }
}

/** Sidrubrik med undertext. */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            title,
            color = DuelText,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 32.sp,
            letterSpacing = (-0.8).sp
        )
        if (subtitle != null) {
            Text(subtitle, color = DuelTextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Rund radioknapp – fylld med gradient när vald. */
@Composable
fun RadioDot(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(22.dp)
            .border(1.5.dp, if (selected) DuelAccent else DuelTextFaint, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Box(Modifier.size(11.dp).clip(CircleShape).background(DuelGradient))
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = DuelTextMuted,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 1.5.sp,
        modifier = modifier.padding(vertical = 6.dp)
    )
}

/** Segmenterad väljare (t.ex. 1080p / 4K). */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(DuelSurface2)
            .border(1.dp, DuelLine, RoundedCornerShape(10.dp))
    ) {
        options.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .then(if (selected) Modifier.background(DuelGradient) else Modifier)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    label,
                    color = if (selected) Color(0xFF04060D) else DuelTextMuted,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.sp
                )
            }
        }
    }
}

/** Premium-modal i studiodesignen: kronikon, funktionslista och orange CTA. */
@Composable
fun PremiumDialog(onDismiss: () -> Unit, onUpgrade: () -> Unit, feature: String) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF141724), Color(0xFF0D0F18))))
                .border(1.dp, DuelOrange.copy(alpha = .4f), RoundedCornerShape(28.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(GoldGradient),
                contentAlignment = Alignment.Center
            ) { DuelIcon(DuelIconKind.CROWN, Color.Black, 30.dp) }
            Spacer(Modifier.height(14.dp))
            Text("DuelShots PRO", color = DuelText, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
            Text(
                feature.uppercase(),
                color = DuelTextMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                letterSpacing = 1.sp, modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(18.dp))
            Column(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = .4f))
                    .border(1.dp, DuelLine, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(
                    "4K Ultra HD recording",
                    "HEVC codec",
                    "60fps & custom bitrate",
                    "Per-camera aspect control",
                    "Watermark-free export"
                ).forEach { line ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(18.dp).clip(CircleShape)
                                .background(DuelOrange.copy(alpha = .2f))
                                .border(1.dp, DuelOrange, CircleShape),
                            contentAlignment = Alignment.Center
                        ) { DuelIcon(DuelIconKind.CHECK, DuelOrangeLight, 11.dp) }
                        Spacer(Modifier.width(10.dp))
                        Text(line, color = Color(0xFFE2E8F0), fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DuelGradient)
                    .clickable(onClick = onUpgrade)
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "UNLOCK PRO",
                    color = Color(0xFF04060D), fontWeight = FontWeight.Black,
                    fontSize = 14.sp, letterSpacing = 1.2.sp
                )
            }
            TextButton(onClick = onDismiss) {
                Text("Close", color = DuelTextMuted, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }
    }
}
