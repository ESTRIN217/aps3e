package aenu.aps3e

import aenu.aps3e.ui.Aps3eColors
import aenu.aps3e.ui.Aps3eTheme
import aenu.emulator.Emulator as BaseEmulator
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import org.xmlpull.v1.XmlPullParser
import java.util.ArrayDeque
import kotlin.math.roundToInt

private const val NS_ANDROID = "http://schemas.android.com/apk/res/android"
private const val NS_APP = "http://schemas.android.com/apk/res-auto"

private sealed class SettingNode(open val key: String, open val titleResId: Int) {
    data class Screen(
        override val key: String,
        override val titleResId: Int,
        val children: MutableList<SettingNode> = mutableListOf()
    ) : SettingNode(key, titleResId)

    data class Toggle(
        override val key: String,
        override val titleResId: Int
    ) : SettingNode(key, titleResId)

    data class ListOption(
        override val key: String,
        override val titleResId: Int,
        val entriesResId: Int,
        val valuesResId: Int
    ) : SettingNode(key, titleResId)

    data class Range(
        override val key: String,
        override val titleResId: Int,
        val min: Int,
        val max: Int
    ) : SettingNode(key, titleResId)
}

private data class SettingsIndex(
    val root: SettingNode.Screen,
    val byKey: Map<String, SettingNode.Screen>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmulatorSettingsScreen(
    activity: EmulatorSettings,
    config: BaseEmulator.Config?,
    originalConfig: BaseEmulator.Config?,
    refreshTick: LiveData<Int>,
    isGlobal: Boolean,
    configPath: String?
) {
    var refreshVersion by remember { mutableStateOf(refreshTick.value ?: 0) }
    DisposableEffect(refreshTick) {
        val observer = Observer<Int> { value ->
            refreshVersion = value ?: 0
        }
        refreshTick.observeForever(observer)
        onDispose { refreshTick.removeObserver(observer) }
    }

    val context = LocalContext.current
    val settingsIndex = remember { buildSettingsIndex(context) }
    var selectedTab by remember { mutableStateOf(SettingsTab.General) }

    val generalStack = remember { mutableStateOf(listOf("__general_root__")) }
    val graphicsStack = remember { mutableStateOf(listOf("Video")) }

    val onBack = {
        val stack = if (selectedTab == SettingsTab.General) generalStack else graphicsStack
        if (stack.value.size > 1) {
            stack.value = stack.value.dropLast(1)
        } else {
            activity.finish()
        }
    }

    BackHandler { onBack() }

    Aps3eTheme {
        androidx.compose.material3.Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(id = R.string.settings),
                            color = Aps3eColors.OnSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = null,
                            tint = Aps3eColors.OnSurface,
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .clickable { onBack() }
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Aps3eColors.Surface,
                        titleContentColor = Aps3eColors.OnSurface
                    )
                )
            }
        ) { paddingValues ->
            if (config == null || originalConfig == null) {
                SettingsErrorState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
                return@Scaffold
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Aps3eColors.Background)
                    .padding(paddingValues)
            ) {
                TabRow(
                    selectedTabIndex = if (selectedTab == SettingsTab.General) 0 else 1,
                    containerColor = Aps3eColors.Surface,
                    contentColor = Aps3eColors.Primary
                ) {
                    Tab(
                        selected = selectedTab == SettingsTab.General,
                        onClick = { selectedTab = SettingsTab.General },
                        text = { Text(text = stringResource(id = R.string.emulator_settings_general)) },
                        icon = { Icon(imageVector = Icons.Filled.Settings, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == SettingsTab.Graphics,
                        onClick = { selectedTab = SettingsTab.Graphics },
                        text = { Text(text = stringResource(id = R.string.emulator_settings_video)) },
                        icon = { Icon(imageVector = Icons.Filled.MoreVert, contentDescription = null) }
                    )
                }

                when (selectedTab) {
                    SettingsTab.General -> {
                        GeneralSettingsContent(
                            settingsIndex = settingsIndex,
                            stack = generalStack.value,
                            onNavigate = { key ->
                                generalStack.value = generalStack.value + key
                            },
                            config = config,
                            originalConfig = originalConfig,
                            refreshVersion = refreshVersion,
                            onValueChange = { key, value ->
                                config.save_config_entry(key, value)
                                applyDependencies(config, key, value)
                                activity.bumpRefresh()
                            },
                            onSpecialAction = { actionKey ->
                                handleSpecialAction(activity, actionKey)
                            },
                            onResetDefault = {
                                activity.resetAsDefaultConfig()
                            },
                            onUseGlobal = {
                                activity.useGlobalConfig()
                            },
                            isGlobal = isGlobal,
                            supportsCustomDriver = Emulator.get.support_custom_driver(),
                            configPath = configPath
                        )
                    }
                    SettingsTab.Graphics -> {
                        GraphicsSettingsContent(
                            settingsIndex = settingsIndex,
                            stack = graphicsStack.value,
                            onNavigate = { key ->
                                graphicsStack.value = graphicsStack.value + key
                            },
                            config = config,
                            originalConfig = originalConfig,
                            refreshVersion = refreshVersion,
                            onValueChange = { key, value ->
                                config.save_config_entry(key, value)
                                applyDependencies(config, key, value)
                                activity.bumpRefresh()
                            },
                            onSpecialAction = { actionKey ->
                                handleSpecialAction(activity, actionKey)
                            },
                            supportsCustomDriver = Emulator.get.support_custom_driver()
                        )
                    }
                }
            }
        }
    }
}

private enum class SettingsTab {
    General,
    Graphics
}

@Composable
private fun SettingsErrorState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Aps3eColors.Background)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(id = R.string.msg_failed),
            color = Aps3eColors.OnSurface,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(id = R.string.invalid_config_file),
            color = Aps3eColors.OnSurface
        )
    }
}

@Composable
private fun GeneralSettingsContent(
    settingsIndex: SettingsIndex,
    stack: List<String>,
    onNavigate: (String) -> Unit,
    config: BaseEmulator.Config,
    originalConfig: BaseEmulator.Config,
    refreshVersion: Int,
    onValueChange: (String, String) -> Unit,
    onSpecialAction: (String) -> Unit,
    onResetDefault: () -> Unit,
    onUseGlobal: () -> Unit,
    isGlobal: Boolean,
    supportsCustomDriver: Boolean,
    configPath: String?
) {
    val currentKey = stack.last()
    if (currentKey == "__general_root__") {
        val sections = listOf(
            "Core",
            "Audio",
            "Input/Output",
            "System",
            "Savestate",
            "Miscellaneous"
        )
        SettingsCategoryList(
            title = stringResource(id = R.string.emulator_settings_general),
            sections = sections.mapNotNull { settingsIndex.byKey[it] },
            onNavigate = { onNavigate(it.key) },
            footer = {
                if (configPath != null) {
                    SettingsFooter(
                        isGlobal = isGlobal,
                        onResetDefault = onResetDefault,
                        onUseGlobal = onUseGlobal
                    )
                }
            }
        )
        return
    }

    val screen = settingsIndex.byKey[currentKey]
    if (screen == null) {
        SettingsCategoryList(
            title = stringResource(id = R.string.emulator_settings_general),
            sections = emptyList(),
            onNavigate = {}
        )
        return
    }

    SettingsScreenContent(
        screen = screen,
        config = config,
        originalConfig = originalConfig,
        refreshVersion = refreshVersion,
        onNavigate = onNavigate,
        onValueChange = onValueChange,
        onSpecialAction = onSpecialAction,
        supportsCustomDriver = supportsCustomDriver
    )
}

@Composable
private fun GraphicsSettingsContent(
    settingsIndex: SettingsIndex,
    stack: List<String>,
    onNavigate: (String) -> Unit,
    config: BaseEmulator.Config,
    originalConfig: BaseEmulator.Config,
    refreshVersion: Int,
    onValueChange: (String, String) -> Unit,
    onSpecialAction: (String) -> Unit,
    supportsCustomDriver: Boolean
) {
    val currentKey = stack.last()
    val screen = settingsIndex.byKey[currentKey]
    if (screen == null) {
        SettingsCategoryList(
            title = stringResource(id = R.string.emulator_settings_video),
            sections = emptyList(),
            onNavigate = {}
        )
        return
    }

    SettingsScreenContent(
        screen = screen,
        config = config,
        originalConfig = originalConfig,
        refreshVersion = refreshVersion,
        onNavigate = onNavigate,
        onValueChange = onValueChange,
        onSpecialAction = onSpecialAction,
        supportsCustomDriver = supportsCustomDriver
    )
}

@Composable
private fun SettingsCategoryList(
    title: String,
    sections: List<SettingNode.Screen>,
    onNavigate: (SettingNode.Screen) -> Unit,
    footer: @Composable (() -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = title,
            color = Aps3eColors.OnSurface,
            fontWeight = FontWeight.Bold
        )
        sections.forEach { screen ->
            NavigationCard(
                title = screenTitle(screen),
                onClick = { onNavigate(screen) }
            )
        }
        footer?.invoke()
    }
}

@Composable
private fun SettingsFooter(
    isGlobal: Boolean,
    onResetDefault: () -> Unit,
    onUseGlobal: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Aps3eColors.CardBackground),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(id = R.string.reset_as_default),
                color = Aps3eColors.OnSurface,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onResetDefault) {
                    Text(text = stringResource(id = R.string.reset_as_default))
                }
                if (!isGlobal) {
                    Button(onClick = onUseGlobal) {
                        Text(text = stringResource(id = R.string.use_global_config))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreenContent(
    screen: SettingNode.Screen,
    config: BaseEmulator.Config,
    originalConfig: BaseEmulator.Config,
    refreshVersion: Int,
    onNavigate: (String) -> Unit,
    onValueChange: (String, String) -> Unit,
    onSpecialAction: (String) -> Unit,
    supportsCustomDriver: Boolean
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = screenTitle(screen),
            color = Aps3eColors.OnSurface,
            fontWeight = FontWeight.Bold
        )

        screen.children.forEach { node ->
            when (node) {
                is SettingNode.Screen -> {
                    val canNavigate = node.children.isNotEmpty()
                    val canAct = hasSpecialAction(node.key)
                    val enabled = isSettingEnabled(node.key, config, supportsCustomDriver) && (canNavigate || canAct)
                    NavigationRow(
                        title = screenTitle(node),
                        enabled = enabled,
                        onClick = {
                            if (canNavigate) {
                                onNavigate(node.key)
                            } else if (canAct) {
                                onSpecialAction(node.key)
                            }
                        }
                    )
                }
                is SettingNode.Toggle -> {
                    ToggleRow(
                        title = screenTitle(node),
                        key = node.key,
                        config = config,
                        originalConfig = originalConfig,
                        enabled = isSettingEnabled(node.key, config, supportsCustomDriver),
                        refreshVersion = refreshVersion,
                        onValueChange = onValueChange
                    )
                }
                is SettingNode.ListOption -> {
                    ListRow(
                        title = screenTitle(node),
                        node = node,
                        config = config,
                        originalConfig = originalConfig,
                        enabled = isSettingEnabled(node.key, config, supportsCustomDriver),
                        refreshVersion = refreshVersion,
                        onValueChange = onValueChange
                    )
                }
                is SettingNode.Range -> {
                    RangeRow(
                        title = screenTitle(node),
                        node = node,
                        config = config,
                        originalConfig = originalConfig,
                        enabled = isSettingEnabled(node.key, config, supportsCustomDriver),
                        refreshVersion = refreshVersion,
                        onValueChange = onValueChange
                    )
                }
            }
            Divider(color = Aps3eColors.Surface)
        }
    }
}

@Composable
private fun NavigationCard(title: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Aps3eColors.CardBackground),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Aps3eColors.OnSurface
            )
        }
    }
}

@Composable
private fun NavigationRow(title: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = if (enabled) Aps3eColors.OnSurface else Aps3eColors.OnSurface.copy(alpha = 0.4f)
        )
        Icon(
            imageVector = Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = if (enabled) Aps3eColors.OnSurface else Aps3eColors.OnSurface.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    key: String,
    config: BaseEmulator.Config,
    originalConfig: BaseEmulator.Config,
    enabled: Boolean,
    refreshVersion: Int,
    onValueChange: (String, String) -> Unit
) {
    val current = remember(refreshVersion) {
        config.load_config_entry(key)?.toBoolean() ?: false
    }
    val modified = isModified(originalConfig, key, current.toString())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (enabled) Aps3eColors.OnSurface else Aps3eColors.OnSurface.copy(alpha = 0.4f),
                fontWeight = if (modified) FontWeight.SemiBold else FontWeight.Normal
            )
            if (modified) {
                Text(
                    text = stringResource(id = R.string.modified),
                    color = Aps3eColors.Accent,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        Switch(
            checked = current,
            onCheckedChange = { checked ->
                if (enabled) onValueChange(key, checked.toString())
            },
            enabled = enabled
        )
    }
}

@Composable
private fun ListRow(
    title: String,
    node: SettingNode.ListOption,
    config: BaseEmulator.Config,
    originalConfig: BaseEmulator.Config,
    enabled: Boolean,
    refreshVersion: Int,
    onValueChange: (String, String) -> Unit
) {
    val entries = stringArrayResource(id = node.entriesResId)
    val values = stringArrayResource(id = node.valuesResId)
    val currentValue = remember(refreshVersion) {
        config.load_config_entry(node.key) ?: values.firstOrNull().orEmpty()
    }
    val currentIndex = values.indexOf(currentValue).coerceAtLeast(0)
    val currentLabel = entries.getOrNull(currentIndex) ?: currentValue
    val modified = isModified(originalConfig, node.key, currentValue)
    var dialogOpen by remember { mutableStateOf(false) }

    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text(text = title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    entries.forEachIndexed { index, label ->
                        TextButton(
                            onClick = {
                                dialogOpen = false
                                onValueChange(node.key, values.getOrNull(index) ?: currentValue)
                            }
                        ) {
                            Text(text = label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dialogOpen = false }) {
                    Text(text = stringResource(id = android.R.string.cancel))
                }
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { dialogOpen = true }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (enabled) Aps3eColors.OnSurface else Aps3eColors.OnSurface.copy(alpha = 0.4f),
                fontWeight = if (modified) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(
                text = currentLabel,
                color = Aps3eColors.OnSurface.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = if (enabled) Aps3eColors.OnSurface else Aps3eColors.OnSurface.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun RangeRow(
    title: String,
    node: SettingNode.Range,
    config: BaseEmulator.Config,
    originalConfig: BaseEmulator.Config,
    enabled: Boolean,
    refreshVersion: Int,
    onValueChange: (String, String) -> Unit
) {
    val currentValue = remember(refreshVersion) {
        config.load_config_entry(node.key)?.toIntOrNull() ?: node.min
    }
    val modified = isModified(originalConfig, node.key, currentValue.toString())
    val useSlider = node.max - node.min <= 200

    if (useSlider) {
        SliderRow(
            title = title,
            currentValue = currentValue,
            min = node.min,
            max = node.max,
            enabled = enabled,
            modified = modified,
            onValueChange = { newValue ->
                onValueChange(node.key, newValue.toString())
            }
        )
    } else {
        NumberPickerRow(
            title = title,
            currentValue = currentValue,
            min = node.min,
            max = node.max,
            enabled = enabled,
            modified = modified,
            onValueChange = { newValue ->
                onValueChange(node.key, newValue.toString())
            }
        )
    }
}

@Composable
private fun SliderRow(
    title: String,
    currentValue: Int,
    min: Int,
    max: Int,
    enabled: Boolean,
    modified: Boolean,
    onValueChange: (Int) -> Unit
) {
    var sliderValue by remember(currentValue) { mutableStateOf(currentValue.toFloat()) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (enabled) Aps3eColors.OnSurface else Aps3eColors.OnSurface.copy(alpha = 0.4f),
                    fontWeight = if (modified) FontWeight.SemiBold else FontWeight.Normal
                )
                Text(
                    text = currentValue.toString(),
                    color = Aps3eColors.OnSurface.copy(alpha = 0.7f)
                )
            }
        }
        androidx.compose.material3.Slider(
            value = sliderValue,
            onValueChange = { value ->
                if (enabled) {
                    sliderValue = value
                    onValueChange(value.roundToInt())
                }
            },
            valueRange = min.toFloat()..max.toFloat(),
            enabled = enabled
        )
    }
}

@Composable
private fun NumberPickerRow(
    title: String,
    currentValue: Int,
    min: Int,
    max: Int,
    enabled: Boolean,
    modified: Boolean,
    onValueChange: (Int) -> Unit
) {
    var dialogOpen by remember { mutableStateOf(false) }
    var inputValue by remember(currentValue) { mutableStateOf(currentValue.toString()) }
    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text(text = title) },
            text = {
                OutlinedTextField(
                    value = inputValue,
                    onValueChange = { inputValue = it },
                    label = { Text(text = stringResource(id = R.string.value)) }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parsed = inputValue.toIntOrNull()
                        if (parsed != null) {
                            val clamped = parsed.coerceIn(min, max)
                            onValueChange(clamped)
                        }
                        dialogOpen = false
                    }
                ) {
                    Text(text = stringResource(id = android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { dialogOpen = false }) {
                    Text(text = stringResource(id = android.R.string.cancel))
                }
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) {
                inputValue = currentValue.toString()
                dialogOpen = true
            }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (enabled) Aps3eColors.OnSurface else Aps3eColors.OnSurface.copy(alpha = 0.4f),
                fontWeight = if (modified) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(
                text = currentValue.toString(),
                color = Aps3eColors.OnSurface.copy(alpha = 0.7f)
            )
        }
        Icon(
            imageVector = Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = if (enabled) Aps3eColors.OnSurface else Aps3eColors.OnSurface.copy(alpha = 0.4f)
        )
    }
}

private fun isModified(originalConfig: BaseEmulator.Config, key: String, value: String): Boolean {
    val original = originalConfig.load_config_entry(key) ?: return false
    return original != value
}

private fun isSettingEnabled(key: String, config: BaseEmulator.Config, supportsCustomDriver: Boolean): Boolean {
    if (!supportsCustomDriver && (key == EmulatorSettings.`Video$Vulkan$Use_Custom_Driver` ||
            key == EmulatorSettings.`Video$Vulkan$Custom_Driver_Library_Path` ||
            key == "Video|Vulkan|Custom Driver Force Max Clocks")) {
        return false
    }

    return when (key) {
        EmulatorSettings.`Video$Resolution_Scale` -> {
            val strict = config.load_config_entry(EmulatorSettings.`Video$Strict_Rendering_Mode`)?.toBoolean() ?: false
            !strict
        }
        EmulatorSettings.`Video$Read_Color_Buffers`, EmulatorSettings.`Video$Write_Color_Buffers` -> {
            val bgra = config.load_config_entry(EmulatorSettings.`Video$Use_BGRA_Format`)?.toBoolean() ?: false
            bgra
        }
        EmulatorSettings.`Video$Vulkan$Asynchronous_Queue_Scheduler` -> {
            val async = config.load_config_entry(EmulatorSettings.`Video$Vulkan$Asynchronous_Texture_Streaming_2`)?.toBoolean() ?: false
            async
        }
        EmulatorSettings.`Video$Vulkan$Custom_Driver_Library_Path` -> {
            val custom = config.load_config_entry(EmulatorSettings.`Video$Vulkan$Use_Custom_Driver`)?.toBoolean() ?: false
            custom
        }
        EmulatorSettings.`Miscellaneous$Custom_Font_File_Path` -> {
            val selection = config.load_config_entry(EmulatorSettings.`Miscellaneous$Font_File_Selection`).orEmpty()
            selection.equals("Custom", ignoreCase = true)
        }
        EmulatorSettings.`Core$Thread_Affinity_Mask` -> {
            val mode = config.load_config_entry(EmulatorSettings.`Core$Thread_Scheduler_Mode`).orEmpty()
            mode.equals("Affinity", ignoreCase = true)
        }
        else -> true
    }
}

private fun applyDependencies(config: BaseEmulator.Config, key: String, value: String) {
    when (key) {
        EmulatorSettings.`Video$Strict_Rendering_Mode` -> {
            val strict = value.toBoolean()
            if (strict) {
                config.save_config_entry(EmulatorSettings.`Video$Resolution_Scale`, "100")
            }
        }
        EmulatorSettings.`Video$Use_BGRA_Format` -> {
            val bgra = value.toBoolean()
            if (!bgra) {
                config.save_config_entry(EmulatorSettings.`Video$Read_Color_Buffers`, "false")
                config.save_config_entry(EmulatorSettings.`Video$Write_Color_Buffers`, "false")
            }
        }
        EmulatorSettings.`Video$Vulkan$Asynchronous_Texture_Streaming_2` -> {
            // Enable state handled by isSettingEnabled.
        }
        EmulatorSettings.`Video$Vulkan$Use_Custom_Driver` -> {
            if (!value.toBoolean()) {
                // Keep path but disable selection.
            }
        }
        EmulatorSettings.`Miscellaneous$Font_File_Selection` -> {
            // Enable state handled by isSettingEnabled.
        }
        EmulatorSettings.`Core$Thread_Scheduler_Mode` -> {
            // Enable state handled by isSettingEnabled.
        }
    }
}

private fun handleSpecialAction(activity: EmulatorSettings, key: String) {
    when (key) {
        EmulatorSettings.`Core$Use_LLVM_CPU` -> activity.showSelectLlvmCpuList()
        "Video|Vulkan|Adapter" -> activity.showSelectVulkanAdapterList()
        EmulatorSettings.`Video$Vulkan$Custom_Driver_Library_Path` -> activity.showSelectCustomDriverList()
        EmulatorSettings.`Miscellaneous$Custom_Font_File_Path` -> activity.showSelectFontFileList()
        EmulatorSettings.`Core$Thread_Affinity_Mask` -> activity.showAffinityMaskView()
        "Core|Libraries Control" -> activity.showLibraryControlView()
    }
}

private fun hasSpecialAction(key: String): Boolean {
    return when (key) {
        EmulatorSettings.`Core$Use_LLVM_CPU`,
        "Video|Vulkan|Adapter",
        EmulatorSettings.`Video$Vulkan$Custom_Driver_Library_Path`,
        EmulatorSettings.`Miscellaneous$Custom_Font_File_Path`,
        EmulatorSettings.`Core$Thread_Affinity_Mask`,
        "Core|Libraries Control" -> true
        else -> false
    }
}

private fun buildSettingsIndex(context: android.content.Context): SettingsIndex {
    val parser = context.resources.getXml(R.xml.emulator_settings)
    val stack = ArrayDeque<SettingNode.Screen>()
    var root: SettingNode.Screen? = null
    val byKey = mutableMapOf<String, SettingNode.Screen>()

    var eventType = parser.eventType
    while (eventType != XmlPullParser.END_DOCUMENT) {
        if (eventType == XmlPullParser.START_TAG) {
            val name = parser.name
            when (name) {
                "PreferenceScreen" -> {
                    val key = parser.getAttributeValue(NS_APP, "key") ?: ""
                    val titleResId = parser.getAttributeResourceValue(NS_APP, "title", 0)
                    val screen = SettingNode.Screen(key, titleResId)
                    if (stack.isEmpty()) {
                        root = screen
                    } else {
                        stack.last().children.add(screen)
                    }
                    stack.add(screen)
                    if (key.isNotBlank()) {
                        byKey[key] = screen
                    }
                }
                "aenu.preference.CheckBoxPreference" -> {
                    val key = parser.getAttributeValue(NS_APP, "key") ?: ""
                    val titleResId = parser.getAttributeResourceValue(NS_APP, "title", 0)
                    stack.lastOrNull()?.children?.add(SettingNode.Toggle(key, titleResId))
                }
                "aenu.preference.ListPreference" -> {
                    val key = parser.getAttributeValue(NS_APP, "key") ?: ""
                    val titleResId = parser.getAttributeResourceValue(NS_APP, "title", 0)
                    val entriesResId = parser.getAttributeResourceValue(NS_APP, "entries", 0)
                    val valuesResId = parser.getAttributeResourceValue(NS_APP, "entryValues", 0)
                    stack.lastOrNull()?.children?.add(
                        SettingNode.ListOption(key, titleResId, entriesResId, valuesResId)
                    )
                }
                "aenu.preference.SeekBarPreference" -> {
                    val key = parser.getAttributeValue(NS_APP, "key") ?: ""
                    val titleResId = parser.getAttributeResourceValue(NS_APP, "title", 0)
                    val min = parser.getAttributeIntValue(NS_APP, "min", 0)
                    val max = parser.getAttributeIntValue(NS_ANDROID, "max", min)
                    stack.lastOrNull()?.children?.add(
                        SettingNode.Range(key, titleResId, min, max)
                    )
                }
            }
        } else if (eventType == XmlPullParser.END_TAG) {
            if (parser.name == "PreferenceScreen") {
                if(stack.isNotEmpty()){
                    stack.removeLast()
                }
            }
        }
        eventType = parser.next()
    }

    val resolvedRoot = root ?: SettingNode.Screen("", 0)
    return SettingsIndex(resolvedRoot, byKey)
}

@Composable
private fun screenTitle(node: SettingNode): String {
    return if (node.titleResId != 0) {
        stringResource(id = node.titleResId)
    } else {
        node.key
    }
}
