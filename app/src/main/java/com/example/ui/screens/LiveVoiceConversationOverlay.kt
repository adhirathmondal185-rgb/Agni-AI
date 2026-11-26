package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Face3
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AgniLanguage
import com.example.ui.theme.AgniAmber
import com.example.ui.theme.AgniFlame
import com.example.ui.theme.CyanSpark
import com.example.ui.theme.NavyAbyss
import com.example.ui.theme.NavyVoid
import com.example.voice.AssistantVoiceGender

@Composable
fun LiveVoiceConversationOverlay(
    language: AgniLanguage,
    voiceGender: AssistantVoiceGender,
    isListening: Boolean,
    isThinking: Boolean,
    isSpeaking: Boolean,
    liveUserTranscript: String,
    lastAssistantReply: String,
    statusHint: String?,
    onSelectVoiceGender: (AssistantVoiceGender) -> Unit,
    onTapMicOrInterrupt: () -> Unit,
    onStopSpeaking: () -> Unit,
    onEndConversation: () -> Unit
) {
    BackHandler { onEndConversation() }

    val infiniteTransition = rememberInfiniteTransition(label = "voice_orb")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = if (isListening || isSpeaking) 1.16f else 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isSpeaking) 680 else 1050,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val orbAccent = when {
        isListening -> CyanSpark
        isSpeaking -> AgniFlame
        isThinking -> AgniAmber
        else -> AgniFlame.copy(alpha = 0.75f)
    }

    val stageLabel = when {
        isListening -> "শুনছি… এখন কথা বলুন (${language.nativeName})"
        isThinking -> "Agni AI উত্তর ভাবছে…"
        isSpeaking -> "${voiceGender.bengaliLabel} অ্যাসিস্ট্যান্ট উত্তর বলছে…"
        else -> "কথা বলতে মাইক বাটনে চাপ দিন"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(NavyVoid, NavyAbyss, Color(0xFF0A1426))
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Top Bar: Brand + Live Male / Female Voice Switcher
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_agni_logo_fg_1795283955599),
                        contentDescription = stringResource(R.string.cd_agni_emblem),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.live_voice_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LiveVoiceGenderButton(
                        icon = Icons.Rounded.Face3,
                        title = "মেয়ে অ্যাসিস্ট্যান্ট",
                        subtitle = "Female",
                        selected = voiceGender == AssistantVoiceGender.FEMALE,
                        testTag = "live_voice_female_button",
                        onClick = { onSelectVoiceGender(AssistantVoiceGender.FEMALE) },
                        modifier = Modifier.weight(1f)
                    )
                    LiveVoiceGenderButton(
                        icon = Icons.Rounded.Face,
                        title = "ছেলে অ্যাসিস্ট্যান্ট",
                        subtitle = "Male",
                        selected = voiceGender == AssistantVoiceGender.MALE,
                        testTag = "live_voice_male_button",
                        onClick = { onSelectVoiceGender(AssistantVoiceGender.MALE) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Center: Animated Voice Orb + Live Transcript & Spoken Reply
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                // Pulsing Agni Voice Orb
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(184.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(172.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        orbAccent.copy(alpha = 0.42f),
                                        orbAccent.copy(alpha = 0.14f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    Surface(
                        onClick = onTapMicOrInterrupt,
                        shape = CircleShape,
                        color = NavyAbyss,
                        border = BorderStroke(2.5.dp, orbAccent),
                        shadowElevation = 14.dp,
                        modifier = Modifier
                            .size(116.dp)
                            .testTag("live_voice_center_orb")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isThinking) {
                                CircularProgressIndicator(
                                    color = AgniAmber,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(44.dp)
                                )
                            } else {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_agni_logo_fg_1795283955599),
                                    contentDescription = stringResource(R.string.cd_agni_emblem),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(50),
                    color = orbAccent.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, orbAccent.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = stageLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                    )
                }

                if (!statusHint.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = statusHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = AgniAmber,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Live Conversation Transcript & AI Reply Card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF10203A).copy(alpha = 0.9f),
                    border = BorderStroke(1.dp, Color(0xFF243B5E)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        if (liveUserTranscript.isNotBlank()) {
                            Text(
                                text = "আপনি: $liveUserTranscript",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CyanSpark
                            )
                        }
                        if (lastAssistantReply.isNotBlank()) {
                            Text(
                                text = lastAssistantReply,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFE8F0FC)
                            )
                        } else if (liveUserTranscript.isBlank()) {
                            Text(
                                text = "আপনি যা বলবেন, Agni AI সাথে সাথে উত্তর দেবে এবং উত্তর বলা শেষ হলেই আবার আপনার পরের কথা শুনবে।",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFA7BAD6),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Bottom Call-Style Controls: Stop Speech | Main Mic / Interrupt | End Call
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                // Mute / Stop AI Speaking
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        onClick = onStopSpeaking,
                        enabled = isSpeaking,
                        shape = CircleShape,
                        color = if (isSpeaking) Color(0xFF1D3354) else Color(0xFF132238),
                        border = BorderStroke(
                            1.dp,
                            if (isSpeaking) AgniAmber else Color(0xFF243B5E)
                        ),
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("live_voice_stop_speaking_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Stop,
                                contentDescription = stringResource(R.string.cd_stop_speaking),
                                tint = if (isSpeaking) AgniAmber else Color(0xFF607491),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "থামান",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFB7C7DF)
                    )
                }

                // Main Talk / Interrupt Mic Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        onClick = onTapMicOrInterrupt,
                        shape = CircleShape,
                        color = if (isListening) CyanSpark else AgniFlame,
                        shadowElevation = 10.dp,
                        modifier = Modifier
                            .size(72.dp)
                            .testTag("live_voice_mic_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isListening) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                                contentDescription = stringResource(R.string.cd_voice_input),
                                tint = if (isListening) NavyVoid else Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isListening) "শুনছে" else "কথা বলুন",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // End Voice Conversation
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        onClick = onEndConversation,
                        shape = CircleShape,
                        color = Color(0xFFE53935),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("live_voice_end_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.CallEnd,
                                contentDescription = stringResource(R.string.cd_end_voice_call),
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "শেষ করুন",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFFB4B4)
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveVoiceGenderButton(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) AgniFlame.copy(alpha = 0.22f) else Color(0xFF132238),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) AgniFlame else Color(0xFF28436B)
        ),
        modifier = modifier.testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) AgniAmber else Color(0xFF9EB0CC),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) AgniAmber else Color(0xFF8CA1C0)
                )
            }
        }
    }
}
