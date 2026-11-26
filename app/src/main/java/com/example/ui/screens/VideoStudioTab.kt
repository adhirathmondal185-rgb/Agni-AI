package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.media.AgniMediaProcessor
import com.example.ui.theme.EmeraldSuccess
import kotlinx.coroutines.launch

@Composable
fun VideoStudioTab(
    videoSlides: List<Bitmap>,
    isExporting: Boolean,
    exportProgress: Float,
    lastExportedVideoUri: Uri?,
    onAddSlideBitmaps: (List<Bitmap>) -> Unit,
    onRemoveSlide: (Int) -> Unit,
    onClearSlides: () -> Unit,
    onExportSlideshow: (Int, String) -> Unit,
    onTrimVideo: (Uri, Long, Long) -> Unit,
    onExtractFrameToPhotoEditor: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var secondsPerSlide by remember { mutableIntStateOf(3) }
    var videoTitleText by remember { mutableStateOf("") }

    // Video Trimmer state
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var videoDurationMs by remember { mutableLongStateOf(0L) }
    var trimRange by remember { mutableStateOf(0f..1f) }
    var frameExtractError by remember { mutableStateOf<String?>(null) }

    val multiPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val loaded = mutableListOf<Bitmap>()
                uris.forEach { uri ->
                    AgniMediaProcessor.loadBitmapFromUri(context, uri, maxDim = 1080)
                        .onSuccess { loaded.add(it) }
                }
                if (loaded.isNotEmpty()) {
                    onAddSlideBitmaps(loaded)
                }
            }
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedVideoUri = uri
            frameExtractError = null
        }
    }

    LaunchedEffect(selectedVideoUri) {
        val uri = selectedVideoUri
        if (uri != null) {
            val dur = AgniMediaProcessor.getVideoDurationMs(context, uri)
            videoDurationMs = dur
            trimRange = 0f..1f
        } else {
            videoDurationMs = 0L
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 780.dp)
            .navigationBarsPadding()
    ) {
        // Export Progress Banner
        if (isExporting) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Text(
                            text = "Exporting MP4 Video… ${(exportProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.titleSmall
                        )
                        LinearProgressIndicator(
                            progress = { exportProgress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // Last Exported Video Preview
        if (lastExportedVideoUri != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Text(
                            text = "Saved Video Preview (Movies/AgniAI)",
                            style = MaterialTheme.typography.titleSmall,
                            color = EmeraldSuccess
                        )
                        AgniVideoPreviewPlayer(
                            uri = lastExportedVideoUri,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                    }
                }
            }
        }

        // SECTION 1: Create MP4 Video from Photos
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.MovieCreation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Create Video from Photos",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Turn your photos into a smooth HD MP4 slideshow with motion zoom & title.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                multiPhotoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("video_pick_photos_button")
                        ) {
                            Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Photos (${videoSlides.size})")
                        }
                        if (videoSlides.isNotEmpty()) {
                            TextButton(onClick = onClearSlides) {
                                Text("Clear All")
                            }
                        }
                    }

                    if (videoSlides.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(videoSlides) { index, bmp ->
                                Box(modifier = Modifier.size(86.dp)) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Video slide ${index + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                    Surface(
                                        onClick = { onRemoveSlide(index) },
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.7f),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(24.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "Remove slide",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Slide Duration Selector
                    Text("Seconds per Photo", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2, 3, 4, 5).forEach { sec ->
                            FilterChip(
                                selected = secondsPerSlide == sec,
                                onClick = { secondsPerSlide = sec },
                                label = { Text("${sec}s") }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = videoTitleText,
                        onValueChange = { if (it.length <= 40) videoTitleText = it },
                        label = { Text("Video Caption / Title Overlay (Optional)") },
                        placeholder = { Text("e.g., My Memories · Agni AI") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { onExportSlideshow(secondsPerSlide, videoTitleText) },
                        enabled = videoSlides.isNotEmpty() && !isExporting,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("video_create_mp4_button")
                    ) {
                        Icon(Icons.Rounded.MovieCreation, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create & Save MP4 Video (${videoSlides.size * secondsPerSlide}s)")
                    }
                }
            }
        }

        // SECTION 2: Trim & Edit Existing Video
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCut,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Edit & Trim Video",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Cut any part of a video with audio preserved, or grab a frame to edit as a photo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            videoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("video_pick_existing_button")
                    ) {
                        Icon(Icons.Rounded.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (selectedVideoUri == null) "Pick Video from Phone" else "Change Selected Video")
                    }

                    val activeVideo = selectedVideoUri
                    if (activeVideo != null) {
                        AgniVideoPreviewPlayer(
                            uri = activeVideo,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )

                        if (videoDurationMs > 500L) {
                            val startMs = (trimRange.start * videoDurationMs).toLong()
                            val endMs = (trimRange.endInclusive * videoDurationMs).toLong()

                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Start: ${formatMs(startMs)}",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(
                                    text = "Clip: ${formatMs((endMs - startMs).coerceAtLeast(0L))}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "End: ${formatMs(endMs)}",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }

                            RangeSlider(
                                value = trimRange,
                                onValueChange = { updated ->
                                    if (updated.endInclusive - updated.start >= 0.03f) {
                                        trimRange = updated
                                    }
                                },
                                valueRange = 0f..1f
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = { onTrimVideo(activeVideo, startMs, endMs) },
                                    enabled = !isExporting && (endMs - startMs) >= 400L,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .testTag("video_trim_export_button")
                                ) {
                                    Icon(Icons.Rounded.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Export Trim")
                                }
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            frameExtractError = null
                                            AgniMediaProcessor.extractVideoFrame(context, activeVideo, startMs)
                                                .onSuccess { bmp -> onExtractFrameToPhotoEditor(bmp) }
                                                .onFailure { err ->
                                                    frameExtractError = err.localizedMessage ?: "Frame capture failed."
                                                }
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("video_capture_frame_button")
                                ) {
                                    Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Grab Frame")
                                }
                            }
                        }

                        if (frameExtractError != null) {
                            Text(
                                text = frameExtractError.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgniVideoPreviewPlayer(
    uri: Uri,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    val controller = MediaController(ctx)
                    controller.setAnchorView(this)
                    setMediaController(controller)
                    setVideoURI(uri)
                    setOnPreparedListener { mp ->
                        mp.isLooping = true
                        start()
                    }
                }
            },
            update = { view ->
                if (view.tag != uri.toString()) {
                    view.tag = uri.toString()
                    view.setVideoURI(uri)
                    view.start()
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return String.format("%02d:%02d", min, sec)
}
