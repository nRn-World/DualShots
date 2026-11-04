package com.nrworld.duelshot.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// DuelShots palett
val DuelBg = Color(0xFF05070D)
val DuelBg2 = Color(0xFF0A0E1A)
val DuelSurface = Color(0xFF141A2A)
val DuelSurface2 = Color(0xFF1B2236)
val DuelLine = Color(0x14FFFFFF)          // rgba(255,255,255,.08)
val DuelLineStrong = Color(0x24FFFFFF)

val DuelCyan = Color(0xFF22D3EE)
val DuelBlue = Color(0xFF2E7DFF)
val DuelPurple = Color(0xFF7C3AED)
val DuelMagenta = Color(0xFFE94FD0)
val DuelGold = Color(0xFFF5C24B)
val DuelGold2 = Color(0xFFFFB020)
val DuelRecord = Color(0xFFFF3B4E)
val DuelGreen = Color(0xFF34D399)

val DuelText = Color(0xFFFFFFFF)
val DuelTextMuted = Color(0xFFA7B0C4)
val DuelTextFaint = Color(0xFF6B768F)

/** Brandgradient: cyan → blå → lila → magenta. */
val DuelGradient = Brush.linearGradient(
    colors = listOf(DuelCyan, DuelBlue, DuelPurple, DuelMagenta)
)

/** Guldgradient för premium-ytor. */
val GoldGradient = Brush.linearGradient(
    colors = listOf(DuelGold, DuelGold2)
)

/** Enkel bakgrund med hela sidans mörka gradient + glöd. */
@Composable
fun DuelBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .background(DuelBg)
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF141B3A).copy(alpha = 0.6f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(200f, 0f),
                    radius = 900f
                )
            )
    ) { content() }
}
