package aenu.aps3e.ui

import aenu.aps3e.Emulator
import aenu.aps3e.R
import aenu.aps3e.data.CoverRefreshResult
import aenu.aps3e.data.CoverRepository
import aenu.aps3e.data.GameCoverRequest
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed as listItemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import android.content.res.Configuration

interface MainScreenCallbacks {
    fun onInstallFirmware()
    fun onInstallGame()
    fun onRefreshList()
    fun onOpenSettings()
    fun onOpenAbout()
    fun onOpenKeymap()
    fun onOpenVirtualPadEdit()
    fun onOpenFileManager()
    fun onSetIsoDir()
    fun onOpenQuickStart()
    fun onBuyPremium()
    fun onGameClick(position: Int)
    fun onGameAction(actionId: Int, position: Int)
    fun isDiscGame(position: Int): Boolean
    fun onResumeLastGame(info: LastPlayedInfo)
}

enum class ViewMode {
    GRID,
    LIST,
    CAROUSEL
}

data class LastPlayedInfo(
    val serial: String,
    val name: String,
    val isoUri: String?,
    val gameDir: String?
)

private sealed class LibraryEntry {
    data class LastPlayed(val info: LastPlayedInfo, val coverPath: String?) : LibraryEntry()
    data class Game(val meta: Emulator.MetaInfo) : LibraryEntry()
}

private const val COVER_ASPECT_RATIO = 0.7f

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun Aps3eMainScreen(
    metasLiveData: LiveData<List<Emulator.MetaInfo>>,
    lastPlayedLiveData: LiveData<LastPlayedInfo?>,
    callbacks: MainScreenCallbacks
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val metasState = remember { mutableStateOf(metasLiveData.value ?: emptyList()) }
    val metas = metasState.value
    val displayMetas = remember(metas) {
        metas.sortedBy { it.getName() ?: "" }
    }
    val lastPlayedState = remember { mutableStateOf(lastPlayedLiveData.value) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val coverRepository = remember { CoverRepository(context.filesDir) }
    val coverPaths = remember { mutableStateMapOf<String, String>() }
    val gameDescriptions = remember { mutableStateMapOf<String, String>() }

    var viewMode by rememberSaveable { mutableStateOf(ViewMode.GRID) }
    var showDetails by rememberSaveable { mutableStateOf(true) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showMoreSheet by remember { mutableStateOf(false) }
    var isRefreshingCovers by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    DisposableEffect(metasLiveData) {
        val observer = Observer<List<Emulator.MetaInfo>> { list ->
            metasState.value = list
        }
        metasLiveData.observeForever(observer)
        onDispose { metasLiveData.removeObserver(observer) }
    }

    DisposableEffect(lastPlayedLiveData) {
        val observer = Observer<LastPlayedInfo?> { info ->
            lastPlayedState.value = info
        }
        lastPlayedLiveData.observeForever(observer)
        onDispose { lastPlayedLiveData.removeObserver(observer) }
    }

    LaunchedEffect(metas) {
        metas.forEach { meta ->
            val serial = meta.getSerial() ?: return@forEach
            val coverPath = coverRepository.getCoverPath(serial)
            if (coverPath != null) {
                coverPaths[serial] = coverPath
            }
        }
    }

    val filteredMetas = remember(displayMetas, searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            displayMetas
        } else {
            val lowerQuery = query.lowercase()
            displayMetas.filter { meta ->
                val name = meta.getName() ?: ""
                val cleaned = cleanedTitleForDisplay(name)
                name.lowercase().contains(lowerQuery) || cleaned.lowercase().contains(lowerQuery)
            }
        }
    }

    fun refreshCovers(force: Boolean = false) {
        if (isRefreshingCovers) return
        val pendingRequests = displayMetas.mapNotNull { meta ->
            val serial = meta.getSerial() ?: return@mapNotNull null
            if (!force && coverPaths.containsKey(serial)) return@mapNotNull null
            val name = meta.getName() ?: return@mapNotNull null
            GameCoverRequest(name, serial)
        }
        if (pendingRequests.isEmpty()) {
            scope.launch {
                snackbarHostState.showSnackbar("Tutte le cover sono gia presenti")
            }
            return
        }
        scope.launch {
            isRefreshingCovers = true
            if (force) {
                coverRepository.clearCovers()
                coverPaths.clear()
            }
            val result: CoverRefreshResult = coverRepository.refreshCovers(pendingRequests)
            val message = when {
                result.scrapedCount > 0 -> "${result.scrapedCount} cover scaricate"
                result.failedCount > 0 -> "Nessuna nuova cover trovata"
                else -> "Tutte le cover sono gia presenti"
            }
            snackbarHostState.showSnackbar(message)
            isRefreshingCovers = false
            pendingRequests.forEach { request ->
                val coverPath = coverRepository.getCoverPath(request.serial)
                if (coverPath != null) {
                    coverPaths[request.serial] = coverPath
                }
            }
        }
    }

    Aps3eTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    modifier = Modifier.height(46.dp),
                    title = {
                        Box(
                            modifier = Modifier.fillMaxHeight(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(id = R.drawable.app_icon),
                                    contentDescription = null,
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(id = R.string.app_name),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = Aps3eColors.Primary
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Aps3eColors.Surface,
                        titleContentColor = Aps3eColors.OnSurface
                    ),
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    actions = {
                        IconButton(onClick = { callbacks.onInstallGame() }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Aps3eColors.Accent,
                                modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { callbacks.onRefreshList() }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Aps3eColors.OnSurface,
                                modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { callbacks.onOpenSettings() }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = Aps3eColors.OnSurface,
                                modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { showMoreSheet = true }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.MoreVert, contentDescription = null, tint = Aps3eColors.OnSurface,
                                modifier = Modifier.size(20.dp))
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Aps3eColors.Background)
            ) {
                WaveBackground()

                if (displayMetas.isEmpty()) {
                    EmptyState()
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        ViewModeSelector(
                            viewMode = viewMode,
                            showDetails = showDetails,
                            lastPlayedInfo = if (isLandscape) lastPlayedState.value else null,
                            isRefreshingCovers = isRefreshingCovers,
                            onResumeLastPlayed = { callbacks.onResumeLastGame(it) },
                            onToggleDetails = { showDetails = !showDetails },
                            onRefreshCovers = { refreshCovers() },
                            onToggleSearch = {
                                val nextShow = !showSearch
                                showSearch = nextShow
                                if (!nextShow) {
                                    searchQuery = ""
                                }
                            },
                            onSelect = { viewMode = it }
                        )
                        if (showSearch) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                placeholder = { Text(text = stringResource(id = R.string.search_games)) },
                                singleLine = true
                            )
                        }
                        when (viewMode) {
                            ViewMode.GRID -> GameGrid(
                                metas = filteredMetas,
                                lastPlayedInfo = if (isLandscape) null else lastPlayedState.value,
                                lastPlayedCoverPath = if (isLandscape) null else lastPlayedState.value?.serial?.let { coverPaths[it] },
                                onResumeLastPlayed = { callbacks.onResumeLastGame(it) },
                                coverPaths = coverPaths,
                                callbacks = callbacks,
                                showDetails = showDetails
                            )
                            ViewMode.LIST -> GameList(
                                metas = filteredMetas,
                                lastPlayedInfo = if (isLandscape) null else lastPlayedState.value,
                                lastPlayedCoverPath = if (isLandscape) null else lastPlayedState.value?.serial?.let { coverPaths[it] },
                                onResumeLastPlayed = { callbacks.onResumeLastGame(it) },
                                coverPaths = coverPaths,
                                callbacks = callbacks,
                                showDetails = showDetails
                            )
                            ViewMode.CAROUSEL -> GameCarousel(
                                metas = filteredMetas,
                                lastPlayedInfo = if (isLandscape) null else lastPlayedState.value,
                                lastPlayedCoverPath = if (isLandscape) null else lastPlayedState.value?.serial?.let { coverPaths[it] },
                                onResumeLastPlayed = { callbacks.onResumeLastGame(it) },
                                coverRepository = coverRepository,
                                gameDescriptions = gameDescriptions,
                                coverPaths = coverPaths,
                                callbacks = callbacks,
                                showDetails = showDetails
                            )
                        }
                    }
                }
            }
        }

        if (showMoreSheet) {
            MoreActionsSheet(
                sheetState = sheetState,
                onDismiss = { showMoreSheet = false },
                onInstallFirmware = { callbacks.onInstallFirmware() },
                onOpenKeymap = { callbacks.onOpenKeymap() },
                onOpenAbout = { callbacks.onOpenAbout() },
                onOpenVirtualPadEdit = { callbacks.onOpenVirtualPadEdit() },
                onOpenFileManager = { callbacks.onOpenFileManager() },
                onSetIsoDir = { callbacks.onSetIsoDir() },
                onOpenQuickStart = { callbacks.onOpenQuickStart() },
                onBuyPremium = { callbacks.onBuyPremium() },
                onRefreshCovers = { refreshCovers() },
                onForceRefreshCovers = { refreshCovers(force = true) }
            )
        }
    }
}

private data class MoreAction(
    val title: String,
    val subtitle: String,
    val accent: Color,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreActionsSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onInstallFirmware: () -> Unit,
    onOpenKeymap: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenVirtualPadEdit: () -> Unit,
    onOpenFileManager: () -> Unit,
    onSetIsoDir: () -> Unit,
    onOpenQuickStart: () -> Unit,
    onBuyPremium: () -> Unit,
    onRefreshCovers: () -> Unit,
    onForceRefreshCovers: () -> Unit
) {
    val setupActions = listOf(
        MoreAction(
            title = stringResource(id = R.string.install_firmware),
            subtitle = stringResource(id = R.string.installing_firmware),
            accent = Aps3eColors.Primary,
            onClick = onInstallFirmware
        ),
        MoreAction(
            title = stringResource(id = R.string.set_iso_dir),
            subtitle = stringResource(id = R.string.select_ps3_iso_dir_hiht_content),
            accent = Aps3eColors.Secondary,
            onClick = onSetIsoDir
        ),
        MoreAction(
            title = stringResource(id = R.string.quick_start_page),
            subtitle = stringResource(id = R.string.welcome),
            accent = Aps3eColors.Accent,
            onClick = onOpenQuickStart
        ),
        MoreAction(
            title = stringResource(id = R.string.force_cover_art_search),
            subtitle = stringResource(id = R.string.refresh_list),
            accent = Aps3eColors.Warning,
            onClick = onForceRefreshCovers
        )
    )

    val toolsActions = listOf(
        MoreAction(
            title = stringResource(id = R.string.key_mappers),
            subtitle = stringResource(id = R.string.key_mappers),
            accent = Aps3eColors.Warning,
            onClick = onOpenKeymap
        ),
        MoreAction(
            title = stringResource(id = R.string.virtual_pad_edit),
            subtitle = stringResource(id = R.string.virtual_pad_edit),
            accent = Aps3eColors.Primary,
            onClick = onOpenVirtualPadEdit
        ),
        MoreAction(
            title = stringResource(id = R.string.open_file_manager),
            subtitle = stringResource(id = R.string.open_file_manager),
            accent = Aps3eColors.Secondary,
            onClick = onOpenFileManager
        )
    )

    val infoActions = listOf(
        MoreAction(
            title = stringResource(id = R.string.about),
            subtitle = stringResource(id = R.string.device_info),
            accent = Aps3eColors.Accent,
            onClick = onOpenAbout
        ),
        MoreAction(
            title = stringResource(id = R.string.buy_premium),
            subtitle = stringResource(id = R.string.buy_premium),
            accent = Aps3eColors.Warning,
            onClick = onBuyPremium
        ),
        MoreAction(
            title = "Refresh covers",
            subtitle = stringResource(id = R.string.refresh_list),
            accent = Aps3eColors.Secondary,
            onClick = onRefreshCovers
        )
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Aps3eColors.Surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SheetSection(title = "Setup", actions = setupActions, onDismiss = onDismiss)
            SheetSection(title = "Tools", actions = toolsActions, onDismiss = onDismiss)
            SheetSection(title = "Info", actions = infoActions, onDismiss = onDismiss)
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SheetSection(title: String, actions: List<MoreAction>, onDismiss: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            color = Aps3eColors.OnSurface,
            fontWeight = FontWeight.SemiBold
        )
        actions.forEach { action ->
            ActionCard(action = action, onDismiss = onDismiss)
        }
    }
}

@Composable
private fun ActionCard(action: MoreAction, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onDismiss()
                action.onClick()
            },
        colors = CardDefaults.cardColors(containerColor = Aps3eColors.CardBackground),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(action.accent)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = action.title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = action.subtitle,
                    color = Aps3eColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(id = R.string.empty_game_list),
            color = Aps3eColors.OnSurface,
            fontSize = 18.sp
        )
    }
}

@Composable
private fun ViewModeSelector(
    viewMode: ViewMode,
    showDetails: Boolean,
    lastPlayedInfo: LastPlayedInfo?,
    isRefreshingCovers: Boolean,
    onResumeLastPlayed: (LastPlayedInfo) -> Unit,
    onToggleDetails: () -> Unit,
    onRefreshCovers: () -> Unit,
    onToggleSearch: () -> Unit,
    onSelect: (ViewMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(id = R.string.select_game),
            color = Aps3eColors.OnSurface,
            fontWeight = FontWeight.SemiBold
        )
        if (lastPlayedInfo != null) {
            InlineLastPlayed(
                info = lastPlayedInfo,
                onResume = { onResumeLastPlayed(lastPlayedInfo) },
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Card(
                modifier = Modifier
                    .size(40.dp)
                    .clickable { onRefreshCovers() },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Aps3eColors.Surface)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_no_cover_placeholder),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Card(
                modifier = Modifier
                    .size(40.dp)
                    .clickable { onToggleSearch() },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Aps3eColors.Surface)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Aps3eColors.OnSurface
                    )
                }
            }
            Card(
                modifier = Modifier
                    .size(40.dp)
                    .clickable { onToggleDetails() },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Aps3eColors.Surface)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (showDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Aps3eColors.OnSurface
                    )
                }
            }
            Card(
                modifier = Modifier
                    .size(40.dp)
                    .clickable {
                        val next = when (viewMode) {
                            ViewMode.GRID -> ViewMode.LIST
                            ViewMode.LIST -> ViewMode.CAROUSEL
                            ViewMode.CAROUSEL -> ViewMode.GRID
                        }
                        onSelect(next)
                    },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Aps3eColors.Surface)
            ) {
                val iconRes = when (viewMode) {
                    ViewMode.GRID -> R.drawable.ic_grid_view
                    ViewMode.LIST -> R.drawable.ic_list_view
                    ViewMode.CAROUSEL -> R.drawable.ic_carousel_view
                }
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InlineLastPlayed(
    info: LastPlayedInfo,
    onResume: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Aps3eColors.Surface)
            .clickable { onResume() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = Aps3eColors.Accent,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(id = R.string.resume_from),
            fontSize = 11.sp,
            color = Aps3eColors.OnSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = info.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Aps3eColors.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .weight(1f)
                .basicMarquee()
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GameGrid(
    metas: List<Emulator.MetaInfo>,
    lastPlayedInfo: LastPlayedInfo?,
    lastPlayedCoverPath: String?,
    onResumeLastPlayed: (LastPlayedInfo) -> Unit,
    coverPaths: Map<String, String>,
    callbacks: MainScreenCallbacks,
    showDetails: Boolean
) {
    var cardScale by rememberSaveable { mutableStateOf(0.67f) }
    val entries = remember(metas, lastPlayedInfo, lastPlayedCoverPath) {
        val list = ArrayList<LibraryEntry>()
        if (lastPlayedInfo != null) {
            list.add(LibraryEntry.LastPlayed(lastPlayedInfo, lastPlayedCoverPath))
        }
        metas.forEach { meta ->
            list.add(LibraryEntry.Game(meta))
        }
        list
    }

    LazyVerticalGrid(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    cardScale = (cardScale * zoom).coerceIn(0.33f, 1f)
                }
            },
        columns = GridCells.Adaptive(minSize = (140.dp * cardScale)),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(
            items = entries,
            span = { _, entry ->
                if (entry is LibraryEntry.LastPlayed) GridItemSpan(maxLineSpan) else GridItemSpan(1)
            }
        ) { index, entry ->
            when (entry) {
                is LibraryEntry.LastPlayed -> {
                    LastPlayedCard(
                        info = entry.info,
                        coverPath = entry.coverPath,
                        onResume = onResumeLastPlayed
                    )
                }
                is LibraryEntry.Game -> {
                    val meta = entry.meta
                    val position = if (lastPlayedInfo != null) index - 1 else index
                    GameCard(
                        meta = meta,
                        coverPath = meta.getSerial()?.let { coverPaths[it] },
                        onClick = { callbacks.onGameClick(position) },
                        onAction = { actionId -> callbacks.onGameAction(actionId, position) },
                        isDiscGame = callbacks.isDiscGame(position),
                        sizeScale = cardScale,
                        showDetails = showDetails
                    )
                }
            }
        }
    }
}

@Composable
private fun GameList(
    metas: List<Emulator.MetaInfo>,
    lastPlayedInfo: LastPlayedInfo?,
    lastPlayedCoverPath: String?,
    onResumeLastPlayed: (LastPlayedInfo) -> Unit,
    coverPaths: Map<String, String>,
    callbacks: MainScreenCallbacks,
    showDetails: Boolean
) {
    val listState = rememberLazyListState()
    val grouped = remember(metas) {
        metas.groupBy { it.getName()?.firstOrNull()?.uppercaseChar() ?: '#' }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 52.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (lastPlayedInfo != null) {
                item {
                    LastPlayedCard(
                        info = lastPlayedInfo,
                        coverPath = lastPlayedCoverPath,
                        onResume = onResumeLastPlayed
                    )
                }
            }
            listItemsIndexed(metas) { index, meta ->
                GameListItem(
                    meta = meta,
                    coverPath = meta.getSerial()?.let { coverPaths[it] },
                    onClick = { callbacks.onGameClick(index) },
                    onAction = { actionId -> callbacks.onGameAction(actionId, index) },
                    isDiscGame = callbacks.isDiscGame(index),
                    showDetails = showDetails
                )
            }
        }
        val currentLetter = if (listState.firstVisibleItemIndex < metas.size) {
            metas[listState.firstVisibleItemIndex].getName()?.firstOrNull()?.uppercaseChar()
        } else null
        AlphabetScrollbar(
            letters = grouped.keys.toList(),
            currentLetter = currentLetter,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LastPlayedCard(
    info: LastPlayedInfo?,
    coverPath: String?,
    onResume: (LastPlayedInfo) -> Unit
) {
    if (info == null || info.name.isBlank()) return

    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Aps3eColors.CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                if (coverPath != null) {
                    AsyncImage(
                        model = coverPath,
                        contentDescription = info.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Aps3eColors.Surface)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = R.string.resume_from),
                    fontSize = 12.sp,
                    color = Aps3eColors.OnSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = info.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Aps3eColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee()
                )
            }
            IconButton(onClick = { onResume(info) }) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Aps3eColors.Accent)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GameCarousel(
    metas: List<Emulator.MetaInfo>,
    lastPlayedInfo: LastPlayedInfo?,
    lastPlayedCoverPath: String?,
    onResumeLastPlayed: (LastPlayedInfo) -> Unit,
    coverRepository: CoverRepository,
    gameDescriptions: MutableMap<String, String>,
    coverPaths: Map<String, String>,
    callbacks: MainScreenCallbacks,
    showDetails: Boolean
) {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val screenHeight = configuration.screenHeightDp.dp
    val carouselHeight = if (isPortrait) screenHeight * 0.58f else screenHeight * 0.72f

    if (isPortrait) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (lastPlayedInfo != null) {
                item {
                    LastPlayedCard(
                        info = lastPlayedInfo,
                        coverPath = lastPlayedCoverPath,
                        onResume = onResumeLastPlayed
                    )
                }
            }
            if (metas.isNotEmpty()) {
                item {
                    CarouselPager(
                        metas = metas,
                        coverPaths = coverPaths,
                        callbacks = callbacks,
                        showDetails = showDetails,
                        isPortrait = true,
                        height = carouselHeight,
                        coverRepository = coverRepository,
                        gameDescriptions = gameDescriptions
                    )
                }
            } else {
                item {
                    EmptyState()
                }
            }
        }
    } else {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val fittedHeight = maxHeight * 0.88f
            if (metas.isNotEmpty()) {
                CarouselPager(
                    metas = metas,
                    coverPaths = coverPaths,
                    callbacks = callbacks,
                    showDetails = showDetails,
                    isPortrait = false,
                    height = fittedHeight,
                    coverRepository = coverRepository,
                    gameDescriptions = gameDescriptions
                )
            } else {
                EmptyState()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CarouselPager(
    metas: List<Emulator.MetaInfo>,
    coverPaths: Map<String, String>,
    callbacks: MainScreenCallbacks,
    showDetails: Boolean,
    isPortrait: Boolean,
    height: Dp,
    coverRepository: CoverRepository,
    gameDescriptions: MutableMap<String, String>
) {
    val pagerState = rememberPagerState(pageCount = { metas.size })
    val pageWidth = if (isPortrait) height * COVER_ASPECT_RATIO else height * 1.6f
    val currentMeta = metas.getOrNull(pagerState.currentPage)
    val currentSerial = currentMeta?.getSerial()
    val currentName = currentMeta?.getName() ?: ""

    LaunchedEffect(isPortrait, currentSerial, currentName) {
        if (!isPortrait && currentSerial != null && currentName.isNotBlank()) {
            if (!gameDescriptions.containsKey(currentSerial)) {
                val description = coverRepository.getGameDescription(currentName)
                gameDescriptions[currentSerial] = description ?: ""
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.Center
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSize = PageSize.Fixed(pageWidth),
            contentPadding = PaddingValues(horizontal = if (isPortrait) 80.dp else 150.dp),
            pageSpacing = if (isPortrait) (-50).dp else (-100).dp
        ) { page ->
            val meta = metas[page]
            val serial = meta.getSerial()
            val description = if (!isPortrait && serial != null) {
                gameDescriptions[serial]?.takeIf { it.isNotBlank() }
            } else {
                null
            }
            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val absoluteOffset = pageOffset.absoluteValue

            val scale = 1f - (absoluteOffset * 0.15f).coerceIn(0f, 0.25f)
            val rotationY = (pageOffset * 30f).coerceIn(-45f, 45f)
            val alpha = 1f - (absoluteOffset * 0.4f).coerceIn(0f, 0.7f)

            val baseModifier = Modifier
                .height(height)
                .aspectRatio(if (isPortrait) COVER_ASPECT_RATIO else 1.6f)
                .graphicsLayer {
                    this.scaleX = scale
                    this.scaleY = scale
                    this.rotationY = rotationY
                    this.alpha = alpha
                    cameraDistance = 12f * density
                }

            if (isPortrait) {
                CarouselCardPortrait(
                    meta = meta,
                    coverPath = meta.getSerial()?.let { coverPaths[it] },
                    modifier = baseModifier,
                    isCenter = absoluteOffset < 0.5f,
                    onClick = { callbacks.onGameClick(page) },
                    onAction = { actionId -> callbacks.onGameAction(actionId, page) },
                    isDiscGame = callbacks.isDiscGame(page),
                    showDetails = showDetails
                )
            } else {
                CarouselCard(
                    meta = meta,
                    coverPath = meta.getSerial()?.let { coverPaths[it] },
                    modifier = baseModifier,
                    isCenter = absoluteOffset < 0.5f,
                    description = description,
                    onClick = { callbacks.onGameClick(page) },
                    onAction = { actionId -> callbacks.onGameAction(actionId, page) },
                    isDiscGame = callbacks.isDiscGame(page),
                    showDetails = showDetails
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CarouselCard(
    meta: Emulator.MetaInfo,
    coverPath: String?,
    modifier: Modifier,
    isCenter: Boolean,
    description: String?,
    onClick: () -> Unit,
    onAction: (Int) -> Unit,
    isDiscGame: Boolean,
    showDetails: Boolean
) {
    var showMenu by remember { mutableStateOf(false) }
    Card(
        modifier = modifier
            .combinedClickable(onClick = onClick, onLongClick = { showMenu = true }),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Aps3eColors.CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCenter) 12.dp else 4.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            val displayTitle = if (showDetails) {
                meta.getName() ?: ""
            } else {
                cleanedTitleForDisplay(meta.getName() ?: "")
            }
            val configuration = LocalConfiguration.current
            var useMarquee by remember(
                displayTitle,
                configuration.screenWidthDp,
                configuration.screenHeightDp
            ) {
                mutableStateOf(false)
            }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(0.79f)
            ) {
                GameCover(meta = meta, coverPath = coverPath, contentScale = ContentScale.Fit)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (useMarquee) {
                            Text(
                                text = displayTitle,
                                fontSize = if (isCenter) 18.sp else 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Aps3eColors.OnSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .basicMarquee()
                            )
                        } else {
                            Text(
                                text = displayTitle,
                                fontSize = if (isCenter) 18.sp else 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Aps3eColors.OnSurface,
                                maxLines = Int.MAX_VALUE,
                                overflow = TextOverflow.Clip,
                                modifier = Modifier.fillMaxWidth(),
                                onTextLayout = { layoutResult ->
                                    val overflow = layoutResult.hasVisualOverflow
                                    if (overflow != useMarquee) {
                                        useMarquee = overflow
                                    }
                                }
                            )
                        }
                        if (!description.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = description,
                                fontSize = 12.sp,
                                color = Aps3eColors.OnSurface.copy(alpha = 0.7f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (showDetails) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = meta.getSerial() ?: "",
                                fontSize = if (isCenter) 14.sp else 12.sp,
                                color = Aps3eColors.OnSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = null, tint = Aps3eColors.OnSurface)
                        }
                        GameActionMenu(
                            expanded = showMenu,
                            onDismiss = { showMenu = false },
                            isDiscGame = isDiscGame,
                            onAction = {
                                showMenu = false
                                onAction(it)
                            }
                        )
                    }
                }
                if (showDetails) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = meta.getVersion() ?: "",
                            fontSize = if (isCenter) 14.sp else 12.sp,
                            color = Aps3eColors.OnSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        StatusChip(text = if (meta.isDecrypt()) "Ready" else "Locked", ok = meta.isDecrypt())
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CarouselCardPortrait(
    meta: Emulator.MetaInfo,
    coverPath: String?,
    modifier: Modifier,
    isCenter: Boolean,
    onClick: () -> Unit,
    onAction: (Int) -> Unit,
    isDiscGame: Boolean,
    showDetails: Boolean
) {
    var showMenu by remember { mutableStateOf(false) }
    Card(
        modifier = modifier
            .combinedClickable(onClick = onClick, onLongClick = { showMenu = true }),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Aps3eColors.CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCenter) 12.dp else 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            val displayTitle = if (showDetails) {
                meta.getName() ?: ""
            } else {
                cleanedTitleForDisplay(meta.getName() ?: "")
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                GameCover(meta = meta, coverPath = coverPath)
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null, tint = Color.White)
                    }
                    GameActionMenu(
                        expanded = showMenu,
                        onDismiss = { showMenu = false },
                        isDiscGame = isDiscGame,
                        onAction = {
                            showMenu = false
                            onAction(it)
                        }
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = displayTitle,
                    fontSize = if (isCenter) 16.sp else 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Aps3eColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee()
                )
                if (showDetails) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = meta.getSerial() ?: "",
                        fontSize = 12.sp,
                        color = Aps3eColors.OnSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = meta.getVersion() ?: "",
                            fontSize = 12.sp,
                            color = Aps3eColors.OnSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        StatusChip(text = if (meta.isDecrypt()) "Ready" else "Locked", ok = meta.isDecrypt())
                    }
                }
            }
        }
    }
}

@Composable
private fun AlphabetScrollbar(
    letters: List<Char>,
    currentLetter: Char?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(end = 4.dp)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.Center
    ) {
        letters.forEach { letter ->
            val isActive = letter == currentLetter
            Box(
                modifier = Modifier
                    .size(if (isActive) 32.dp else 24.dp)
                    .offset(x = if (isActive) (-8).dp else 0.dp)
                    .background(
                        color = if (isActive) {
                            Aps3eColors.Primary.copy(alpha = 0.9f)
                        } else {
                            Aps3eColors.Primary.copy(alpha = 0.3f)
                        },
                        shape = CircleShape
                    )
                    .clickable { },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = letter.toString(),
                    fontSize = if (isActive) 14.sp else 10.sp,
                    fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                    color = Aps3eColors.OnBackground
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GameCard(
    meta: Emulator.MetaInfo,
    coverPath: String?,
    onClick: () -> Unit,
    onAction: (Int) -> Unit,
    isDiscGame: Boolean,
    sizeScale: Float,
    showDetails: Boolean
) {
    var showMenu by remember { mutableStateOf(false) }
    val titleSize = (14.sp.value * sizeScale.coerceIn(0.6f, 1f)).sp
    val subtitleSize = (12.sp.value * sizeScale.coerceIn(0.6f, 1f)).sp
    val displayTitle = if (showDetails) {
        meta.getName() ?: ""
    } else {
        cleanedTitleForDisplay(meta.getName() ?: "")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { showMenu = true })
            .shadow(8.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Aps3eColors.CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(COVER_ASPECT_RATIO)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                GameCover(meta = meta, coverPath = coverPath)
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null, tint = Color.White)
                    }
                    GameActionMenu(
                        expanded = showMenu,
                        onDismiss = { showMenu = false },
                        isDiscGame = isDiscGame,
                        onAction = {
                            showMenu = false
                            onAction(it)
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = displayTitle,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                fontSize = titleSize,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee()
            )
            if (showDetails) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = meta.getSerial() ?: "",
                    color = Aps3eColors.OnSurface,
                    fontSize = subtitleSize
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = meta.getVersion() ?: "",
                        color = Aps3eColors.OnSurface,
                        fontSize = subtitleSize
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    StatusChip(text = if (meta.isDecrypt()) "Ready" else "Locked", ok = meta.isDecrypt())
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GameListItem(
    meta: Emulator.MetaInfo,
    coverPath: String?,
    onClick: () -> Unit,
    onAction: (Int) -> Unit,
    isDiscGame: Boolean,
    showDetails: Boolean
) {
    var showMenu by remember { mutableStateOf(false) }
    val displayTitle = if (showDetails) {
        meta.getName() ?: ""
    } else {
        cleanedTitleForDisplay(meta.getName() ?: "")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .combinedClickable(onClick = onClick, onLongClick = { showMenu = true }),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Aps3eColors.CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(COVER_ASPECT_RATIO)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                GameCover(meta = meta, coverPath = coverPath)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayTitle,
                    color = Aps3eColors.OnSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee()
                )
                if (showDetails) {
                    Text(
                        text = meta.getSerial() ?: "",
                        color = Aps3eColors.OnSurface,
                        fontSize = 12.sp
                    )
                }
            }
            if (showDetails) {
                StatusChip(text = if (meta.isDecrypt()) "Ready" else "Locked", ok = meta.isDecrypt())
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = null, tint = Aps3eColors.OnSurface)
                }
                GameActionMenu(
                    expanded = showMenu,
                    onDismiss = { showMenu = false },
                    isDiscGame = isDiscGame,
                    onAction = {
                        showMenu = false
                        onAction(it)
                    }
                )
            }
        }
    }
}

@Composable
private fun GameCover(
    meta: Emulator.MetaInfo,
    coverPath: String?,
    contentScale: ContentScale = ContentScale.Fit
) {
    val context = LocalContext.current
    val iconBytes = meta.getIcon()

    if (coverPath == null && iconBytes == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Aps3eColors.Surface, Aps3eColors.CardBackground)
                    )
                )
        )
        return
    }

    val request = ImageRequest.Builder(context)
        .data(coverPath ?: iconBytes)
        .crossfade(true)
        .build()

    AsyncImage(
        model = request,
        contentDescription = meta.getName(),
        contentScale = contentScale,
        modifier = Modifier.fillMaxSize()
    )
}

private fun cleanedTitleForDisplay(rawTitle: String): String {
    if (rawTitle.isBlank()) return ""
    var cleaned = rawTitle
        .replace(Regex("[\\u2122\\u00AE\\u00A9]"), "")
        .replace(Regex("\\[[^\\]]*\\]"), " ")
        .replace(Regex("\\([^)]*\\)"), " ")
        .replace(Regex("(?i)\\b(?:EN|DE|IT|FR|ES|PT|RU|JA|KO|ZH)(?:[/_-](?:EN|DE|IT|FR|ES|PT|RU|JA|KO|ZH))+\\b"), " ")
        .replace(Regex("(?i)\\b[0-9]+\\.[0-9]+\\b"), " ")
        .replace(Regex("([A-Za-z])([0-9])"), "$1 $2")
        .replace(Regex("([0-9])([A-Za-z])"), "$1 $2")
        .replace(Regex("\\s+"), " ")
        .trim()

    if (cleaned.isBlank()) return ""

    val tokens = cleaned.split(" ")
    val filtered = tokens.filter { token ->
        if (token == "&") return@filter true
        val tokenKey = token.uppercase().replace(Regex("[^A-Z0-9]"), "")
        tokenKey.isNotBlank() && !DISPLAY_DROP_TOKENS.contains(tokenKey) && !isLocaleToken(token)
    }

    return filtered.joinToString(" ").replace(Regex("\\s+"), " ").trim()
}

private fun isLocaleToken(token: String): Boolean {
    val trimmed = token.trim()
    if (trimmed.isEmpty()) return false

    val hyphenMatch = Regex("^[A-Za-z]{2}[-_][A-Za-z]{2}$").matches(trimmed)
    if (hyphenMatch) {
        val parts = trimmed.split('-', '_')
        val lang = parts[0].uppercase()
        val region = parts[1].uppercase()
        return LANGUAGE_CODES.contains(lang) && REGION_CODES.contains(region)
    }

    if (trimmed.length == 4 && trimmed.all { it.isLetter() }) {
        val lang = trimmed.substring(0, 2).uppercase()
        val region = trimmed.substring(2, 4).uppercase()
        return LANGUAGE_CODES.contains(lang) && REGION_CODES.contains(region)
    }

    return false
}

private val DISPLAY_DROP_TOKENS = setOf(
    "PS3", "HDD", "HD", "FULL", "BUNDLE", "TRIAL", "DEMO", "DIGITAL",
    "EDITION", "ULTIMATE", "COMPLETE", "REMASTERED", "COLLECTION",
    "DELUXE", "GOTY", "DEFINITIVE",
    "EN", "DE", "IT", "FR", "ES", "PT", "RU", "JA", "KO", "ZH",
    "CN", "TW", "US", "EU", "JP", "KR", "BR", "PL", "TR", "AR",
    "NL", "SE", "NO", "DK", "FI", "GR", "HU", "CZ", "SK", "RO",
    "BG", "HR", "SL", "LT", "LV", "EE", "UA", "IL"
)

private val LANGUAGE_CODES = setOf(
    "EN", "DE", "IT", "FR", "ES", "PT", "RU", "JA", "KO", "ZH",
    "NL", "SV", "NO", "DA", "FI", "EL", "HU", "CS", "SK", "RO",
    "BG", "HR", "SL", "LT", "LV", "ET", "UK", "IL", "AR", "PL",
    "TR"
)

private val REGION_CODES = setOf(
    "CN", "TW", "US", "EU", "JP", "KR", "BR", "PL", "TR", "AR",
    "GB", "DE", "IT", "FR", "ES", "PT", "RU", "UA", "NL", "SE",
    "NO", "DK", "FI", "GR", "HU", "CZ", "SK", "RO", "BG", "HR",
    "SL", "LT", "LV", "EE", "IL"
)

@Composable
private fun StatusChip(text: String, ok: Boolean) {
    val background = if (ok) Aps3eColors.Accent else Aps3eColors.Warning
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(text = text, fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GameActionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    isDiscGame: Boolean,
    onAction: (Int) -> Unit
) {
    val items = if (isDiscGame) {
        listOf(
            R.id.delete_hdd0_install_data to R.string.delete_hdd0_install_data,
            R.id.delete_game_data to R.string.delete_game_data,
            R.id.delete_shaders_cache to R.string.delete_shaders_cache,
            R.id.edit_custom_config to R.string.edit_custom_config,
            R.id.create_shortcut to R.string.create_shortcut,
            R.id.show_game_info to R.string.show_game_info,
            R.id.show_trophy_info to R.string.show_trophy_info,
            R.id.delete_ppu_cache to R.string.delete_ppu_cache,
            R.id.delete_spu_cache to R.string.delete_spu_cache
        )
    } else {
        listOf(
            R.id.delete_game_and_data to R.string.delete_game_and_data,
            R.id.delete_game_data to R.string.delete_game_data,
            R.id.delete_shaders_cache to R.string.delete_shaders_cache,
            R.id.edit_custom_config to R.string.edit_custom_config,
            R.id.create_shortcut to R.string.create_shortcut,
            R.id.show_game_info to R.string.show_game_info,
            R.id.show_trophy_info to R.string.show_trophy_info,
            R.id.delete_ppu_cache to R.string.delete_ppu_cache,
            R.id.delete_spu_cache to R.string.delete_spu_cache
        )
    }

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        items.forEach { (id, label) ->
            DropdownMenuItem(
                text = { Text(text = stringResource(id = label)) },
                onClick = { onAction(id) }
            )
        }
    }
}
