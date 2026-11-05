package com.nrworld.duelshot.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// DuelShots palett (orange studio-design)
val DuelBg = Color(0xFF07080C)
val DuelBg2 = Color(0xFF0E1018)
val DuelSurface = Color(0xFF121522)
val DuelSurface2 = Color(0xFF1C2236)
val DuelLine = Color(0x14FFFFFF)          // white 8%
val DuelLineStrong = Color(0x24FFFFFF)    // white 14%

/** Primär accent – används för val, markeringar och aktiva lägen. */
val DuelAccent = Color(0xFFFF7700)
val DuelOrangeDeep = Color(0xFFFF4400)
val DuelOrange = Color(0xFFFF6600)
val DuelOrangeLight = Color(0xFFFF8800)
val DuelBlue = Color(0xFF2E7DFF)
val DuelPurple = Color(0xFF7C3AED)
val DuelMagenta = Color(0xFFE94FD0)
val DuelGold = Color(0xFFFF9900)
val DuelGold2 = Color(0xFFFFAA00)
val DuelRecord = Color(0xFFFF3300)
val DuelGreen = Color(0xFF34D399)

val DuelText = Color(0xFFFFFFFF)
val DuelTextMuted = Color(0xFF94A3B8)
val DuelTextFaint = Color(0xFF64748B)

/** Brandgradient: orange. */
val DuelGradient = Brush.linearGradient(
    colors = listOf(DuelOrangeDeep, DuelOrange, DuelOrangeLight)
)

/** Guldgradient för premium-ytor (orange). */
val GoldGradient = Brush.linearGradient(
    colors = listOf(DuelOrangeDeep, DuelOrangeLight, DuelGold2)
)

// === Glass & Premium palette ===
val DuelViolet = Color(0xFF8B5CF6)
val DuelIndigo = Color(0xFF6366F1)
val DuelMagentaP = Color(0xFFD946EF)
val DuelCyan = Color(0xFF22D3EE)

/** Levande gradientbakgrund för glass & premium-skärmar. */
val DuelBgGradient = Brush.verticalGradient(
    listOf(Color(0xFF1A0B2E), Color(0xFF2A0F4D), Color(0xFF20103C), Color(0xFF0B0B18))
)

/** Frostningsglas – ljus, translucen. */
val GlassFill = Color.White.copy(alpha = 0.07f)
val GlassFillStrong = Color.White.copy(alpha = 0.11f)
val GlassBorder = Color.White.copy(alpha = 0.18f)
val GlassHighlight = Color.White.copy(alpha = 0.50f)

/** Premium glass & gradient-bakgrund med flera glödor. */
@Composable
fun DuelPremiumBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.background(DuelBgGradient)) {
        Box(Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(DuelViolet.copy(alpha = 0.42f), Color.Transparent), radius = 820f)
        ))
        Box(Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(DuelMagentaP.copy(alpha = 0.30f), Color.Transparent), center = Offset(720f, -50f), radius = 740f)
        ))
        Box(Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(DuelOrange.copy(alpha = 0.24f), Color.Transparent), center = Offset(110f, 920f), radius = 800f)
        ))
        Box(Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(DuelCyan.copy(alpha = 0.14f), Color.Transparent), center = Offset(920f, 720f), radius = 620f)
        ))
        content()
    }
}

/** Enkel bakgrund med hela sidans mörka gradient + glöd. */
@Composable
fun DuelBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .background(DuelBg)
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF3A1606).copy(alpha = 0.6f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(200f, 0f),
                    radius = 900f
                )
            )
    ) { content() }
}
