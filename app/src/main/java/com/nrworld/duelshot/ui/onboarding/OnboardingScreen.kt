package com.nrworld.duelshot.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nrworld.duelshot.ui.components.DuelIcon
import com.nrworld.duelshot.ui.components.DuelIconKind
import com.nrworld.duelshot.ui.theme.DuelBg
import com.nrworld.duelshot.ui.theme.DuelGradient
import com.nrworld.duelshot.ui.theme.DuelText
import com.nrworld.duelshot.ui.theme.DuelTextMuted

private data class OnboardingStep(val icon: DuelIconKind, val title: String, val body: String)

private val STEPS = listOf(
    OnboardingStep(
        DuelIconKind.CAMERA,
        "Two cameras, one take",
        "Record the front and back camera at the same time – one composed video, two files, or both."
    ),
    OnboardingStep(
        DuelIconKind.PIP,
        "Make the shot yours",
        "Drag, resize, pause or hide the small camera while you film. Pinch to zoom either camera."
    ),
    OnboardingStep(
        DuelIconKind.GALLERY,
        "Ready to share",
        "Clips land in Movies/DualShots. Preview, share or delete them without leaving the app."
    )
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    var index by remember { mutableStateOf(0) }
    val step = STEPS[index]

    Column(
        modifier = Modifier.fillMaxSize().background(DuelBg).padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(0.22f))

        Box(
            modifier = Modifier.size(112.dp).clip(RoundedCornerShape(32.dp)).background(DuelGradient),
            contentAlignment = Alignment.Center
        ) {
            DuelIcon(DuelIconKind.CAMERA, Color(0xFF04060D), 48.dp)
        }

        Spacer(Modifier.height(30.dp))
        Text(
            step.title, color = DuelText, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            step.body, color = DuelTextMuted, fontSize = 14.sp, lineHeight = 21.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.weight(0.28f))

        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(7.dp)) {
            STEPS.indices.forEach { i ->
                Box(
                    Modifier
                        .height(4.dp)
                        .width(if (i == index) 22.dp else 8.dp)
                        .clip(CircleShape)
                        .background(if (i == index) DuelText else Color.White.copy(alpha = .22f))
                )
            }
        }

        Spacer(Modifier.height(26.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(DuelGradient)
                .clickable { if (index < STEPS.lastIndex) index++ else onDone() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (index == STEPS.lastIndex) "Start recording" else "Next",
                color = Color(0xFF04060D), fontWeight = FontWeight.Bold, fontSize = 15.sp
            )
        }
        TextButton(onClick = onDone) {
            Text("Skip", color = DuelTextMuted, fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
    }
}
