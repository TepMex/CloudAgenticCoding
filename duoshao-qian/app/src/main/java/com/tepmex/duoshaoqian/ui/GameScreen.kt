package com.tepmex.duoshaoqian.ui

import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tepmex.duoshaoqian.R
import com.tepmex.duoshaoqian.data.Product
import com.tepmex.duoshaoqian.data.RmbWallet
import com.tepmex.duoshaoqian.data.ShopSnapshot
import com.tepmex.duoshaoqian.ui.theme.Cinnabar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val text = when (message) {
            GameMessage.NeedListen -> context.getString(R.string.listen_first)
            GameMessage.Incorrect -> context.getString(R.string.incorrect)
            is GameMessage.Correct -> context.getString(R.string.correct, message.priceYuan, message.spoken)
            is GameMessage.Skipped -> context.getString(R.string.skipped, message.priceYuan, message.spoken)
        }
        snackbar.showSnackbar(text)
        viewModel.consumeMessage()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("多少钱", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                        val snapshot = state.snapshot
                        if (snapshot != null) {
                            Text(
                                text = stringResource(R.string.score, snapshot.correctCount, snapshot.streak),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Cinnabar,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val snapshot = state.snapshot
        if (snapshot == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.catalog_error),
                    modifier = Modifier.padding(24.dp),
                    textAlign = TextAlign.Center,
                )
            }
            return@Scaffold
        }
        ShopContent(
            snapshot = snapshot,
            onAsk = viewModel::askPrice,
            onAddNote = viewModel::addNote,
            onRemoveNote = viewModel::removeNoteAt,
            onClear = viewModel::clearTender,
            onPay = viewModel::pay,
            onSkip = viewModel::skip,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

@Composable
private fun ShopContent(
    snapshot: ShopSnapshot,
    onAsk: () -> Unit,
    onAddNote: (Int) -> Unit,
    onRemoveNote: (Int) -> Unit,
    onClear: () -> Unit,
    onPay: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProductCard(product = snapshot.round.product)
        Button(
            onClick = onAsk,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Outlined.VolumeUp, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.ask_price), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            text = stringResource(R.string.wallet_label),
            style = MaterialTheme.typography.titleMedium,
        )
        NoteGrid(onAddNote = onAddNote)
        TenderTray(
            tender = snapshot.tender,
            onRemoveNote = onRemoveNote,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.total_label, snapshot.tenderYuan),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClear, enabled = snapshot.tender.isNotEmpty()) {
                Text(stringResource(R.string.clear_tender))
            }
        }
        Button(
            onClick = onPay,
            enabled = snapshot.tender.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
            ),
        ) {
            Text(stringResource(R.string.pay), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        TextButton(
            onClick = onSkip,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(stringResource(R.string.skip))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ProductCard(product: Product) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AssetPhoto(
            path = product.imageAsset,
            contentDescription = product.russian,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(16.dp)),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = product.hanzi,
            style = MaterialTheme.typography.displaySmall,
            fontSize = 34.sp,
        )
        Text(
            text = product.pinyin,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = product.russian,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun AssetPhoto(
    path: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap = remember(path) {
        context.assets.open(path).use { BitmapFactory.decodeStream(it) }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(contentDescription)
        }
    }
}

@Composable
private fun NoteGrid(onAddNote: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RmbWallet.notes.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { note ->
                    RmbNoteCard(
                        note = note,
                        modifier = Modifier.weight(1f),
                        onClick = { onAddNote(note.yuan) },
                    )
                }
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TenderTray(
    tender: List<Int>,
    onRemoveNote: (Int) -> Unit,
) {
    val notesByYuan = remember { RmbWallet.notes.associateBy { it.yuan } }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (tender.isEmpty()) {
            Text(
                text = stringResource(R.string.tender_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(tender) { index, yuan ->
                    val note = notesByYuan[yuan] ?: return@itemsIndexed
                    RmbNoteCard(
                        note = note,
                        modifier = Modifier.width(132.dp),
                        compact = true,
                        onClick = { onRemoveNote(index) },
                    )
                }
            }
        }
    }
}
