package com.tepmex.instantpinyin.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeometrySize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tepmex.instantpinyin.R
import com.tepmex.instantpinyin.domain.GlossLabel
import com.tepmex.instantpinyin.domain.GlossLabels
import com.tepmex.instantpinyin.domain.KnownReading
import com.tepmex.instantpinyin.domain.LabelFacing
import com.tepmex.instantpinyin.domain.NormRect
import com.tepmex.instantpinyin.domain.PinyinLabel
import com.tepmex.instantpinyin.domain.PinyinLabels
import com.tepmex.instantpinyin.domain.PlecoLinks
import com.tepmex.instantpinyin.domain.RecognitionZone
import com.tepmex.instantpinyin.domain.ZoneDrag
import com.tepmex.instantpinyin.ocr.FrameBitmaps
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var asked by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        asked = true
        granted = it
    }

    val still by viewModel.still.collectAsStateWithLifecycle()
    val knownText by viewModel.knownText.collectAsStateWithLifecycle()
    val onlyKnown by viewModel.onlyKnown.collectAsStateWithLifecycle()
    val pinyinOnly by viewModel.pinyinOnly.collectAsStateWithLifecycle()
    val known = remember(knownText) { KnownReading.knownSet(knownText) }
    var settingsOpen by remember { mutableStateOf(false) }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.onGallery(uri)
    }
    val openGallery = {
        galleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    if (settingsOpen) {
        SettingsScreen(
            initialText = knownText,
            onBack = { settingsOpen = false },
            onSave = viewModel::saveKnownText,
            modifier = modifier,
        )
        return
    }

    if (still !is StillState.Idle) {
        StillReader(
            state = still,
            known = known,
            onlyKnown = onlyKnown,
            pinyinOnly = pinyinOnly,
            onBack = viewModel::closeStill,
            onOpenSettings = { settingsOpen = true },
            modifier = modifier,
        )
        return
    }

    if (!granted) {
        PermissionGate(
            asked = asked,
            onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onGallery = openGallery,
            onOpenSettings = { settingsOpen = true },
            modifier = modifier,
        )
        return
    }

    LiveReader(
        viewModel = viewModel,
        known = known,
        onlyKnown = onlyKnown,
        pinyinOnly = pinyinOnly,
        onOnlyKnownChange = viewModel::setOnlyKnown,
        onPinyinOnlyChange = viewModel::setPinyinOnly,
        onZoneChange = viewModel::setZone,
        onOpenSettings = { settingsOpen = true },
        onGallery = openGallery,
        modifier = modifier,
    )
}

@Composable
private fun PermissionGate(
    asked: Boolean,
    onRequest: () -> Unit,
    onGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.permission_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.permission_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
        )
        Button(onClick = onRequest) {
            Text(stringResource(R.string.permission_button))
        }
        FilledTonalButton(
            onClick = onGallery,
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
            Text(
                text = stringResource(R.string.pick_gallery),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        TextButton(onClick = onOpenSettings, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.settings))
        }
        if (asked) {
            TextButton(
                onClick = {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    )
                    context.startActivity(intent)
                },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.permission_settings))
            }
        }
    }
}

@Composable
private fun LiveReader(
    viewModel: ReaderViewModel,
    known: Set<String>,
    onlyKnown: Boolean,
    pinyinOnly: Boolean,
    onOnlyKnownChange: (Boolean) -> Unit,
    onPinyinOnlyChange: (Boolean) -> Unit,
    onZoneChange: (NormRect) -> Unit,
    onOpenSettings: () -> Unit,
    onGallery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val plecoMissing = stringResource(R.string.pleco_missing)
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val savedZone by viewModel.zone.collectAsStateWithLifecycle()
    var zone by remember { mutableStateOf(savedZone) }
    var zoneDragging by remember { mutableStateOf(false) }
    LaunchedEffect(savedZone, zoneDragging) {
        if (!zoneDragging) zone = savedZone
    }
    val zoneRef = remember { AtomicReference(NormRect.Default) }
    val viewRef = remember { AtomicReference(IntSize.Zero) }
    SideEffect {
        zoneRef.set(zone)
        viewRef.set(viewSize)
    }
    val configuration = LocalConfiguration.current
    var textRotation by remember { mutableIntStateOf(0) }
    DisposableEffect(configuration.orientation) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                textRotation = LabelFacing.textRotation(windowDegrees(context), LabelFacing.snapHold(orientation))
            }
        }
        if (listener.canDetectOrientation()) listener.enable()
        onDispose { listener.disable() }
    }
    var torchOn by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val alive = remember { AtomicBoolean(true) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    val lexicon = viewModel.lexicon
    val glyphs = remember(ui.glyphs, known, onlyKnown) {
        KnownReading.visibleGlyphs(ui.glyphs, known, onlyKnown)
    }
    val labels = remember(glyphs, ui.imageWidth, ui.imageHeight, viewSize, textRotation, known) {
        PinyinLabels.layout(
            glyphs = glyphs,
            imageWidth = ui.imageWidth,
            imageHeight = ui.imageHeight,
            viewWidth = viewSize.width.toFloat(),
            viewHeight = viewSize.height.toFloat(),
            textRotation = textRotation,
        ).map { KnownReading.mutePinyin(it, known) }
    }
    val glosses = remember(glyphs, ui.imageWidth, ui.imageHeight, viewSize, lexicon, textRotation, known, pinyinOnly) {
        if (pinyinOnly) {
            emptyList()
        } else {
            GlossLabels.layout(
                glyphs = glyphs,
                lexicon = lexicon,
                imageWidth = ui.imageWidth,
                imageHeight = ui.imageHeight,
                viewWidth = viewSize.width.toFloat(),
                viewHeight = viewSize.height.toFloat(),
                textRotation = textRotation,
            ).filter { KnownReading.keepGloss(it.text, known) }
        }
    }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.Default) {
            runCatching { viewModel.ocr.warmup() }.isSuccess
        }
        if (loaded) viewModel.markReady() else viewModel.markFailed()
    }

    DisposableEffect(Unit) {
        onDispose {
            alive.set(false)
            runCatching { provider?.unbindAll() }
            executor.shutdown()
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .onSizeChanged { viewSize = it },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    post {
                        if (!alive.get() || !isAttachedToWindow) return@post
                        val future = ProcessCameraProvider.getInstance(ctx)
                        future.addListener(
                            {
                                if (!alive.get()) return@addListener
                                val cameraProvider = future.get()
                                provider = cameraProvider
                                val resolution = ResolutionSelector.Builder()
                                    .setResolutionStrategy(
                                        ResolutionStrategy(
                                            Size(1280, 720),
                                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                        ),
                                    )
                                    .build()
                                val preview = Preview.Builder()
                                    .setResolutionSelector(resolution)
                                    .build()
                                    .also { it.surfaceProvider = surfaceProvider }
                                val analysis = ImageAnalysis.Builder()
                                    .setResolutionSelector(resolution)
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                                    .build()
                                analysis.setAnalyzer(executor) { image ->
                                    val viewport = try {
                                        val bitmap = FrameBitmaps.rgba(image)
                                        val crop = image.cropRect
                                        FrameBitmaps.uprightViewport(
                                            source = bitmap,
                                            bufferWidth = image.width,
                                            bufferHeight = image.height,
                                            cropLeft = crop.left,
                                            cropTop = crop.top,
                                            cropRight = crop.right,
                                            cropBottom = crop.bottom,
                                            rotationDegrees = image.imageInfo.rotationDegrees,
                                        )
                                    } catch (error: Exception) {
                                        Log.w(TAG, "frame copy failed", error)
                                        null
                                    } finally {
                                        image.close()
                                    } ?: return@setAnalyzer
                                    val crop = RecognitionZone.bitmapCrop(
                                        zoneRef.get(),
                                        viewport.width,
                                        viewport.height,
                                        viewRef.get().width.toFloat(),
                                        viewRef.get().height.toFloat(),
                                    )
                                    val ocrBitmap = if (crop == null) {
                                        viewport
                                    } else {
                                        Bitmap.createBitmap(viewport, crop.left, crop.top, crop.width, crop.height)
                                    }
                                    try {
                                        val lines = viewModel.ocr.recognize(ocrBitmap, maxBoxes = 32)
                                        val shifted = if (crop == null) {
                                            lines
                                        } else {
                                            lines.map { line ->
                                                line.copy(box = line.box.copy(x = line.box.x + crop.left, y = line.box.y + crop.top))
                                            }
                                        }
                                        viewModel.publish(shifted, viewport.width, viewport.height)
                                    } catch (error: Exception) {
                                        Log.w(TAG, "ocr failed", error)
                                    } finally {
                                        if (ocrBitmap !== viewport) ocrBitmap.recycle()
                                        viewport.recycle()
                                    }
                                }
                                val capture = ImageCapture.Builder()
                                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                    .build()
                                val group = UseCaseGroup.Builder()
                                    .addUseCase(preview)
                                    .addUseCase(analysis)
                                    .addUseCase(capture)
                                viewPort?.let { group.setViewPort(it) }
                                cameraProvider.unbindAll()
                                val camera = try {
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        group.build(),
                                    )
                                } catch (error: Exception) {
                                    Log.e(TAG, "camera bind failed", error)
                                    viewModel.markFailed()
                                    return@addListener
                                }
                                cameraControl = camera.cameraControl
                                imageCapture = capture
                                if (torchOn) camera.cameraControl.enableTorch(true)
                            },
                            ContextCompat.getMainExecutor(ctx),
                        )
                    }
                }
            },
        )

        RecognitionScrim(zone)

        PinyinArOverlay(
            labels = labels,
            glosses = glosses,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(labels, glosses) {
                    detectTapGestures { offset ->
                        val gloss = glosses.asReversed().firstOrNull { it.hit(offset.x, offset.y) }
                        val hanzi = gloss?.text
                            ?: labels.asReversed().firstOrNull { it.hit(offset.x, offset.y) }?.hanzi
                            ?: return@detectTapGestures
                        if (!openPleco(context, hanzi)) {
                            scope.launch { snackbar.showSnackbar(plecoMissing) }
                        }
                    }
                },
        )

        ZoneHandles(
            zone = zone,
            onDragStart = { zoneDragging = true },
            onDrag = { mode, dx, dy ->
                if (viewSize.width > 0 && viewSize.height > 0) {
                    zone = RecognitionZone.drag(
                        zone,
                        mode,
                        dx,
                        dy,
                        viewSize.width.toFloat(),
                        viewSize.height.toFloat(),
                    )
                }
            },
            onDragEnd = {
                zoneDragging = false
                onZoneChange(zone)
            },
        )

        val hint = when {
            ui.phase == ReaderPhase.FAILED -> stringResource(R.string.hint_failed)
            ui.phase == ReaderPhase.LOADING -> stringResource(R.string.hint_loading)
            labels.isEmpty() -> stringResource(R.string.hint_point)
            else -> null
        }
        if (hint != null) {
            Text(
                text = hint,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 24.dp, end = 24.dp, bottom = 88.dp)
                    .background(Color(0xCC101418), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilledTonalButton(onClick = onGallery) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = stringResource(R.string.pick_gallery))
                Text(
                    text = stringResource(R.string.pick_gallery),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Button(
                onClick = {
                    val capture = imageCapture ?: return@Button
                    capture.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val bmp = image.toBitmap()
                                image.close()
                                viewModel.onPhoto(bmp)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                Log.w(TAG, "still capture failed", exception)
                            }
                        },
                    )
                },
                enabled = imageCapture != null,
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = stringResource(R.string.take_photo))
                Text(
                    text = stringResource(R.string.take_photo),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 4.dp, top = 4.dp),
        ) {
            IconButton(onClick = onOpenSettings) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.settings),
                    tint = Color.White,
                )
            }
            ReaderCheck(
                checked = onlyKnown,
                label = stringResource(R.string.only_known),
                onToggle = { onOnlyKnownChange(!onlyKnown) },
            )
            ReaderCheck(
                checked = pinyinOnly,
                label = stringResource(R.string.pinyin_only),
                onToggle = { onPinyinOnlyChange(!pinyinOnly) },
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        IconButton(
            onClick = {
                torchOn = !torchOn
                cameraControl?.enableTorch(torchOn)
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(8.dp),
        ) {
            Icon(
                imageVector = if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                contentDescription = stringResource(
                    if (torchOn) R.string.torch_off else R.string.torch_on,
                ),
                tint = Color.White,
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars),
        )
    }
}

@Composable
private fun ReaderCheck(
    checked: Boolean,
    label: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(Color(0xCC101418), RoundedCornerShape(20.dp))
            .clickable(onClick = onToggle)
            .padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = Color.White,
                uncheckedColor = Color.White,
                checkmarkColor = Color(0xFF101418),
            ),
        )
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun RecognitionScrim(zone: NormRect) {
    Canvas(Modifier.fillMaxSize()) {
        val l = zone.left * size.width
        val t = zone.top * size.height
        val r = zone.right * size.width
        val b = zone.bottom * size.height
        val dim = Color(0x99000000)
        drawRect(dim, size = GeometrySize(size.width, t))
        drawRect(dim, topLeft = Offset(0f, b), size = GeometrySize(size.width, size.height - b))
        drawRect(dim, topLeft = Offset(0f, t), size = GeometrySize(l, b - t))
        drawRect(dim, topLeft = Offset(r, t), size = GeometrySize(size.width - r, b - t))
        drawRect(
            color = Color.White,
            topLeft = Offset(l, t),
            size = GeometrySize((r - l).coerceAtLeast(0f), (b - t).coerceAtLeast(0f)),
            style = Stroke(width = 3f),
        )
    }
}

@Composable
private fun ZoneHandles(
    zone: NormRect,
    onDragStart: () -> Unit,
    onDrag: (ZoneDrag, Float, Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val density = LocalDensity.current
    val handle = 28.dp
    val handlePx = with(density) { handle.toPx() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        for (mode in ZoneDrag.entries) {
            val (cx, cy) = handleCenter(mode, zone, widthPx, heightPx)
            val description = stringResource(
                if (mode == ZoneDrag.MOVE) R.string.zone_move else R.string.zone_resize,
            )
            Box(
                modifier = Modifier
                    .offset { IntOffset((cx - handlePx / 2f).roundToInt(), (cy - handlePx / 2f).roundToInt()) }
                    .size(handle)
                    .semantics { contentDescription = description }
                    .pointerInput(mode) {
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                        ) { change, drag ->
                            change.consume()
                            onDrag(mode, drag.x, drag.y)
                        }
                    }
                    .background(
                        if (mode == ZoneDrag.MOVE) Color(0xFFFFE08A) else Color.White,
                        RoundedCornerShape(50),
                    ),
            )
        }
    }
}

private fun handleCenter(mode: ZoneDrag, zone: NormRect, width: Float, height: Float): Pair<Float, Float> {
    val l = zone.left * width
    val t = zone.top * height
    val r = zone.right * width
    val b = zone.bottom * height
    val cx = (l + r) / 2f
    val cy = (t + b) / 2f
    return when (mode) {
        ZoneDrag.TOP_LEFT -> l to t
        ZoneDrag.TOP_RIGHT -> r to t
        ZoneDrag.BOTTOM_LEFT -> l to b
        ZoneDrag.BOTTOM_RIGHT -> r to b
        ZoneDrag.LEFT -> l to cy
        ZoneDrag.RIGHT -> r to cy
        ZoneDrag.TOP -> cx to t
        ZoneDrag.BOTTOM -> cx to b
        ZoneDrag.MOVE -> cx to t - 22f
    }
}

@Composable
private fun PinyinArOverlay(
    labels: List<PinyinLabel>,
    glosses: List<GlossLabel>,
    modifier: Modifier = Modifier,
) {
    val textPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
    }
    val bgPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(200, 10, 14, 18)
        }
    }
    val glossPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(255, 224, 138)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
    }
    val glossBgPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(210, 28, 22, 8)
        }
    }
    Canvas(modifier) {
        val native = drawContext.canvas.nativeCanvas
        for (label in labels) {
            if (label.pinyin.isEmpty()) continue
            drawPill(
                native,
                textPaint,
                bgPaint,
                label.pinyin,
                label.textSizePx,
                label.pill,
                size.width,
                label.textRotation,
            )
        }
        for (gloss in glosses) {
            drawPill(
                native,
                glossPaint,
                glossBgPaint,
                gloss.gloss,
                gloss.textSizePx,
                gloss.pill,
                size.width,
                gloss.textRotation,
            )
        }
    }
}

private fun drawPill(
    native: android.graphics.Canvas,
    textPaint: Paint,
    bgPaint: Paint,
    text: String,
    textSizePx: Float,
    box: com.tepmex.instantpinyin.domain.PxBox,
    viewWidth: Float,
    rotation: Int,
) {
    textPaint.textSize = textSizePx
    val quarter = rotation == 90 || rotation == 270
    val measured = textPaint.measureText(text)
    val padX = textSizePx * 0.38f
    val along = max(measured + padX * 2f, if (quarter) box.height else box.width)
    val across = if (quarter) box.width else box.height
    val screenAlong = if (quarter) across else along
    val maxLeft = (viewWidth - screenAlong).coerceAtLeast(0f)
    val centerX = if (quarter) box.centerX else (box.centerX).coerceIn(along / 2f, maxLeft + along / 2f)
    native.save()
    native.rotate(rotation.toFloat(), centerX, box.centerY)
    val left = centerX - along / 2f
    val top = box.centerY - across / 2f
    val pill = android.graphics.RectF(left, top, left + along, top + across)
    val radius = textSizePx * 0.38f
    native.drawRoundRect(pill, radius, radius, bgPaint)
    val metrics = textPaint.fontMetrics
    val baseline = pill.centerY() - (metrics.ascent + metrics.descent) / 2f
    native.drawText(text, pill.centerX(), baseline, textPaint)
    native.restore()
}

private fun windowDegrees(context: android.content.Context): Int = when (context.display?.rotation) {
    Surface.ROTATION_90 -> 90
    Surface.ROTATION_180 -> 180
    Surface.ROTATION_270 -> 270
    else -> 0
}

private fun openPleco(context: android.content.Context, hanzi: String): Boolean {
    return try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(PlecoLinks.searchUri(hanzi))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

private const val TAG = "InstantPinyin"
