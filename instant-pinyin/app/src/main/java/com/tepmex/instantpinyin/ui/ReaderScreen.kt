package com.tepmex.instantpinyin.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tepmex.instantpinyin.R
import com.tepmex.instantpinyin.domain.GlossLabel
import com.tepmex.instantpinyin.domain.GlossLabels
import com.tepmex.instantpinyin.domain.LabelFacing
import com.tepmex.instantpinyin.domain.PinyinLabel
import com.tepmex.instantpinyin.domain.PinyinLabels
import com.tepmex.instantpinyin.domain.PlecoLinks
import com.tepmex.instantpinyin.ocr.FrameBitmaps
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
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

    if (still !is StillState.Idle) {
        StillReader(
            state = still,
            onBack = viewModel::closeStill,
            modifier = modifier,
        )
        return
    }

    if (!granted) {
        PermissionGate(
            asked = asked,
            onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onGallery = openGallery,
            modifier = modifier,
        )
        return
    }

    LiveReader(
        viewModel = viewModel,
        onGallery = openGallery,
        modifier = modifier,
    )
}

@Composable
private fun PermissionGate(
    asked: Boolean,
    onRequest: () -> Unit,
    onGallery: () -> Unit,
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
    val labels = remember(ui.glyphs, ui.imageWidth, ui.imageHeight, viewSize, textRotation) {
        PinyinLabels.layout(
            glyphs = ui.glyphs,
            imageWidth = ui.imageWidth,
            imageHeight = ui.imageHeight,
            viewWidth = viewSize.width.toFloat(),
            viewHeight = viewSize.height.toFloat(),
            textRotation = textRotation,
        )
    }
    val glosses = remember(ui.glyphs, ui.imageWidth, ui.imageHeight, viewSize, lexicon, textRotation) {
        GlossLabels.layout(
            glyphs = ui.glyphs,
            lexicon = lexicon,
            imageWidth = ui.imageWidth,
            imageHeight = ui.imageHeight,
            viewWidth = viewSize.width.toFloat(),
            viewHeight = viewSize.height.toFloat(),
            textRotation = textRotation,
        )
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
                                    try {
                                        val lines = viewModel.ocr.recognize(viewport, maxBoxes = 32)
                                        viewModel.publish(lines, viewport.width, viewport.height)
                                    } catch (error: Exception) {
                                        Log.w(TAG, "ocr failed", error)
                                    } finally {
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
