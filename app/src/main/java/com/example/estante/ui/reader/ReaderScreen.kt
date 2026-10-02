package com.example.estante.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.estante.data.ReadingMode
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

private val NightColorMatrix = ColorMatrix(
    floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    vm: ReaderViewModel,
    onBack: () -> Unit
) {
    val book = vm.book
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var chromeVisible by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }

    val backgroundColor = when (vm.readingMode) {
        ReadingMode.LIGHT -> Color(0xFFDADADA)
        ReadingMode.SEPIA -> Color(0xFFE9D9B8)
        ReadingMode.NIGHT -> Color.Black
    }
    val pageColorFilter = when (vm.readingMode) {
        ReadingMode.LIGHT -> null
        ReadingMode.SEPIA -> ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 0.93f, 0f, 0f, 0f,
                    0f, 0f, 0.78f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        ReadingMode.NIGHT -> ColorFilter.colorMatrix(NightColorMatrix)
    }

    /* ---------- brilho da tela ---------- */
    val activity = LocalContext.current.findActivity()
    LaunchedEffect(vm.brightnessOverride) {
        val window = activity?.window ?: return@LaunchedEffect
        window.attributes = window.attributes.apply {
            screenBrightness = vm.brightnessOverride
                ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }
    DisposableEffect(activity) {
        onDispose {
            val window = activity?.window ?: return@onDispose
            window.attributes = window.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        val errorMessage = vm.error
        when {
            errorMessage != null -> ReaderErrorContent(message = errorMessage, onBack = onBack)
            book == null || vm.pageCount <= 0 -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                val pageCount = vm.pageCount
                val pagerState = rememberPagerState(
                    initialPage = vm.currentPage.coerceIn(0, pageCount - 1)
                ) { pageCount }

                // Salva o progresso sempre que a página muda.
                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.currentPage }.collect { page ->
                        vm.onPageChanged(page)
                    }
                }

                var zoom by remember { mutableFloatStateOf(1f) }
                var panOffset by remember { mutableStateOf(Offset.Zero) }
                // Resetar o zoom ao virar a página.
                LaunchedEffect(pagerState.currentPage) {
                    zoom = 1f
                    panOffset = Offset.Zero
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    userScrollEnabled = zoom <= 1.01f
                ) { pageIndex ->
                    PdfPageContainer(
                        vm = vm,
                        index = pageIndex,
                        colorFilter = pageColorFilter,
                        background = backgroundColor,
                        zoom = zoom,
                        panOffset = panOffset,
                        onZoomChange = { value -> zoom = value },
                        onPanChange = { value -> panOffset = value },
                        onTapPrevious = {
                            scope.launch {
                                val target = pagerState.currentPage - 1
                                if (target >= 0) pagerState.animateScrollToPage(target)
                            }
                        },
                        onTapNext = {
                            scope.launch {
                                val target = pagerState.currentPage + 1
                                if (target < pageCount) pagerState.animateScrollToPage(target)
                            }
                        },
                        onToggleChrome = { chromeVisible = !chromeVisible }
                    )
                }

                /* ---------- barra superior ---------- */
                AnimatedVisibility(
                    visible = chromeVisible,
                    enter = fadeIn() + slideInVertically { -it },
                    exit = fadeOut() + slideOutVertically { -it },
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    TopAppBar(
                        title = {
                            Text(
                                text = book.title,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Voltar"
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = {
                                vm.addBookmark()
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "Marcador adicionado na página ${vm.currentPage + 1}"
                                    )
                                }
                            }) {
                                Icon(Icons.Filled.BookmarkAdd, contentDescription = "Marcar página atual")
                            }
                            IconButton(onClick = { showBookmarks = true }) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Marcadores")
                            }
                            IconButton(onClick = { showSettings = true }) {
                                Icon(Icons.Filled.Settings, contentDescription = "Ajustes de leitura")
                            }
                        }
                    )
                }

                /* ---------- barra inferior ---------- */
                AnimatedVisibility(
                    visible = chromeVisible,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it },
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                        Row(
                            modifier = Modifier
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${pagerState.currentPage + 1}",
                                style = MaterialTheme.typography.labelMedium
                            )
                            if (pageCount > 1) {
                                Slider(
                                    value = pagerState.currentPage.toFloat(),
                                    onValueChange = { value ->
                                        scope.launch {
                                            pagerState.scrollToPage(value.roundToInt())
                                        }
                                    },
                                    valueRange = 0f..(pageCount - 1).toFloat(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 12.dp)
                                )
                                Text(
                                    text = "$pageCount",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 88.dp)
                )

                if (showSettings) {
                    ReaderSettingsSheet(
                        vm = vm,
                        onDismiss = { showSettings = false }
                    )
                }
                if (showBookmarks) {
                    BookmarksSheet(
                        vm = vm,
                        onDismiss = { showBookmarks = false },
                        onJump = { page ->
                            scope.launch { pagerState.scrollToPage(page) }
                        }
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Página do PDF com zoom/pan e zonas de toque                         */
/* ------------------------------------------------------------------ */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun PdfPageContainer(
    vm: ReaderViewModel,
    index: Int,
    colorFilter: ColorFilter?,
    background: Color,
    zoom: Float,
    panOffset: Offset,
    onZoomChange: (Float) -> Unit,
    onPanChange: (Offset) -> Unit,
    onTapPrevious: () -> Unit,
    onTapNext: () -> Unit,
    onToggleChrome: () -> Unit
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        val newZoom = (zoom * zoomChange).coerceIn(1f, 6f)
        onZoomChange(newZoom)
        if (newZoom > 1.01f && containerSize != IntSize.Zero) {
            val maxX = (newZoom - 1f) * containerSize.width / 2f
            val maxY = (newZoom - 1f) * containerSize.height / 2f
            onPanChange(
                Offset(
                    (panOffset.x + panChange.x).coerceIn(-maxX, maxX),
                    (panOffset.y + panChange.y).coerceIn(-maxY, maxY)
                )
            )
        } else {
            onPanChange(Offset.Zero)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val width = containerSize.width.coerceAtLeast(1)
                        when {
                            offset.x < width * 0.3f -> onTapPrevious()
                            offset.x > width * 0.7f -> onTapNext()
                            else -> onToggleChrome()
                        }
                    }
                )
            }
            .transformable(
                state = transformableState,
                canPan = { zoom > 1.01f }
            )
            .onSizeChanged { containerSize = it }
    ) {
        PdfPageView(
            vm = vm,
            index = index,
            colorFilter = colorFilter,
            modifier = Modifier
                .align(Alignment.Center)
                .clipToBounds()
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = panOffset.x
                    translationY = panOffset.y
                }
        )
    }
}

@Composable
private fun PdfPageView(
    vm: ReaderViewModel,
    index: Int,
    colorFilter: ColorFilter?,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val targetWidth = constraints.maxWidth
        val bitmap: Bitmap? by produceState<Bitmap?>(null, index, targetWidth) {
            value = if (targetWidth > 0) vm.renderPage(index, targetWidth) else null
        }
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = "Página ${index + 1}",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Fit,
                colorFilter = colorFilter
            )
        } else {
            CircularProgressIndicator(Modifier.padding(top = 120.dp))
        }
    }
}

/* ------------------------------------------------------------------ */
/* Erro                                                                */
/* ------------------------------------------------------------------ */

@Composable
private fun ReaderErrorContent(message: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack) { Text("Voltar para a estante") }
    }
}

/* ------------------------------------------------------------------ */
/* Ajustes de leitura                                                  */
/* ------------------------------------------------------------------ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSettingsSheet(
    vm: ReaderViewModel,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text("Ajustes de leitura", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Text("Modo de leitura", style = MaterialTheme.typography.labelLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                listOf(
                    ReadingMode.LIGHT to "Claro",
                    ReadingMode.SEPIA to "Sépia",
                    ReadingMode.NIGHT to "Noturno"
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = vm.readingMode == mode,
                        onClick = { vm.applyReadingMode(mode) },
                        label = { Text(label) }
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.BrightnessMedium,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.size(8.dp))
                Text("Brilho da tela", style = MaterialTheme.typography.labelLarge)
            }
            Slider(
                value = vm.brightnessOverride ?: 1f,
                onValueChange = { value -> vm.brightnessOverride = value.coerceIn(0.05f, 1f) },
                valueRange = 0.05f..1f,
                modifier = Modifier.padding(top = 4.dp)
            )
            TextButton(onClick = { vm.brightnessOverride = null }) {
                Text("Usar brilho do sistema")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Dica: toque nas laterais da tela para virar a página e no centro para mostrar/ocultar os controles. Use dois dedos para ampliar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* Marcadores                                                          */
/* ------------------------------------------------------------------ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarksSheet(
    vm: ReaderViewModel,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Marcadores",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { vm.addBookmark() }) {
                    Text("Adicionar página atual")
                }
            }
            Spacer(Modifier.height(4.dp))

            if (vm.bookmarks.isEmpty()) {
                Text(
                    text = "Nenhum marcador neste livro ainda.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(vm.bookmarks, key = { it.id }) { bookmark ->
                        val dateText = remember(bookmark.createdAt) {
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                .format(Date(bookmark.createdAt))
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            ) {
                                Text(
                                    text = "Página ${bookmark.pageIndex + 1}",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = dateText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { vm.removeBookmark(bookmark) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Excluir marcador",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Helper                                                              */
/* ------------------------------------------------------------------ */

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
