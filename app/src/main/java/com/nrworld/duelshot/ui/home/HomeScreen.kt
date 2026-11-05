package com.nrworld.duelshot.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.withStyle
import com.nrworld.duelshot.R
import com.nrworld.duelshot.ui.AppViewModel
import com.nrworld.duelshot.ui.components.Brand
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
import com.nrworld.duelshot.ui.gallery.EntryThumb
import com.nrworld.duelshot.ui.gallery.LibraryEntry

/** Home enligt design/reference/home.png (mått i referenspixlar). */
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
        // Hero: telefonerna (bild ur referensen), logga, rubrik.
        Box(Modifier.fillMaxWidth().height(rp(513))) {
            Image(
                painterResource(R.drawable.ref_hero), contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.absoluteOffset(rp(452), rp(10)).size(rp(572), rp(498))
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()
                        // Mjuka kanter mot bakgrunden (vänster och nederkant).
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

        // START RECORDING
        Box(
            Modifier.padding(start = rp(62), end = rp(58)).fillMaxWidth().height(rp(124))
                .shadow(rp(28), RoundedCornerShape(rp(36)), spotColor = RefOrange, ambientColor = RefOrange)
                .clip(RoundedCornerShape(rp(36)))
                .background(Brush.horizontalGradient(listOf(Color(0xFFFF5A0A), Color(0xFFFF7410), Color(0xFFFF9A1C))))
                .clickable(onClick = onStart)
        ) {
            Box(
                Modifier.absoluteOffset(rp(36), rp(27)).size(rp(89), rp(70))
                    .clip(RoundedCornerShape(rp(18))).background(Color(0x4D8C2300)),
                contentAlignment = Alignment.Center
            ) { RefIcon(RefIconKind.VIDEO, Color(0xFF2A0D02), rp(54), stroke = 2f) }
            RText("START RECORDING", 160, 42, 33, 40, color = Color(0xFF1C0D05), weight = FontWeight.Bold, ls = 1.6)
            RefIcon(RefIconKind.CHEVRON, Color(0xFF1C0D05), rp(34), Modifier.absoluteOffset(rp(826), rp(45)), stroke = 2.4f)
        }

        // Fyra genvägar
        Spacer(Modifier.height(rp(30)))
        Row(
            Modifier.padding(start = rp(62), end = rp(58)).fillMaxWidth().height(rp(216)),
            horizontalArrangement = Arrangement.spacedBy(rp(21))
        ) {
            QuickCard(RefIconKind.LAYERS, "My Recordings", "View and manage\nyour videos", onOpenGallery)
            QuickCard(RefIconKind.GEAR, "Settings", "Customize your\nexperience", onOpenSettings)
            QuickCard(RefIconKind.CROWN, "Premium", if (vm.isPremium) "Premium\nactive" else "Unlock more\nfeatures", onOpenPremium)
            QuickCard(RefIconKind.BULB, "Tips", "Get the most\nout of DualShots", onOpenTips)
        }

        // Senaste inspelningarna
        Spacer(Modifier.height(rp(27)))
        Box(
            Modifier.padding(start = rp(62), end = rp(58)).fillMaxWidth().height(rp(342))
                .clip(RoundedCornerShape(rp(28)))
                .background(Brush.verticalGradient(listOf(Color(0xFF121926), Color(0xFF0F1520))))
                .border(rp(2), Color(0xFF212A3B), RoundedCornerShape(rp(28)))
        ) {
            RText("Recent Recordings", 26, 27, 30, 36, weight = FontWeight.Medium)
            SeeAll(onOpenGallery, Modifier.align(Alignment.TopEnd).absoluteOffset(x = -rp(33), y = rp(30)))
            Row(
                Modifier.absoluteOffset(rp(24), rp(90)).padding(end = rp(48)).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(rp(24))
            ) {
                recent.take(3).forEach { e ->
                    Box(Modifier.weight(1f).height(rp(230)).clickable { onOpenEntry(e) }) {
                        EntryThumb(e, Modifier.fillMaxWidth().height(rp(155)).clip(RoundedCornerShape(rp(14))))
                        RText(e.title, 2, 168, 22, 26, weight = FontWeight.Medium, maxWidth = 220)
                        RText(e.date, 2, 199, 19, 24, color = RefMuted, maxWidth = 230)
                        RefIcon(RefIconKind.DOTS, RefSub, rp(28), Modifier.align(Alignment.TopEnd).absoluteOffset(x = -rp(2), y = rp(182)))
                    }
                }
            }
        }
        Spacer(Modifier.height(rp(40)))
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.QuickCard(
    icon: RefIconKind, title: String, sub: String, onClick: () -> Unit
) {
    Box(
        Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(rp(26)))
            .background(Brush.verticalGradient(listOf(Color(0xFF131A28), Color(0xFF0F1420))))
            .border(rp(2), Color(0xFF212A3B), RoundedCornerShape(rp(26)))
            .clickable(onClick = onClick)
    ) {
        RefIcon(icon, RefOrange, rp(48), Modifier.absoluteOffset(rp(28), rp(31)))
        RText(title, 28, 98, 24, 30, weight = FontWeight.Medium)
        RText(sub, 28, 136, 19, 26, color = RefMuted, ls = -.3)
        RefIcon(RefIconKind.CHEVRON, RefMuted, rp(28), Modifier.align(Alignment.TopEnd).absoluteOffset(x = -rp(22), y = rp(160)), stroke = 2.2f)
    }
}

