package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Rotate90DegreesCw
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VideoCall
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.media.AgniMediaProcessor
import com.example.media.AgniPhotoFilter
import com.example.media.PhotoEditParams
import com.example.media.SearchedImageResult
import com.example.ui.theme.AgniAmber
import com.example.ui.theme.EmeraldSuccess
import com.example.viewmodel.MediaStudioState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaStudioScreen(
    state: MediaStudioState,
    onBack: () -> Unit,
    onSearchImages: (String) -> Unit,
    onSelectSearchResultForEdit: (SearchedImageResult) -> Unit,
    onDownloadSearchResult: (SearchedImageResult) -> Unit,
    onAddSearchResultToVideo: (SearchedImageResult) -> Unit,
    onSendBitmapToChat: (Bitmap, String) -> Unit,
    onSendSearchResultToChat: (SearchedImageResult) -> Unit,
    onGenerateAiImage: (String) -> Unit,
    onAiEditCurrentImage: (String) -> Unit,
    onSetEditorBitmap: (Bitmap, String) -> Unit,
    onSaveEditedBitmap: (Bitmap) -> Unit,
    onAddBitmapToVideoSlides: (Bitmap) -> Unit,
    onRemoveVideoSlide: (Int) -> Unit,
    onClearVideoSlides: () -> Unit,
    onExportSlideshowVideo: (Int, String) -> Unit,
    onTrimVideo: (Uri, Long, Long) -> Unit,
    onClearStudioMessage: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }

    // Automatically switch to Photo Studio tab if a bitmap was just loaded from search or frame extract
    var lastBitmapIdentity by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(state.editorSourceBitmap) {
        val currentHash = state.editorSourceBitmap?.let { System.identityHashCode(it) }
        if (currentHash != null && currentHash != lastBitmapIdentity && selectedTab == 0) {
            selectedTab = 1
        }
        lastBitmapIdentity = currentHash
    }

    // Trigger default curated discovery search once if empty
    LaunchedEffect(Unit) {
        if (state.searchResults.isEmpty() && !state.isSearching && state.searchError == null) {
            onSearchImages("Nature landscape")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Header with Agni AI Emblem
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("media_studio_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_architecture_back)
                        )
                    }
                    Image(
                        painter = painterResource(id = R.drawable.ic_agni_logo_fg_1795283955599),
                        contentDescription = stringResource(R.string.cd_agni_emblem),
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.media_studio_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.media_studio_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.ImageSearch,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        text = { Text("Find Photos", maxLines = 1) },
                        modifier = Modifier.testTag("studio_tab_find")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Brush,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        text = { Text("Photo Studio", maxLines = 1) },
                        modifier = Modifier.testTag("studio_tab_photo")
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.MovieCreation,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        text = { Text("Video Studio", maxLines = 1) },
                        modifier = Modifier.testTag("studio_tab_video")
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        // Studio Status / Error Banner
        AnimatedVisibility(visible = state.statusMessage != null || state.errorMessage != null) {
            val isErr = state.errorMessage != null
            val bannerText = state.errorMessage ?: state.statusMessage.orEmpty()
            val accent = if (isErr) MaterialTheme.colorScheme.error else EmeraldSuccess
            Surface(
                color = accent.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = if (isErr) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = bannerText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onClearStudioMessage, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Dismiss message",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            when (selectedTab) {
                0 -> ImageFinderTab(
                    state = state,
                    onSearch = onSearchImages,
                    onEditResult = onSelectSearchResultForEdit,
                    onDownloadResult = onDownloadSearchResult,
                    onAddResultToVideo = {
                        onAddSearchResultToVideo(it)
                    },
                    onAskAiWithResult = onSendSearchResultToChat
                )
                1 -> PhotoStudioTab(
                    state = state,
                    onGenerateAiImage = onGenerateAiImage,
                    onAiEditCurrentImage = onAiEditCurrentImage,
                    onSetEditorBitmap = onSetEditorBitmap,
                    onPickLocalBitmap = { uri ->
                        scope.launch {
                            AgniMediaProcessor.loadBitmapFromUri(context, uri)
                                .onSuccess { bmp -> onSetEditorBitmap(bmp, "Gallery Photo") }
                                .onFailure { /* handled via editor banner */ }
                        }
                    },
                    onSaveEditedBitmap = onSaveEditedBitmap,
                    onAddToVideo = { bmp ->
                        onAddBitmapToVideoSlides(bmp)
                        selectedTab = 2
                    },
                    onAskAi = onSendBitmapToChat
                )
                2 -> VideoStudioTab(
                    videoSlides = state.videoSlides,
                    isExporting = state.isVideoExporting,
                    exportProgress = state.videoExportProgress,
                    lastExportedVideoUri = state.lastExportedVideoUri,
                    onAddSlideBitmaps = { list -> list.forEach { onAddBitmapToVideoSlides(it) } },
                    onRemoveSlide = onRemoveVideoSlide,
                    onClearSlides = onClearVideoSlides,
                    onExportSlideshow = onExportSlideshowVideo,
                    onTrimVideo = onTrimVideo,
                    onExtractFrameToPhotoEditor = { bmp ->
                        onSetEditorBitmap(bmp, "Video Frame")
                        selectedTab = 1
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageFinderTab(
    state: MediaStudioState,
    onSearch: (String) -> Unit,
    onEditResult: (SearchedImageResult) -> Unit,
    onDownloadResult: (SearchedImageResult) -> Unit,
    onAddResultToVideo: (SearchedImageResult) -> Unit,
    onAskAiWithResult: (SearchedImageResult) -> Unit
) {
    var query by remember(state.searchQuery) { mutableStateOf(state.searchQuery) }
    var previewItem by remember { mutableStateOf<SearchedImageResult?>(null) }

    val quickTopics = remember {
        listOf("Nature", "Kolkata", "Flowers", "Sunset", "Animals", "Space", "Mountains", "Art")
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 780.dp)
            .navigationBarsPadding()
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search any photo (e.g. Tiger, Kolkata, Sunset)...") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        if (query.isNotBlank()) onSearch(query)
                    }),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("image_search_input")
                )
                Button(
                    onClick = { if (query.isNotBlank()) onSearch(query) },
                    enabled = query.isNotBlank() && !state.isSearching,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    modifier = Modifier.testTag("image_search_button")
                ) {
                    Text("Find")
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(quickTopics) { topic ->
                    FilterChip(
                        selected = state.searchQuery.equals(topic, ignoreCase = true),
                        onClick = {
                            query = topic
                            onSearch(topic)
                        },
                        label = { Text(topic) }
                    )
                }
            }
        }

        if (state.isSearching) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Searching free public photos…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else if (state.searchError != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = state.searchError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else if (state.searchResults.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No images matched that keyword. Try another English search term like 'Sunset', 'River', or 'Flower'.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            val rows = state.searchResults.chunked(2)
            items(rows) { pair ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    pair.forEach { item ->
                        Surface(
                            onClick = { previewItem = item },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column {
                                AsyncImage(
                                    model = item.thumbUrl,
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1.15f)
                                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                )
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.license,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    previewItem?.let { item ->
        ModalBottomSheet(onDismissRequest = { previewItem = null }) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .navigationBarsPadding()
            ) {
                AsyncImage(
                    model = item.thumbUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                )
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.author} · ${item.license}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            onEditResult(item)
                            previewItem = null
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("search_preview_edit_button")
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Edit")
                    }
                    OutlinedButton(
                        onClick = {
                            onDownloadResult(item)
                            previewItem = null
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("search_preview_save_button")
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save")
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            onAddResultToVideo(item)
                            previewItem = null
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.VideoCall, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add to Video")
                    }
                    OutlinedButton(
                        onClick = {
                            onAskAiWithResult(item)
                            previewItem = null
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ask AI")
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun PhotoStudioTab(
    state: MediaStudioState,
    onGenerateAiImage: (String) -> Unit,
    onAiEditCurrentImage: (String) -> Unit,
    onSetEditorBitmap: (Bitmap, String) -> Unit,
    onPickLocalBitmap: (Uri) -> Unit,
    onSaveEditedBitmap: (Bitmap) -> Unit,
    onAddToVideo: (Bitmap) -> Unit,
    onAskAi: (Bitmap, String) -> Unit
) {
    var aiPrompt by remember { mutableStateOf("") }
    var aiEditPrompt by remember { mutableStateOf("") }
    var editParams by remember(state.editorSourceBitmap) { mutableStateOf(PhotoEditParams()) }
    var previewBitmap by remember(state.editorSourceBitmap) { mutableStateOf(state.editorSourceBitmap) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onPickLocalBitmap(uri)
    }

    LaunchedEffect(state.editorSourceBitmap, editParams) {
        val src = state.editorSourceBitmap
        previewBitmap = if (src == null) {
            null
        } else {
            withContext(Dispatchers.Default) {
                AgniMediaProcessor.applyPhotoEdits(src, editParams)
            }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 780.dp)
            .navigationBarsPadding()
    ) {
        // AI Image Generator Card
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_agni_flame),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Create Image with Agni AI",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    OutlinedTextField(
                        value = aiPrompt,
                        onValueChange = { aiPrompt = it },
                        placeholder = { Text("Describe any image (e.g., Sunset over Howrah Bridge in watercolor)...") },
                        maxLines = 3,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_image_prompt_input")
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { onGenerateAiImage(aiPrompt) },
                            enabled = aiPrompt.isNotBlank() && !state.isAiGenerating,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("ai_generate_image_button")
                        ) {
                            if (state.isAiGenerating) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Creating…")
                            } else {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_agni_flame),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Generate AI Photo")
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("photo_editor_pick_button")
                        ) {
                            Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pick Photo")
                        }
                    }
                }
            }
        }

        // Active Photo Preview + Instant On-Device & AI Editor
        val activeBmp = previewBitmap
        if (activeBmp == null) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Brush,
                            contentDescription = null,
                            tint = AgniAmber,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Pick a photo from your phone, search in 'Find Photos', or generate a new one above to start editing.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Image(
                            bitmap = activeBmp.asImageBitmap(),
                            contentDescription = state.editorSourceTitle,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(270.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                        )

                        // Quick Transform Row: Rotate, Flip, Reset
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = {
                                    editParams = editParams.copy(
                                        rotationDegrees = (editParams.rotationDegrees + 90) % 360
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Rotate90DegreesCw, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Rotate", style = MaterialTheme.typography.labelMedium)
                            }
                            OutlinedButton(
                                onClick = {
                                    editParams = editParams.copy(flipHorizontal = !editParams.flipHorizontal)
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Flip, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Flip", style = MaterialTheme.typography.labelMedium)
                            }
                            OutlinedButton(
                                onClick = { editParams = PhotoEditParams() },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        // Filter Chips
                        Text("Color Filters", style = MaterialTheme.typography.labelMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(AgniPhotoFilter.entries) { filter ->
                                FilterChip(
                                    selected = editParams.filter == filter,
                                    onClick = { editParams = editParams.copy(filter = filter) },
                                    label = { Text(filter.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }

                        // Sliders: Brightness, Contrast, Saturation
                        EditorSliderRow(
                            label = "Brightness",
                            value = editParams.brightness,
                            range = -0.35f..0.35f,
                            onValueChange = { editParams = editParams.copy(brightness = it) }
                        )
                        EditorSliderRow(
                            label = "Contrast",
                            value = editParams.contrast,
                            range = 0.6f..1.6f,
                            onValueChange = { editParams = editParams.copy(contrast = it) }
                        )
                        EditorSliderRow(
                            label = "Saturation",
                            value = editParams.saturation,
                            range = 0f..1.8f,
                            onValueChange = { editParams = editParams.copy(saturation = it) }
                        )

                        // Text Caption Overlay
                        OutlinedTextField(
                            value = editParams.overlayText,
                            onValueChange = { if (it.length <= 40) editParams = editParams.copy(overlayText = it) },
                            label = { Text("Add Text Caption on Photo (Optional)") },
                            placeholder = { Text("e.g., শুভ সকাল · Agni AI") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Save / Add to Video / Ask AI Row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { onSaveEditedBitmap(activeBmp) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .testTag("photo_editor_save_button")
                            ) {
                                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save Photo")
                            }
                            OutlinedButton(
                                onClick = { onAddToVideo(activeBmp) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.VideoCall, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("To Video")
                            }
                            OutlinedButton(
                                onClick = { onAskAi(activeBmp, state.editorSourceTitle) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(0.9f)
                            ) {
                                Text("Ask AI")
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // AI Photo Edit Instruction
                        Text("AI Smart Photo Edit", style = MaterialTheme.typography.labelMedium)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = aiEditPrompt,
                                onValueChange = { aiEditPrompt = it },
                                placeholder = { Text("e.g., Turn sky into golden sunset...") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("ai_edit_prompt_input")
                            )
                            Button(
                                onClick = {
                                    onSetEditorBitmap(activeBmp, state.editorSourceTitle)
                                    onAiEditCurrentImage(aiEditPrompt)
                                },
                                enabled = aiEditPrompt.isNotBlank() && !state.isAiGenerating,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("ai_edit_apply_button")
                            ) {
                                Text("AI Edit")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall)
            Text(
                text = String.format("%.2f", value),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range
        )
    }
}
