package com.nrworld.duelshot.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.nrworld.duelshot.R
import com.nrworld.duelshot.ui.AppViewModel
import com.nrworld.duelshot.ui.components.Brand
import com.nrworld.duelshot.ui.components.LocalRefFont
import com.nrworld.duelshot.ui.components.LocalRefStretch
import com.nrworld.duelshot.ui.components.RCircle
import com.nrworld.duelshot.ui.components.RText
import com.nrworld.duelshot.ui.components.RefIcon
import com.nrworld.duelshot.ui.components.RefIconKind
import com.nrworld.duelshot.ui.components.RefLabel
import com.nrworld.duelshot.ui.components.RefMuted
import com.nrworld.duelshot.ui.components.RefOrange
import com.nrworld.duelshot.ui.components.RefSub
import com.nrworld.duelshot.ui.components.SeeAll
import com.nrworld.duelshot.ui.components.rp
import com.nrworld.duelshot.ui.components.rs
import com.nrworld.duelshot.ui.components.rv
import com.nrworld.duelshot.ui.gallery.EntryThumb
import com.nrworld.duelshot.ui.gallery.LibraryEntry

/** Home enligt design/reference/home.png, anpassad för hög mobilskärm (större text, 2×2-genvägar). */
@Composable
fun HomeScreen(
    vm: AppViewModel,
    recent: List<LibraryEntry>,
    onStart: () -> Unit,
    onOpenPremium: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTips: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenEntry: (LibraryEntry) -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Hero: bilden och rubriken sträcks mindre än resten så telefonerna inte förvrängs.
        val heroStretch = 1f + (LocalRefStretch.current - 1f) * 0.55f
        val heroFont = 1f + (LocalRefFont.current - 1f) * 0.4f
        CompositionLocalProvider(LocalRefStretch provides heroStretch, LocalRefFont provides heroFont) {
            Box(Modifier.fillMaxWidth().height(rv(513))) {
                Image(
                    painterResource(R.drawable.ref_hero), contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.absoluteOffset(rp(452), rv(10)).size(rp(572), rv(498))
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        .drawWithContent {
                            drawContent()
                            val fade = 48f / 572f
                            drawRect(Brush.horizontalGradient(0f to Color.Transparent, fade to Color.Black), blendMode = BlendMode.DstIn)
                            drawRect(Brush.verticalGradient(0.88f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                        }
                )
                Brand(57, 31, 48)
                RCircle(RefIconKind.USER, 917, 26, 62, 38, bg = Color(0xFF1C2335), onClick = onOpenAbout)
                RText(
                    buildAnnotatedString {
                        append("RECORD BOTH SIDES.\n")
                        withStyle(SpanStyle(color = RefOrange)) { append("AT ONCE.") }
                    },
                    62, 132.5, 22, 35, color = RefLabel, mono = true, ls = .8
                )
                RText(
                    buildAnnotatedString {
                        append("Two\nPerspectives.\n")
                        withStyle(SpanStyle(color = RefOrange)) { append("One Story.") }
                    },
                    60, 223.5, 60, 59, weight = FontWeight.Bold, ls = -1
                )
                RText("Capture more. Create better.", 62, 416, 28, 34, color = RefSub)
            }
        }

        // START RECORDING
        Box(
            Modifier.padding(start = rp(62), end = rp(58)).fillMaxWidth().height(rv(124))
                .shadow(rp(28), RoundedCornerShape(rp(36)), spotColor = RefOrange, ambientColor = RefOrange)
                .clip(RoundedCornerShape(rp(36)))
                .background(Brush.horizontalGradient(listOf(Color(0xFFFF5A0A), Color(0xFFFF7410), Color(0xFFFF9A1C))))
                .clickable(onClick = onStart)
        ) {
            Box(
                Modifier.absoluteOffset(rp(36), rv(27)).size(rp(89), rv(70))
                    .clip(RoundedCornerShape(rp(18))).background(Color(0x4D8C2300)),
                contentAlignment = Alignment.Center
            ) { RefIcon(RefIconKind.VIDEO, Color(0xFF2A0D02), rp(54), stroke = 2f) }
            RText("START RECORDING", 160, 40, 33, 40, color = Color(0xFF1C0D05), weight = FontWeight.Bold, ls = 1.2)
            RefIcon(RefIconKind.CHEVRON, Color(0xFF1C0D05), rp(34), Modifier.absoluteOffset(rp(826), rv(45)), stroke = 2.4f)
        }

        // Fyra genvägar, 2×2 så att texten får plats.
        Spacer(Modifier.height(rv(30)))
        Column(
            Modifier.padding(start = rp(62), end = rp(58)).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(rv(18))
        ) {
            Row(Modifier.fillMaxWidth().height(rv(176)), horizontalArrangement = Arrangement.spacedBy(rp(21))) {
                QuickCard(RefIconKind.LAYERS, "My Recordings", "View and manage\nyour videos", onOpenGallery)
                QuickCard(RefIconKind.GEAR, "Settings", "Customize your\nexperience", onOpenSettings)
            }
            Row(Modifier.fillMaxWidth().height(rv(176)), horizontalArrangement = Arrangement.spacedBy(rp(21))) {
                QuickCard(RefIconKind.CROWN, "Premium", if (vm.isPremium) "Premium\nactive" else "Unlock more\nfeatures", onOpenPremium)
                QuickCard(RefIconKind.BULB, "Tips", "Get the most out\nof DualShots", onOpenTips)
            }
        }

        // Senaste inspelningarna
        Spacer(Modifier.height(rv(27)))
        Column(
            Modifier.padding(start = rp(62), end = rp(58)).fillMaxWidth()
                .clip(RoundedCornerShape(rp(28)))
                .background(Brush.verticalGradient(listOf(Color(0xFF121926), Color(0xFF0F1520))))
                .border(rp(2), Color(0xFF212A3B), RoundedCornerShape(rp(28)))
                .padding(horizontal = rp(24))
        ) {
            Box(Modifier.fillMaxWidth().height(rv(84))) {
                RText("Recent Recordings", 2, 27, 29, 36, weight = FontWeight.Medium)
                SeeAll(onOpenGallery, Modifier.align(Alignment.TopEnd).absoluteOffset(x = -rp(9), y = rv(30)))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(rp(20))) {
                recent.take(3).forEach { e ->
                    Column(Modifier.weight(1f).clickable { onOpenEntry(e) }) {
                        EntryThumb(e, Modifier.fillMaxWidth().aspectRatio(1.72f).clip(RoundedCornerShape(rp(14))))
                        Text(
                            e.title, color = Color.White, fontSize = rs(20), lineHeight = rs(26), fontWeight = FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = rv(10))
                        )
                        Text(e.date, color = RefMuted, fontSize = rs(16), lineHeight = rs(22), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(rv(26)))
        }
        Spacer(Modifier.height(rv(40)))
    }
}

@Composable
private fun RowScope.QuickCard(icon: RefIconKind, title: String, sub: String, onClick: () -> Unit) {
    Box(
        Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(rp(26)))
            .background(Brush.verticalGradient(listOf(Color(0xFF131A28), Color(0xFF0F1420))))
            .border(rp(2), Color(0xFF212A3B), RoundedCornerShape(rp(26)))
            .clickable(onClick = onClick)
    ) {
        RefIcon(icon, RefOrange, rp(54), Modifier.absoluteOffset(rp(28), rv(22)))
        RText(title, 28, 80, 23, 30, weight = FontWeight.Medium)
        RText(sub, 28, 114, 17, 24, color = RefMuted)
        RefIcon(RefIconKind.CHEVRON, RefMuted, rp(30), Modifier.align(Alignment.TopEnd).absoluteOffset(x = -rp(22), y = rv(122)), stroke = 2.2f)
    }
}
