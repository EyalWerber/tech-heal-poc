package com.ptsdalert.infrastructure.alert

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ptsdalert.domain.model.ArousalState

private const val HOLD_DURATION_MS = 10_000

class LockScreenAlertActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        val stateName = intent.getStringExtra(EXTRA_STATE) ?: ArousalState.HYPERAROUSAL.name
        val state = ArousalState.valueOf(stateName)

        data class AlertContent(val bgColor: Color, val headline: String, val body: String, val dismissLabel: String)
        val content = when (state) {
            ArousalState.HYPERAROUSAL -> AlertContent(
                Color(0xFFC62828),
                "High arousal detected",
                "Your heart rate is elevated.\nTake a slow breath.",
                "I'm working out"
            )
            ArousalState.HYPOAROUSAL -> AlertContent(
                Color(0xFF1565C0),
                "Low arousal detected",
                "Your heart rate is very low.\nTry to ground yourself.",
                "I'm ok"
            )
            ArousalState.NORMAL -> AlertContent(Color.DarkGray, "Monitoring", "", "Dismiss")
        }

        setContent {
            var isHeld by remember { mutableStateOf(false) }
            val progress = remember { Animatable(0f) }

            LaunchedEffect(isHeld) {
                if (isHeld) {
                    progress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = HOLD_DURATION_MS, easing = LinearEasing)
                    )
                    if (isHeld) {
                        DismissSignal.flow.tryEmit(Unit)
                        finish()
                    }
                } else {
                    progress.snapTo(0f)
                }
            }

            val secsLeft = ((1f - progress.value) * (HOLD_DURATION_MS / 1000)).toInt() + 1

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(content.bgColor)
                    .padding(40.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = content.headline,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (content.body.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = content.body,
                        fontSize = 18.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        lineHeight = 26.sp
                    )
                }
                Spacer(modifier = Modifier.height(48.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    isHeld = true
                                    tryAwaitRelease()
                                    isHeld = false
                                }
                            )
                        }
                ) {
                    // Fill grows left→right as user holds
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress.value)
                            .background(Color.White.copy(alpha = 0.35f))
                    )
                    Text(
                        text = if (isHeld) "${content.dismissLabel} — $secsLeft s"
                               else "Hold to confirm: ${content.dismissLabel}",
                        modifier = Modifier.align(Alignment.Center),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_STATE = "state"
    }
}
