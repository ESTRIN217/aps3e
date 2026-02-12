package aenu.aps3e

import aenu.aps3e.ui.Aps3eColors
import aenu.aps3e.ui.Aps3eTheme
import aenu.emulator.Emulator as BaseEmulator
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import java.io.File

interface QuickStartCallbacks {
    fun onInstallFirmware()
    fun onSelectIsoDir()
    fun onSelectCustomFont()
    fun onSelectCustomDriver()
    fun onFinish()
    fun onQuit()
    fun onRefresh()
}

data class QuickStep(
    val id: String,
    val title: String,
    val description: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickStartScreen(
    activity: QuickStartActivity,
    config: BaseEmulator.Config?,
    refreshTick: LiveData<Int>,
    callbacks: QuickStartCallbacks
) {
    var refreshVersion by remember { mutableStateOf(refreshTick.value ?: 0) }
    DisposableEffect(refreshTick) {
        val observer = Observer<Int> { value ->
            refreshVersion = value ?: 0
        }
        refreshTick.observeForever(observer)
        onDispose { refreshTick.removeObserver(observer) }
    }

    val supportsVulkan = Application.device_support_vulkan()
    val supportsCustomDriver = Emulator.get.support_custom_driver()

    val steps = buildList {
        add(
            QuickStep(
                id = "welcome",
                title = stringResource(id = R.string.welcome),
                description = stringResource(id = R.string.welcome_content, stringResource(id = R.string.app_name))
            )
        )
        add(
            QuickStep(
                id = "firmware",
                title = stringResource(id = R.string.install_firmware),
                description = stringResource(id = R.string.install_ps3_firmware_hiht_content)
            )
        )
        add(
            QuickStep(
                id = "iso",
                title = stringResource(id = R.string.set_iso_dir),
                description = stringResource(id = R.string.select_ps3_iso_dir_hiht_content)
            )
        )
        add(
            QuickStep(
                id = "font",
                title = stringResource(id = R.string.emulator_settings_miscellaneous_custom_font_file_path_dialog_title),
                description = stringResource(id = R.string.font_select_hiht_content)
            )
        )
        if (supportsCustomDriver) {
            add(
                QuickStep(
                    id = "driver",
                    title = stringResource(id = R.string.emulator_settings_video_vulkan_custom_driver_library_path_dialog_title),
                    description = stringResource(id = R.string.select_gpu_driver_hiht_content)
                )
            )
        }
        add(
            QuickStep(
                id = "config",
                title = stringResource(id = R.string.settings),
                description = stringResource(id = R.string.modify_config_hiht_content)
            )
        )
    }

    var pageIndex by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(pageIndex) {
        if (steps.getOrNull(pageIndex)?.id == "config") {
            activity.apply_config_fixes()
        }
    }

    Aps3eTheme {
        androidx.compose.material3.Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(id = R.string.quick_start_page),
                            color = Aps3eColors.OnSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Aps3eColors.Surface,
                        titleContentColor = Aps3eColors.OnSurface
                    )
                )
            }
        ) { paddingValues ->
            if (!supportsVulkan) {
                VulkanUnsupportedScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    onQuit = callbacks::onQuit
                )
                return@Scaffold
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Aps3eColors.Background)
                    .padding(paddingValues)
            ) {
                StepProgressHeader(
                    current = pageIndex + 1,
                    total = steps.size,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )

                val currentStep = steps[pageIndex]
                StepContent(
                    step = currentStep,
                    config = config,
                    refreshVersion = refreshVersion,
                    onInstallFirmware = callbacks::onInstallFirmware,
                    onSelectIsoDir = callbacks::onSelectIsoDir,
                    onSelectCustomFont = callbacks::onSelectCustomFont,
                    onSelectCustomDriver = callbacks::onSelectCustomDriver,
                    onRefresh = callbacks::onRefresh
                )

                Spacer(modifier = Modifier.weight(1f))

                BottomNavBar(
                    pageIndex = pageIndex,
                    totalPages = steps.size,
                    isNextEnabled = isNextEnabled(currentStep.id, config),
                    onPrev = { if (pageIndex > 0) pageIndex -= 1 },
                    onNext = {
                        if (pageIndex == steps.size - 1) {
                            callbacks.onFinish()
                        } else {
                            pageIndex += 1
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun StepProgressHeader(current: Int, total: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = "${current} / ${total}",
            color = Aps3eColors.OnSurface,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = current.toFloat() / total.toFloat(),
            color = Aps3eColors.Primary,
            trackColor = Aps3eColors.Surface
        )
    }
}

@Composable
private fun StepContent(
    step: QuickStep,
    config: BaseEmulator.Config?,
    refreshVersion: Int,
    onInstallFirmware: () -> Unit,
    onSelectIsoDir: () -> Unit,
    onSelectCustomFont: () -> Unit,
    onSelectCustomDriver: () -> Unit,
    onRefresh: () -> Unit
) {
    val scrollState = rememberScrollState()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .verticalScroll(scrollState),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Aps3eColors.CardBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = step.title,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = step.description,
                color = Aps3eColors.OnSurface
            )

            when (step.id) {
                "welcome" -> {
                    Text(
                        text = stringResource(id = R.string.welcome_content2, stringResource(id = R.string.next_step)),
                        color = Aps3eColors.OnSurface
                    )
                }
                "firmware" -> {
                    val installed = MainActivity.firmware_installed_file().exists()
                    Button(onClick = onInstallFirmware) {
                        Text(text = stringResource(id = if (installed) R.string.ps3_firmware_installed else R.string.select_ps3_firmware))
                    }
                }
                "iso" -> {
                    val hasIso = MainActivity.load_pref_iso_dir(LocalContext.current) != null
                    Button(onClick = onSelectIsoDir) {
                        Text(text = stringResource(id = if (hasIso) R.string.ps3_iso_dir_is_set else R.string.set_iso_dir))
                    }
                }
                "font" -> {
                    FontSelectionSection(config = config, refreshVersion = refreshVersion, onSelectCustomFont = onSelectCustomFont, onRefresh = onRefresh)
                }
                "driver" -> {
                    DriverSelectionSection(config = config, refreshVersion = refreshVersion, onSelectCustomDriver = onSelectCustomDriver, onRefresh = onRefresh)
                }
                "config" -> {
                    Text(
                        text = stringResource(id = R.string.modify_config_hiht_content),
                        color = Aps3eColors.OnSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun FontSelectionSection(
    config: BaseEmulator.Config?,
    refreshVersion: Int,
    onSelectCustomFont: () -> Unit,
    onRefresh: () -> Unit
) {
    val entries = stringArrayResource(id = R.array.miscellaneous_font_file_selection_entries)
    val values = stringArrayResource(id = R.array.miscellaneous_font_file_selection_values)
    val currentSelection = config?.load_config_entry(EmulatorSettings.`Miscellaneous$Font_File_Selection`).orEmpty()
    val resolvedSelection = if (currentSelection.isBlank()) values.getOrNull(1) ?: "" else currentSelection

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        entries.forEachIndexed { index, label ->
            val value = values.getOrNull(index) ?: ""
            SelectionRow(
                label = label,
                selected = value == resolvedSelection,
                onSelect = {
                    config?.save_config_entry(EmulatorSettings.`Miscellaneous$Font_File_Selection`, value)
                    onRefresh()
                }
            )
        }

        val fontPath = config?.load_config_entry(EmulatorSettings.`Miscellaneous$Custom_Font_File_Path`).orEmpty()
        Text(
            text = stringResource(id = R.string.custom_font_path_prefix) + fontPath,
            color = Aps3eColors.OnSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        val files = Application.get_custom_font_dir().listFiles()?.toList().orEmpty()
        val items = buildList {
            files.forEach { add(it.name) }
            add(stringResource(id = R.string.emulator_settings_miscellaneous_custom_font_file_path_dialog_add_hint))
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEachIndexed { index, name ->
                SelectionCard(
                    label = name,
                    onClick = {
                        if (index == items.lastIndex) {
                            onSelectCustomFont()
                        } else {
                            val fontFile = File(Application.get_custom_font_dir(), name)
                            config?.save_config_entry(EmulatorSettings.`Miscellaneous$Custom_Font_File_Path`, fontFile.absolutePath)
                            onRefresh()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun DriverSelectionSection(
    config: BaseEmulator.Config?,
    refreshVersion: Int,
    onSelectCustomDriver: () -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val enabled = config?.load_config_entry(EmulatorSettings.`Video$Vulkan$Use_Custom_Driver`)?.toBoolean() ?: false

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Checkbox(
            checked = enabled,
            onCheckedChange = { checked ->
                config?.save_config_entry(EmulatorSettings.`Video$Vulkan$Use_Custom_Driver`, checked.toString())
                onRefresh()
            }
        )
        Text(text = stringResource(id = R.string.emulator_settings_video_vulkan_use_custom_driver), color = Aps3eColors.OnSurface)
    }

    val driverPath = config?.load_config_entry(EmulatorSettings.`Video$Vulkan$Custom_Driver_Library_Path`).orEmpty()
    Text(
        text = stringResource(id = R.string.custom_gpu_driver_path_prefix) + driverPath,
        color = Aps3eColors.OnSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )

    if (enabled) {
        val items = buildDriverList(context)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEachIndexed { index, name ->
                SelectionCard(
                    label = name,
                    onClick = {
                        if (index == items.lastIndex) {
                            onSelectCustomDriver()
                        } else {
                            val driverFile = File(Application.get_custom_driver_dir(), name)
                            config?.save_config_entry(EmulatorSettings.`Video$Vulkan$Custom_Driver_Library_Path`, driverFile.absolutePath)
                            onRefresh()
                        }
                    }
                )
            }
        }
    }
}

private fun buildDriverList(context: android.content.Context): List<String> {
    val list = mutableListOf<String>()
    val files = Application.get_custom_driver_dir().listFiles()
    if (files == null || files.isEmpty()) {
        list.add(context.getString(R.string.emulator_settings_video_vulkan_custom_driver_library_path_dialog_add_hint))
        return list
    }

    files.forEach { file ->
        if (file.isFile) {
            list.add(file.name)
        } else {
            val subFiles = file.listFiles()
            if (subFiles != null && subFiles.size == 1) {
                list.add(file.name + "/" + subFiles[0].name)
            } else {
                val jsonFile = File(file, "meta.json")
                if (jsonFile.exists()) {
                    try {
                        val json = org.json.JSONObject(Utils.read_file_as_str(jsonFile))
                        list.add(file.name + "/" + json.getString("libraryName"))
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    list.add(context.getString(R.string.emulator_settings_video_vulkan_custom_driver_library_path_dialog_add_hint))
    return list
}

@Composable
private fun SelectionRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .border(1.dp, Aps3eColors.OnSurface, RoundedCornerShape(7.dp))
                .background(if (selected) Aps3eColors.Primary else Color.Transparent, RoundedCornerShape(7.dp))
        )
        Text(text = label, color = Aps3eColors.OnSurface)
    }
}

@Composable
private fun SelectionCard(label: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Aps3eColors.Surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = label,
            color = Aps3eColors.OnSurface,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun BottomNavBar(
    pageIndex: Int,
    totalPages: Int,
    isNextEnabled: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Divider(color = Aps3eColors.Surface)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(onClick = onPrev, enabled = pageIndex > 0) {
            Text(text = stringResource(id = R.string.prev_step))
        }
        Button(onClick = onNext, enabled = isNextEnabled) {
            Text(text = stringResource(id = if (pageIndex == totalPages - 1) R.string.finish else R.string.next_step))
        }
    }
}

@Composable
private fun VulkanUnsupportedScreen(modifier: Modifier = Modifier, onQuit: () -> Unit) {
    Column(
        modifier = modifier
            .background(Aps3eColors.Background)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(id = R.string.device_unsupport_vulkan_msg),
            color = Aps3eColors.OnSurface
        )
        Button(onClick = onQuit) {
            Text(text = stringResource(id = R.string.quit))
        }
    }
}

@Composable
private fun isNextEnabled(stepId: String, config: BaseEmulator.Config?): Boolean {
    return when (stepId) {
        "firmware" -> MainActivity.firmware_installed_file().exists()
        "font" -> {
            val values = stringArrayResource(id = R.array.miscellaneous_font_file_selection_values)
            val selection = config?.load_config_entry(EmulatorSettings.`Miscellaneous$Font_File_Selection`).orEmpty()
            val path = config?.load_config_entry(EmulatorSettings.`Miscellaneous$Custom_Font_File_Path`).orEmpty()
            val customSelection = values.getOrNull(1) ?: ""
            !(selection == customSelection && path.isBlank())
        }
        "driver" -> {
            val enabled = config?.load_config_entry(EmulatorSettings.`Video$Vulkan$Use_Custom_Driver`)?.toBoolean() ?: false
            val path = config?.load_config_entry(EmulatorSettings.`Video$Vulkan$Custom_Driver_Library_Path`).orEmpty()
            !(enabled && path.isBlank())
        }
        else -> true
    }
}
