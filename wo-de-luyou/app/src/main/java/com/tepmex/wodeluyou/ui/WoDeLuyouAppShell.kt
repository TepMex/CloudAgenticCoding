package com.tepmex.wodeluyou.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tepmex.wodeluyou.R
import com.tepmex.wodeluyou.data.CategoryTile
import com.tepmex.wodeluyou.data.DictionaryCatalog
import com.tepmex.wodeluyou.data.PlecoLinks
import com.tepmex.wodeluyou.data.RussianPlurals
import com.tepmex.wodeluyou.data.VocabEntry
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val HOME_ROUTE = "home"
private const val CATEGORY_ROUTE = "category/{name}"

private fun encodeRoute(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

private fun decodeRoute(value: String): String =
    URLDecoder.decode(value, StandardCharsets.UTF_8)

@Composable
fun WoDeLuyouAppShell(
    catalog: DictionaryCatalog,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val onCopied: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
    val onOpenPleco: (VocabEntry) -> Unit = { entry ->
        openPleco(context, entry.hanzi, onCopied)
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = HOME_ROUTE,
            modifier = Modifier.padding(padding),
        ) {
            composable(HOME_ROUTE) {
                HomeScreen(
                    catalog = catalog,
                    onOpenCategory = { name ->
                        navController.navigate("category/${encodeRoute(name)}")
                    },
                    onCopied = onCopied,
                    onOpenPleco = onOpenPleco,
                )
            }
            composable(
                route = CATEGORY_ROUTE,
                arguments = listOf(navArgument("name") { type = NavType.StringType }),
            ) { backStack ->
                val name = decodeRoute(backStack.arguments?.getString("name").orEmpty())
                CategoryScreen(
                    category = name,
                    entries = catalog.entriesIn(name),
                    onBack = { navController.popBackStack() },
                    onCopied = onCopied,
                    onOpenPleco = onOpenPleco,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    catalog: DictionaryCatalog,
    onOpenCategory: (String) -> Unit,
    onCopied: (String) -> Unit,
    onOpenPleco: (VocabEntry) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val searching = query.isNotBlank()
    val results = remember(query, catalog) { catalog.search(query) }

    Column(modifier = Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.app_name))
                    Text(
                        text = stringResource(R.string.app_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            shape = RoundedCornerShape(16.dp),
        )
        if (searching) {
            if (results.isEmpty()) {
                EmptyState(stringResource(R.string.search_empty))
            } else {
                WordList(
                    entries = results,
                    onCopied = onCopied,
                    onOpenPleco = onOpenPleco,
                )
            }
        } else {
            CategoryGrid(
                tiles = catalog.categories,
                onOpenCategory = onOpenCategory,
            )
        }
    }
}

@Composable
private fun CategoryGrid(
    tiles: List<CategoryTile>,
    onOpenCategory: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(tiles, key = { it.name }) { tile ->
            CategoryTileButton(tile = tile, onClick = { onOpenCategory(tile.name) })
        }
    }
}

@Composable
private fun CategoryTileButton(
    tile: CategoryTile,
    onClick: () -> Unit,
) {
    val style = CategoryStyles.of(tile.name)
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = style.accent),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(
                imageVector = style.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
            Column {
                Text(
                    text = tile.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                    Text(
                    text = RussianPlurals.words(tile.count),
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryScreen(
    category: String,
    entries: List<VocabEntry>,
    onBack: () -> Unit,
    onCopied: (String) -> Unit,
    onOpenPleco: (VocabEntry) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = category,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                    )
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )
        if (entries.isEmpty()) {
            EmptyState(stringResource(R.string.category_empty))
        } else {
            WordList(
                entries = entries,
                onCopied = onCopied,
                onOpenPleco = onOpenPleco,
            )
        }
    }
}

@Composable
private fun WordList(
    entries: List<VocabEntry>,
    onCopied: (String) -> Unit,
    onOpenPleco: (VocabEntry) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(entries, key = { it.id }) { entry ->
            VocabCard(
                entry = entry,
                onCopied = onCopied,
                onOpenPleco = { onOpenPleco(entry) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VocabCard(
    entry: VocabEntry,
    onCopied: (String) -> Unit,
    onOpenPleco: () -> Unit,
) {
    val copiedHanzi = stringResource(R.string.copied_hanzi, entry.hanzi)
    val copiedPinyin = stringResource(R.string.copied_pinyin, entry.pinyin)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (entry.hasRegion || entry.priorityStars > 0) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (entry.hasRegion) {
                        Surface(
                            shape = RoundedCornerShape(100),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                text = entry.region,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                    if (entry.priorityStars > 0) {
                        Text(
                            text = "★".repeat(entry.priorityStars),
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            CopyableLine(
                text = entry.hanzi,
                contentDescription = stringResource(R.string.copy_hanzi),
                onCopy = { onCopied(copiedHanzi) },
                emphasize = true,
            )
            Spacer(Modifier.height(6.dp))
            CopyableLine(
                text = entry.pinyin,
                contentDescription = stringResource(R.string.copy_pinyin),
                onCopy = { onCopied(copiedPinyin) },
                emphasize = false,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = entry.russian,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            if (entry.hasNote) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = entry.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onOpenPleco,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(
                    Icons.Outlined.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.open_in_pleco))
            }
        }
    }
}

@Composable
private fun CopyableLine(
    text: String,
    contentDescription: String,
    onCopy: () -> Unit,
    emphasize: Boolean,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) {
                copyToClipboard(context, text)
                onCopy()
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = if (emphasize) {
                MaterialTheme.typography.displaySmall.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 42.sp,
                )
            } else {
                MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
            },
        )
        Icon(
            imageVector = Icons.Outlined.ContentCopy,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 8.dp)
                .size(20.dp),
        )
    }
}

@Composable
private fun EmptyState(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("wo-de-luyou", text))
}

internal fun openPleco(
    context: Context,
    hanzi: String,
    onMessage: (String) -> Unit,
) {
    val uri = Uri.parse(PlecoLinks.searchUri(hanzi))
    val intent = Intent(Intent.ACTION_VIEW, uri)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        val message = context.getString(R.string.pleco_missing)
        onMessage(message)
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
