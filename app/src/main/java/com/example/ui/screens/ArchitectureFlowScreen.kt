package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.AltRoute
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.AgniAmber
import com.example.ui.theme.CyanSpark
import com.example.ui.theme.EmeraldSuccess
import com.example.viewmodel.PipelineNodeId
import com.example.viewmodel.PipelineTraceState

private data class FlowNodeSpec(
    val id: PipelineNodeId,
    val code: String,
    val title: String,
    val bengaliSubtitle: String,
    val icon: ImageVector,
    val isLiveInAndroid: Boolean,
    val technicalDetail: String
)

private val AgniFlowNodes: Map<PipelineNodeId, FlowNodeSpec> = mapOf(
    PipelineNodeId.USER_QUESTION to FlowNodeSpec(
        id = PipelineNodeId.USER_QUESTION,
        code = "A",
        title = "User asks a question",
        bengaliSubtitle = "ব্যবহারকারীর প্রশ্ন (টেক্সট, ভয়েস, ছবি বা PDF)",
        icon = Icons.Rounded.QuestionAnswer,
        isLiveInAndroid = true,
        technicalDetail = "Captures multilingual prompts (Bengali, Hindi, English & 9+ languages), voice transcripts, images, or PDF documents from the composer."
    ),
    PipelineNodeId.AGNI_CLIENT to FlowNodeSpec(
        id = PipelineNodeId.AGNI_CLIENT,
        code = "B",
        title = "AGNI Website / Android",
        bengaliSubtitle = "AGNI অ্যান্ড্রয়েড ও ওয়েব ক্লায়েন্ট",
        icon = Icons.Rounded.Devices,
        isLiveInAndroid = true,
        technicalDetail = "Native Jetpack Compose Android app with reactive state management, streaming UI, dark/light themes, and offline-resilient Firestore sync."
    ),
    PipelineNodeId.AUTHENTICATION to FlowNodeSpec(
        id = PipelineNodeId.AUTHENTICATION,
        code = "C",
        title = "Authentication",
        bengaliSubtitle = "Google Sign-In ও Firebase Auth যাচাই",
        icon = Icons.Rounded.VerifiedUser,
        isLiveInAndroid = true,
        technicalDetail = "Verifies user identity via Google Sign-In (Jetpack Credential Manager) and enforces per-user zero-trust access rules."
    ),
    PipelineNodeId.FASTAPI_BACKEND to FlowNodeSpec(
        id = PipelineNodeId.FASTAPI_BACKEND,
        code = "D",
        title = "FastAPI Backend",
        bengaliSubtitle = "এক্সটার্নাল API / Firebase AI গেটওয়ে",
        icon = Icons.Rounded.CloudQueue,
        isLiveInAndroid = false,
        technicalDetail = "Extensible backend orchestration tier. Currently this Android client connects directly via Firebase AI Logic; a self-hosted FastAPI gateway can be plugged into AiModelRouter without UI changes."
    ),
    PipelineNodeId.REQUEST_VALIDATION to FlowNodeSpec(
        id = PipelineNodeId.REQUEST_VALIDATION,
        code = "E",
        title = "Request Validation",
        bengaliSubtitle = "প্রম্পট, সাইজ ও অ্যাটাচমেন্ট যাচাই",
        icon = Icons.Rounded.FactCheck,
        isLiveInAndroid = true,
        technicalDetail = "Validates non-empty prompts, trims whitespace, checks 20 MB media limits, and sanitizes conversation turn history before inference."
    ),
    PipelineNodeId.MODEL_ROUTER to FlowNodeSpec(
        id = PipelineNodeId.MODEL_ROUTER,
        code = "F",
        title = "Model Router",
        bengaliSubtitle = "AiModelRouter — স্মার্ট মডেল নির্বাচন",
        icon = Icons.Rounded.AltRoute,
        isLiveInAndroid = true,
        technicalDetail = "Modular AiModelRouter interface that routes requests by selected model, assistant mode (Chat, Translate, Learn, Math, Code, Research), and memory context."
    ),
    PipelineNodeId.OPEN_SOURCE_AI to FlowNodeSpec(
        id = PipelineNodeId.OPEN_SOURCE_AI,
        code = "G",
        title = "Open Source AI",
        bengaliSubtitle = "ওপেন-সোর্স মডেল স্লট (Llama / Mistral / Sarvam)",
        icon = Icons.Rounded.Hub,
        isLiveInAndroid = false,
        technicalDetail = "Pluggable open-source LLM branch (external-opensource-peft slot in model catalog). Ready to connect to a hosted vLLM/FastAPI inference server."
    ),
    PipelineNodeId.AGNI_FINE_TUNED to FlowNodeSpec(
        id = PipelineNodeId.AGNI_FINE_TUNED,
        code = "H",
        title = "AGNI Fine Tuned Model",
        bengaliSubtitle = "AGNI ফাইন-টিউনড / লাইভ Agni ক্লাউড ইঞ্জিন",
        icon = Icons.Rounded.Tune,
        isLiveInAndroid = true,
        technicalDetail = "Active inference branch served today by Agni Flash, Agni Lite, and Agni Argon — ready to swap with custom AGNI PEFT/LoRA weights."
    ),
    PipelineNodeId.RESPONSE_PROCESSING to FlowNodeSpec(
        id = PipelineNodeId.RESPONSE_PROCESSING,
        code = "I",
        title = "Response Processing",
        bengaliSubtitle = "স্ট্রিমিং, Markdown ও ওয়েব সাইটেশন প্রসেসিং",
        icon = Icons.Rounded.Memory,
        isLiveInAndroid = true,
        technicalDetail = "Streams tokens incrementally, formats Markdown & JetBrains Mono code blocks, and extracts Google Search grounding citations."
    ),
    PipelineNodeId.SAFETY_CHECKS to FlowNodeSpec(
        id = PipelineNodeId.SAFETY_CHECKS,
        code = "J",
        title = "Safety and Quality Checks",
        bengaliSubtitle = "নিরাপত্তা, খালি উত্তর ও গুণমান যাচাই",
        icon = Icons.Rounded.Security,
        isLiveInAndroid = true,
        technicalDetail = "Applies model-level safety filters plus client-side non-empty output validation, timeout protection (60s), and transparent error reporting."
    ),
    PipelineNodeId.SAVE_CHAT_HISTORY to FlowNodeSpec(
        id = PipelineNodeId.SAVE_CHAT_HISTORY,
        code = "K",
        title = "Save Chat History",
        bengaliSubtitle = "Cloud Firestore-এ চ্যাট হিস্ট্রি ও ইউসেজ সেভ",
        icon = Icons.Rounded.History,
        isLiveInAndroid = true,
        technicalDetail = "Persists user and assistant messages, chat metadata, and usage counters securely under /users/{uid}/chats in Cloud Firestore."
    ),
    PipelineNodeId.RETURN_ANSWER to FlowNodeSpec(
        id = PipelineNodeId.RETURN_ANSWER,
        code = "L",
        title = "Return Answer to User",
        bengaliSubtitle = "ব্যবহারকারীর স্ক্রিনে তাৎক্ষণিক উত্তর প্রদর্শন",
        icon = Icons.Rounded.LocalFireDepartment,
        isLiveInAndroid = true,
        technicalDetail = "Renders the final formatted answer in the user's preferred language with copyable code blocks, source chips, and follow-up context."
    )
)

@Composable
fun ArchitectureFlowScreen(
    pipelineTrace: PipelineTraceState,
    isCustomEndpointLive: Boolean = false,
    onBack: () -> Unit,
    onTryInChat: () -> Unit
) {
    BackHandler { onBack() }

    var selectedNode by remember { mutableStateOf(PipelineNodeId.MODEL_ROUTER) }
    val resolvedNodes = remember(isCustomEndpointLive) {
        AgniFlowNodes.mapValues { (id, spec) ->
            if (isCustomEndpointLive && (id == PipelineNodeId.FASTAPI_BACKEND || id == PipelineNodeId.OPEN_SOURCE_AI)) {
                spec.copy(isLiveInAndroid = true)
            } else {
                spec
            }
        }
    }
    val selectedSpec = resolvedNodes.getValue(selectedNode)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .navigationBarsPadding()
        ) {
            // Top Bar
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("architecture_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_architecture_back)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.architecture_title),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = stringResource(R.string.architecture_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AccountTree,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "12 Steps",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Live Last-Request Pipeline Telemetry Card
            item {
                PipelineTelemetryBanner(
                    trace = pipelineTrace,
                    onTryInChat = onTryInChat
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Legend Row
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
                    LegendPill(color = EmeraldSuccess, label = "Live in App")
                    LegendPill(color = AgniAmber, label = "External / Pluggable")
                    LegendPill(color = CyanSpark, label = "Last Run Trace")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Flowchart TD: A -> B -> C -> D -> E -> F
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val topChain = listOf(
                        PipelineNodeId.USER_QUESTION,
                        PipelineNodeId.AGNI_CLIENT,
                        PipelineNodeId.AUTHENTICATION,
                        PipelineNodeId.FASTAPI_BACKEND,
                        PipelineNodeId.REQUEST_VALIDATION,
                        PipelineNodeId.MODEL_ROUTER
                    )

                    topChain.forEachIndexed { index, nodeId ->
                        val spec = resolvedNodes.getValue(nodeId)
                        FlowNodeCard(
                            spec = spec,
                            selected = selectedNode == nodeId,
                            executed = nodeId in pipelineTrace.completedNodes,
                            active = pipelineTrace.activeNode == nodeId,
                            onClick = { selectedNode = nodeId },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (index < topChain.lastIndex) {
                            VerticalArrowConnector(
                                highlighted = nodeId in pipelineTrace.completedNodes
                            )
                        }
                    }

                    // Split Connector: F -> G & F -> H
                    SplitArrowConnector(
                        leftHighlighted = PipelineNodeId.OPEN_SOURCE_AI in pipelineTrace.completedNodes,
                        rightHighlighted = PipelineNodeId.AGNI_FINE_TUNED in pipelineTrace.completedNodes
                    )

                    // Parallel Model Branches: G (Open Source AI) | H (AGNI Fine Tuned Model)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val openSourceSpec = resolvedNodes.getValue(PipelineNodeId.OPEN_SOURCE_AI)
                        val fineTunedSpec = resolvedNodes.getValue(PipelineNodeId.AGNI_FINE_TUNED)

                        BranchNodeCard(
                            spec = openSourceSpec,
                            selected = selectedNode == PipelineNodeId.OPEN_SOURCE_AI,
                            executed = PipelineNodeId.OPEN_SOURCE_AI in pipelineTrace.completedNodes,
                            active = pipelineTrace.activeNode == PipelineNodeId.OPEN_SOURCE_AI,
                            onClick = { selectedNode = PipelineNodeId.OPEN_SOURCE_AI },
                            modifier = Modifier.weight(1f)
                        )
                        BranchNodeCard(
                            spec = fineTunedSpec,
                            selected = selectedNode == PipelineNodeId.AGNI_FINE_TUNED,
                            executed = PipelineNodeId.AGNI_FINE_TUNED in pipelineTrace.completedNodes,
                            active = pipelineTrace.activeNode == PipelineNodeId.AGNI_FINE_TUNED,
                            onClick = { selectedNode = PipelineNodeId.AGNI_FINE_TUNED },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Merge Connector: G -> I & H -> I
                    MergeArrowConnector(
                        leftHighlighted = PipelineNodeId.OPEN_SOURCE_AI in pipelineTrace.completedNodes,
                        rightHighlighted = PipelineNodeId.AGNI_FINE_TUNED in pipelineTrace.completedNodes
                    )

                    // Bottom Chain: I -> J -> K -> L
                    val bottomChain = listOf(
                        PipelineNodeId.RESPONSE_PROCESSING,
                        PipelineNodeId.SAFETY_CHECKS,
                        PipelineNodeId.SAVE_CHAT_HISTORY,
                        PipelineNodeId.RETURN_ANSWER
                    )
                    bottomChain.forEachIndexed { index, nodeId ->
                        val spec = resolvedNodes.getValue(nodeId)
                        FlowNodeCard(
                            spec = spec,
                            selected = selectedNode == nodeId,
                            executed = nodeId in pipelineTrace.completedNodes,
                            active = pipelineTrace.activeNode == nodeId,
                            onClick = { selectedNode = nodeId },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (index < bottomChain.lastIndex) {
                            VerticalArrowConnector(
                                highlighted = nodeId in pipelineTrace.completedNodes
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Interactive Selected Node Inspector Card
            item {
                AnimatedVisibility(visible = true) {
                    SelectedNodeInspectorCard(
                        spec = selectedSpec,
                        executed = selectedSpec.id in pipelineTrace.completedNodes
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun PipelineTelemetryBanner(
    trace: PipelineTraceState,
    onTryInChat: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (trace.completedNodes.isEmpty()) {
                            "AGNI AI Request Pipeline"
                        } else {
                            "Last Executed Request Trace"
                        },
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (trace.completedNodes.isEmpty()) {
                            "Tap any block below to inspect how AGNI AI processes questions end-to-end."
                        } else {
                            val latencyLabel = trace.lastLatencyMs?.let { "${it} ms" } ?: "In progress"
                            "Model: ${trace.lastModelName ?: "Agni Flash"} · Latency: $latencyLabel · ${trace.completedNodes.size} stages verified"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    onClick = onTryInChat,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("architecture_try_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ask AI", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun LegendPill(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = color,
            modifier = Modifier.size(8.dp)
        ) {}
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FlowNodeCard(
    spec: FlowNodeSpec,
    selected: Boolean,
    executed: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            active -> MaterialTheme.colorScheme.primary
            selected -> MaterialTheme.colorScheme.primary
            executed -> CyanSpark.copy(alpha = 0.85f)
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        label = "node_border_${spec.code}"
    )

    val containerColor = when {
        selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        executed -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainer
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(if (selected || active) 1.5.dp else 1.dp, borderColor),
        modifier = modifier.testTag("flow_node_${spec.code.lowercase()}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // Step Badge (A..L)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (spec.isLiveInAndroid) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                } else {
                    AgniAmber.copy(alpha = 0.18f)
                },
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = spec.icon,
                        contentDescription = null,
                        tint = if (spec.isLiveInAndroid) MaterialTheme.colorScheme.primary else AgniAmber,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${spec.code}. ${spec.title}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (executed) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Executed in last request",
                            tint = CyanSpark,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = spec.bengaliSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            StatusChip(isLive = spec.isLiveInAndroid)
        }
    }
}

@Composable
private fun BranchNodeCard(
    spec: FlowNodeSpec,
    selected: Boolean,
    executed: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            active || selected -> MaterialTheme.colorScheme.primary
            executed -> CyanSpark.copy(alpha = 0.85f)
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        label = "branch_border_${spec.code}"
    )

    val containerColor = when {
        selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        executed -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainer
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(if (selected || active) 1.5.dp else 1.dp, borderColor),
        modifier = modifier.testTag("flow_node_${spec.code.lowercase()}")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (spec.isLiveInAndroid) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                } else {
                    AgniAmber.copy(alpha = 0.18f)
                },
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = spec.icon,
                        contentDescription = null,
                        tint = if (spec.isLiveInAndroid) MaterialTheme.colorScheme.primary else AgniAmber,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${spec.code}. ${spec.title}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = spec.bengaliSubtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            StatusChip(isLive = spec.isLiveInAndroid)
        }
    }
}

@Composable
private fun StatusChip(isLive: Boolean) {
    val tint = if (isLive) EmeraldSuccess else AgniAmber
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = tint.copy(alpha = 0.16f)
    ) {
        Text(
            text = if (isLive) "LIVE" else "PLUG-IN",
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun VerticalArrowConnector(highlighted: Boolean) {
    val arrowColor = if (highlighted) CyanSpark else MaterialTheme.colorScheme.outline
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
    ) {
        val centerX = size.width / 2f
        val topY = 0f
        val tipY = size.height - 2.dp.toPx()
        val strokePx = 2.dp.toPx()
        val headSize = 5.dp.toPx()

        drawLine(
            color = arrowColor,
            start = Offset(centerX, topY),
            end = Offset(centerX, tipY - 2.dp.toPx()),
            strokeWidth = strokePx,
            cap = StrokeCap.Round
        )

        val headPath = Path().apply {
            moveTo(centerX, tipY)
            lineTo(centerX - headSize, tipY - headSize * 1.25f)
            lineTo(centerX + headSize, tipY - headSize * 1.25f)
            close()
        }
        drawPath(path = headPath, color = arrowColor)
    }
}

@Composable
private fun SplitArrowConnector(
    leftHighlighted: Boolean,
    rightHighlighted: Boolean
) {
    val defaultColor = MaterialTheme.colorScheme.outline
    val leftColor = if (leftHighlighted) CyanSpark else defaultColor
    val rightColor = if (rightHighlighted) CyanSpark else defaultColor

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
    ) {
        val centerX = size.width / 2f
        val leftX = size.width * 0.25f
        val rightX = size.width * 0.75f
        val midY = size.height * 0.45f
        val tipY = size.height - 2.dp.toPx()
        val strokePx = 2.dp.toPx()
        val headSize = 5.dp.toPx()

        // Stem from Model Router (F)
        drawLine(
            color = if (leftHighlighted || rightHighlighted) CyanSpark else defaultColor,
            start = Offset(centerX, 0f),
            end = Offset(centerX, midY),
            strokeWidth = strokePx,
            cap = StrokeCap.Round
        )

        // Left branch to Open Source AI (G)
        val leftPath = Path().apply {
            moveTo(centerX, midY)
            lineTo(leftX, midY)
            lineTo(leftX, tipY - 2.dp.toPx())
        }
        drawPath(path = leftPath, color = leftColor, style = Stroke(width = strokePx, cap = StrokeCap.Round))
        drawPath(
            path = Path().apply {
                moveTo(leftX, tipY)
                lineTo(leftX - headSize, tipY - headSize * 1.25f)
                lineTo(leftX + headSize, tipY - headSize * 1.25f)
                close()
            },
            color = leftColor
        )

        // Right branch to AGNI Fine Tuned Model (H)
        val rightPath = Path().apply {
            moveTo(centerX, midY)
            lineTo(rightX, midY)
            lineTo(rightX, tipY - 2.dp.toPx())
        }
        drawPath(path = rightPath, color = rightColor, style = Stroke(width = strokePx, cap = StrokeCap.Round))
        drawPath(
            path = Path().apply {
                moveTo(rightX, tipY)
                lineTo(rightX - headSize, tipY - headSize * 1.25f)
                lineTo(rightX + headSize, tipY - headSize * 1.25f)
                close()
            },
            color = rightColor
        )
    }
}

@Composable
private fun MergeArrowConnector(
    leftHighlighted: Boolean,
    rightHighlighted: Boolean
) {
    val defaultColor = MaterialTheme.colorScheme.outline
    val leftColor = if (leftHighlighted) CyanSpark else defaultColor
    val rightColor = if (rightHighlighted) CyanSpark else defaultColor
    val mergedColor = if (leftHighlighted || rightHighlighted) CyanSpark else defaultColor

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
    ) {
        val centerX = size.width / 2f
        val leftX = size.width * 0.25f
        val rightX = size.width * 0.75f
        val midY = size.height * 0.45f
        val tipY = size.height - 2.dp.toPx()
        val strokePx = 2.dp.toPx()
        val headSize = 5.dp.toPx()

        // Left incoming from G
        val leftPath = Path().apply {
            moveTo(leftX, 0f)
            lineTo(leftX, midY)
            lineTo(centerX, midY)
        }
        drawPath(path = leftPath, color = leftColor, style = Stroke(width = strokePx, cap = StrokeCap.Round))

        // Right incoming from H
        val rightPath = Path().apply {
            moveTo(rightX, 0f)
            lineTo(rightX, midY)
            lineTo(centerX, midY)
        }
        drawPath(path = rightPath, color = rightColor, style = Stroke(width = strokePx, cap = StrokeCap.Round))

        // Merged arrow into Response Processing (I)
        drawLine(
            color = mergedColor,
            start = Offset(centerX, midY),
            end = Offset(centerX, tipY - 2.dp.toPx()),
            strokeWidth = strokePx,
            cap = StrokeCap.Round
        )
        drawPath(
            path = Path().apply {
                moveTo(centerX, tipY)
                lineTo(centerX - headSize, tipY - headSize * 1.25f)
                lineTo(centerX + headSize, tipY - headSize * 1.25f)
                close()
            },
            color = mergedColor
        )
    }
}

@Composable
private fun SelectedNodeInspectorCard(
    spec: FlowNodeSpec,
    executed: Boolean
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("architecture_node_inspector")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = spec.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Stage ${spec.code}: ${spec.title}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                StatusChip(isLive = spec.isLiveInAndroid)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = spec.bengaliSubtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = spec.technicalDetail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (executed) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = CyanSpark,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Verified during your latest chat request",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanSpark
                    )
                }
            }
        }
    }
}
