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
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
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
import androidx.compose.material3.Button
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

    if (!granted) {
        PermissionGate(
            asked = asked,
            onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            modifier = modifier,
        )
        return
    }

    LiveReader(
        viewModel = viewModel,
        modifier = modifier,
    )
}

@Composable
private fun PermissionGate(
    asked: Boolean,
    onRequest: () -> Unit,
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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val plecoMissing = stringResource(R.string.pleco_missing)
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var torchOn by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val alive = remember { AtomicBoolean(true) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    val lexicon = viewModel.lexicon
    val labels = remember(ui.glyphs, ui.imageWidth, ui.imageHeight, viewSize) {
        PinyinLabels.layout(
            glyphs = ui.glyphs,
            imageWidth = ui.imageWidth,
            imageHeight = ui.imageHeight,
            viewWidth = viewSize.width.toFloat(),
            viewHeight = viewSize.height.toFloat(),
        )
    }
    val glosses = remember(ui.glyphs, ui.imageWidth, ui.imageHeight, viewSize, lexicon) {
        GlossLabels.layout(
            glyphs = ui.glyphs,
            lexicon = lexicon,
            imageWidth = ui.imageWidth,
            imageHeight = ui.imageHeight,
            viewWidth = viewSize.width.toFloat(),
            viewHeight = viewSize.height.toFloat(),
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
                                val group = UseCaseGroup.Builder()
                                    .addUseCase(preview)
                                    .addUseCase(analysis)
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
                    .padding(start = 24.dp, end = 24.dp, bottom = 28.dp)
                    .background(Color(0xCC101418), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
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
            drawPill(native, textPaint, bgPaint, label.pinyin, label.textSizePx, label.pill, size.width)
        }
        for (gloss in glosses) {
            drawPill(native, glossPaint, glossBgPaint, gloss.gloss, gloss.textSizePx, gloss.pill, size.width)
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
) {
    textPaint.textSize = textSizePx
    val measured = textPaint.measureText(text)
    val padX = textSizePx * 0.38f
    val width = max(measured + padX * 2f, box.width)
    val maxLeft = (viewWidth - width).coerceAtLeast(0f)
    val left = (box.centerX - width / 2f).coerceIn(0f, maxLeft)
    val pill = android.graphics.RectF(left, box.top, left + width, box.bottom)
    val radius = textSizePx * 0.38f
    native.drawRoundRect(pill, radius, radius, bgPaint)
    val metrics = textPaint.fontMetrics
    val baseline = pill.centerY() - (metrics.ascent + metrics.descent) / 2f
    native.drawText(text, pill.centerX(), baseline, textPaint)
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
