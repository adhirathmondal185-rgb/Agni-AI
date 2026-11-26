package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.attemptAutoSignIn
import com.example.auth.onGoogleSignInClicked
import com.example.ui.theme.AgniAmber
import com.example.ui.theme.AgniFlame
import com.example.ui.theme.NavyAbyss
import com.example.ui.theme.NavyVoid

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthScreen(
    autoLaunchPicker: Boolean = false,
    onPickerConsumed: () -> Unit = {},
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(autoLaunchPicker) {
        if (autoLaunchPicker) {
            onPickerConsumed()
            errorMessage = null
            isLoading = true
            onGoogleSignInClicked(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                    isLoading = false
                    onAuthSuccess()
                },
                onAuthError = { msg ->
                    isLoading = false
                    errorMessage = msg
                },
                scope = scope,
                onAuthCancelled = {
                    isLoading = false
                }
            )
        } else {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = onAuthSuccess,
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(NavyVoid, NavyAbyss, Color(0xFF0E1D34))
                )
            )
    ) {
        // Atmospheric top hero artwork
        Image(
            painter = painterResource(id = R.drawable.img_agni_hero_banner_1795283950260),
            contentDescription = stringResource(R.string.cd_hero_banner),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(270.dp)
                .align(Alignment.TopCenter)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            NavyAbyss.copy(alpha = 0.72f),
                            NavyAbyss
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .widthIn(max = 560.dp)
                .align(Alignment.Center),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Spacer(modifier = Modifier.height(52.dp))

                // Emblem + Brand Badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.5.dp, AgniFlame.copy(alpha = 0.7f)),
                        color = NavyAbyss,
                        shadowElevation = 10.dp
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_agni_logo_fg_1795283955599),
                            contentDescription = stringResource(R.string.cd_agni_emblem),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(62.dp)
                                .clip(RoundedCornerShape(18.dp))
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = AgniFlame.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, AgniFlame.copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = "MULTILINGUAL AI PLATFORM",
                                style = MaterialTheme.typography.labelSmall,
                                color = AgniAmber,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "AGNI AI",
                            style = MaterialTheme.typography.displayMedium,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.headlineMedium,
                    color = AgniAmber
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Converse across Bengali, Hindi, English, and 9+ Indian languages with live multimodal vision, PDF intelligence, voice input, math, coding, and web-grounded research.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFB7C7DF)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Multilingual script ribbon
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("বাংলা", "हिन्दी", "English", "தமிழ்", "తెలుగు", "मराठी", "ગુજરાતી").forEach { script ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF152947),
                            border = BorderStroke(1.dp, Color(0xFF28436B))
                        ) {
                            Text(
                                text = script,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFE5EEF9),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Capability highlights
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFF10203A).copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, Color(0xFF243B5E)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CapabilityRow(Icons.Rounded.LocalFireDepartment, "Streaming AI Chat & Model Router", "Switch between Agni Flash, Lite, Argon & Custom AI.")
                        CapabilityRow(Icons.Rounded.DocumentScanner, "Image Finder, Photo & Video Studio", "Find photos, create/edit images & make MP4 videos.")
                        CapabilityRow(Icons.Rounded.Mic, "Multilingual Voice & Translation", "Speak naturally across Indian languages.")
                        CapabilityRow(Icons.Rounded.Public, "Live Web Research & Coding", "Grounded citations, math proofs & clean code.")
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedVisibility(visible = errorMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF471521),
                        border = BorderStroke(1.dp, Color(0xFFFF5A6E).copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                    ) {
                        Text(
                            text = errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFD6DC),
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                Button(
                    onClick = {
                        errorMessage = null
                        isLoading = true
                        onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isLoading = false
                                onAuthSuccess()
                            },
                            onAuthError = { msg ->
                                isLoading = false
                                errorMessage = msg
                            },
                            scope = scope,
                            onAuthCancelled = {
                                isLoading = false
                            }
                        )
                    },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AgniFlame,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(vertical = 16.dp, horizontal = 24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("google_sign_in_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Signing in…",
                            style = MaterialTheme.typography.labelLarge
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.sign_in_with_google),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Pick any Google account from your device — your Agni AI account is created automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFD0DCF0)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "যেকোনো একটি Google অ্যাকাউন্ট সিলেক্ট করলেই সাথে সাথে আপনার অ্যাকাউন্ট তৈরি হয়ে যাবে।",
                    style = MaterialTheme.typography.bodySmall,
                    color = AgniAmber
                )

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2ECC8A))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Secured by Firebase Auth, App Check & private user isolation",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8CA1C0)
                    )
                }
            }
        }
    }
}

@Composable
private fun CapabilityRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = AgniFlame.copy(alpha = 0.14f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AgniAmber,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9EB0CC)
            )
        }
    }
}
