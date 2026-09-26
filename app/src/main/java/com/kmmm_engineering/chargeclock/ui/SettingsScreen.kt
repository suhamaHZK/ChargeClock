package com.kmmm_engineering.chargeclock.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kmmm_engineering.chargeclock.BuildConfig
import com.kmmm_engineering.chargeclock.R
import com.kmmm_engineering.chargeclock.data.AppLanguage
import com.kmmm_engineering.chargeclock.data.ClockTheme
import com.kmmm_engineering.chargeclock.data.DateFormatOption
import com.kmmm_engineering.chargeclock.data.DisplayScale
import com.kmmm_engineering.chargeclock.data.LandscapeMode
import com.kmmm_engineering.chargeclock.data.MonthFormatOption
import com.kmmm_engineering.chargeclock.data.SettingsRepository
import com.kmmm_engineering.chargeclock.data.UserSettings
import com.kmmm_engineering.chargeclock.data.WeekdayFormatOption
import com.kmmm_engineering.chargeclock.discord.DiscordNotifier
import com.kmmm_engineering.chargeclock.util.DiscordIconSaver
import com.kmmm_engineering.chargeclock.widget.ClockWidgetCompactProvider
import com.kmmm_engineering.chargeclock.widget.ClockWidgetFullProvider
import com.larswerkman.holocolorpicker.ColorPicker
import com.larswerkman.holocolorpicker.SVBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    repository: SettingsRepository,
    onBack: () -> Unit,
    onIdleBrightnessPreview: (Float?) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showWidgetSizePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val iconOkMsg = stringResource(R.string.discord_icon_save_ok)
    val iconFailMsg = stringResource(R.string.discord_icon_save_fail)
    val widgetUnsupportedMsg = stringResource(R.string.widget_pin_unsupported)
    var webhookDraft by remember(settings.discordWebhookUrl) {
        mutableStateOf(settings.discordWebhookUrl)
    }
    var testSending by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    val testEmptyMsg = stringResource(R.string.discord_test_empty)
    val testOkMsg = stringResource(R.string.discord_test_ok)
    val testFailMsg = stringResource(R.string.discord_test_fail)
    val testContent = stringResource(R.string.discord_test_msg)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121212),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
        containerColor = Color.Black,
    ) { padding ->
        // Landscape: keep content in the middle ~80% width (≈10% unused each side).
        // Scroll on the full-width parent so side-margin drags also scroll.
        val isLandscape =
            LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.8f else 1f)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(stringResource(R.string.language))
            ChipRow {
                LangChip(stringResource(R.string.language_system), settings.language == AppLanguage.SYSTEM) {
                    scope.launch { repository.setLanguage(AppLanguage.SYSTEM) }
                }
                LangChip(stringResource(R.string.language_en), settings.language == AppLanguage.ENGLISH) {
                    scope.launch { repository.setLanguage(AppLanguage.ENGLISH) }
                }
                LangChip(stringResource(R.string.language_ja), settings.language == AppLanguage.JAPANESE) {
                    scope.launch { repository.setLanguage(AppLanguage.JAPANESE) }
                }
            }

            SectionTitle(stringResource(R.string.idle_brightness))
            Text(
                text = "%.0f%%".format(settings.idleBrightness * 100),
                color = Color(0xFFAAAAAA),
            )
            Slider(
                value = settings.idleBrightness,
                onValueChange = { v ->
                    onIdleBrightnessPreview(v)
                    scope.launch { repository.setIdleBrightness(v) }
                },
                onValueChangeFinished = {
                    onIdleBrightnessPreview(null)
                },
                valueRange = 0.01f..0.35f,
            )

            SectionTitle(stringResource(R.string.clock_theme))
            val themeDefault = stringResource(R.string.clock_theme_default)
            val themeClassic = stringResource(R.string.clock_theme_classic_digital)
            val themeSeg14 = stringResource(R.string.clock_theme_seg14_digital)
            SimpleDropdown(
                options = listOf(
                    ClockTheme.DEFAULT to themeDefault,
                    ClockTheme.CLASSIC_DIGITAL to themeClassic,
                    ClockTheme.SEG14_DIGITAL to themeSeg14,
                ),
                selected = settings.clockTheme,
                labelOf = { opt ->
                    when (opt) {
                        ClockTheme.DEFAULT -> themeDefault
                        ClockTheme.CLASSIC_DIGITAL -> themeClassic
                        ClockTheme.SEG14_DIGITAL -> themeSeg14
                    }
                },
                onSelect = { opt -> scope.launch { repository.setClockTheme(opt) } },
            )

            SectionTitle(stringResource(R.string.text_color))
            TextColorPresetRow(
                selectedArgb = settings.textColorArgb,
                onPreset = { argb -> scope.launch { repository.setTextColorArgb(argb) } },
                onOpenPicker = { showColorPicker = true },
            )

            SectionTitle(stringResource(R.string.discord_webhook))
            OutlinedTextField(
                value = webhookDraft,
                onValueChange = {
                    webhookDraft = it
                    scope.launch { repository.setDiscordWebhookUrl(it.trim()) }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("https://discord.com/api/webhooks/…") },
            )
            Button(
                onClick = {
                    val url = webhookDraft.trim().ifEmpty { settings.discordWebhookUrl.trim() }
                    if (url.isEmpty()) {
                        scope.launch { snackbarHostState.showSnackbar(testEmptyMsg) }
                        return@Button
                    }
                    if (testSending) return@Button
                    testSending = true
                    scope.launch {
                        val ok = DiscordNotifier.send(url, testContent)
                        snackbarHostState.showSnackbar(if (ok) testOkMsg else testFailMsg)
                        testSending = false
                    }
                },
                enabled = !testSending,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.discord_test_send))
            }
            Button(
                onClick = {
                    scope.launch {
                        val ok = DiscordIconSaver.saveToGallery(context)
                        snackbarHostState.showSnackbar(if (ok) iconOkMsg else iconFailMsg)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.discord_icon_get))
            }

            SectionTitle(stringResource(R.string.discord_discharge))
            ThresholdDropdown(settings.discordDischargeThreshold) { v ->
                scope.launch { repository.setDiscordDischarge(v) }
            }

            SectionTitle(stringResource(R.string.discord_charge))
            ThresholdDropdown(settings.discordChargeThreshold) { v ->
                scope.launch { repository.setDiscordCharge(v) }
            }

            SectionTitle(stringResource(R.string.force_landscape))
            ChipRow {
                LangChip(stringResource(R.string.landscape_off), settings.landscapeMode == LandscapeMode.OFF) {
                    scope.launch { repository.setLandscape(LandscapeMode.OFF) }
                }
                LangChip(stringResource(R.string.landscape_right), settings.landscapeMode == LandscapeMode.RIGHT) {
                    scope.launch { repository.setLandscape(LandscapeMode.RIGHT) }
                }
                LangChip(stringResource(R.string.landscape_left), settings.landscapeMode == LandscapeMode.LEFT) {
                    scope.launch { repository.setLandscape(LandscapeMode.LEFT) }
                }
            }

            SectionTitle(stringResource(R.string.display_scale))
            ChipRow {
                LangChip(stringResource(R.string.scale_small), settings.displayScale == DisplayScale.SMALL) {
                    scope.launch { repository.setDisplayScale(DisplayScale.SMALL) }
                }
                LangChip(stringResource(R.string.scale_normal), settings.displayScale == DisplayScale.NORMAL) {
                    scope.launch { repository.setDisplayScale(DisplayScale.NORMAL) }
                }
                LangChip(stringResource(R.string.scale_large), settings.displayScale == DisplayScale.LARGE) {
                    scope.launch { repository.setDisplayScale(DisplayScale.LARGE) }
                }
                LangChip(stringResource(R.string.scale_xlarge), settings.displayScale == DisplayScale.XLARGE) {
                    scope.launch { repository.setDisplayScale(DisplayScale.XLARGE) }
                }
            }

            SectionTitle(stringResource(R.string.time_format))
            ChipRow {
                LangChip(stringResource(R.string.time_24), settings.use24Hour) {
                    scope.launch { repository.setUse24Hour(true) }
                }
                LangChip(stringResource(R.string.time_12), !settings.use24Hour) {
                    scope.launch { repository.setUse24Hour(false) }
                }
            }

            SwitchRow(
                label = stringResource(R.string.show_seconds),
                checked = settings.showSeconds,
                onCheckedChange = { scope.launch { repository.setShowSeconds(it) } },
            )

            SectionTitle(stringResource(R.string.date_format))
            SimpleDropdown(
                options = listOf(
                    DateFormatOption.YMD to "YYYY/MM/DD",
                    DateFormatOption.MDY to "MM/DD/YYYY",
                    DateFormatOption.DMY to "DD/MM/YYYY",
                ),
                selected = settings.dateFormat,
                labelOf = { opt ->
                    when (opt) {
                        DateFormatOption.YMD -> "YYYY/MM/DD"
                        DateFormatOption.MDY -> "MM/DD/YYYY"
                        DateFormatOption.DMY -> "DD/MM/YYYY"
                    }
                },
                onSelect = { opt -> scope.launch { repository.setDateFormat(opt) } },
            )

            SectionTitle(stringResource(R.string.month_format))
            ChipRow {
                LangChip(stringResource(R.string.month_numeric), settings.monthFormat == MonthFormatOption.NUMERIC) {
                    scope.launch { repository.setMonthFormat(MonthFormatOption.NUMERIC) }
                }
                LangChip(stringResource(R.string.month_abbr), settings.monthFormat == MonthFormatOption.ABBR) {
                    scope.launch { repository.setMonthFormat(MonthFormatOption.ABBR) }
                }
            }

            if (!settings.forcesEnglishWeekday) {
                SectionTitle(stringResource(R.string.weekday_format))
                val weekdayEn = stringResource(R.string.weekday_en)
                val weekdayJa = stringResource(R.string.weekday_ja)
                val weekdayTw = stringResource(R.string.weekday_tw)
                val weekdayCn = stringResource(R.string.weekday_cn)
                SimpleDropdown(
                    options = listOf(
                        WeekdayFormatOption.EN to weekdayEn,
                        WeekdayFormatOption.JA to weekdayJa,
                        WeekdayFormatOption.TW to weekdayTw,
                        WeekdayFormatOption.CN to weekdayCn,
                    ),
                    selected = settings.weekdayFormat,
                    labelOf = { opt ->
                        when (opt) {
                            WeekdayFormatOption.EN -> weekdayEn
                            WeekdayFormatOption.JA -> weekdayJa
                            WeekdayFormatOption.TW -> weekdayTw
                            WeekdayFormatOption.CN -> weekdayCn
                        }
                    },
                    onSelect = { opt -> scope.launch { repository.setWeekdayFormat(opt) } },
                )
            }

            SwitchRow(
                label = stringResource(R.string.exit_on_unplug),
                checked = settings.exitOnUnplug,
                onCheckedChange = { scope.launch { repository.setExitOnUnplug(it) } },
            )

            SwitchRow(
                label = stringResource(R.string.allow_auto_sleep),
                checked = settings.allowAutoSleep,
                onCheckedChange = { scope.launch { repository.setAllowAutoSleep(it) } },
            )

            SectionTitle(stringResource(R.string.widget_section))
            Button(
                onClick = { showWidgetSizePicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.widget_pin))
            }

            SectionTitle(stringResource(R.string.credits_oss))
            Text(
                text = stringResource(R.string.credits_holocolorpicker),
                color = Color(0xFFAAAAAA),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.credits_dseg),
                color = Color(0xFFAAAAAA),
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(Modifier.height(8.dp))
            Text(
                text = if (BuildConfig.APP_REV.isNotEmpty()) {
                    "${BuildConfig.VERSION_NAME} rev${BuildConfig.APP_REV}"
                } else {
                    BuildConfig.VERSION_NAME
                },
                color = Color(0xFF888888),
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(Modifier.height(32.dp))
        }
        } // outer full-width scroll (landscape side margins scroll too)
    }

    if (showColorPicker) {
        HoloColorPickerDialog(
            initialArgb = settings.textColorArgb,
            onDismiss = { showColorPicker = false },
            onConfirm = { argb ->
                scope.launch { repository.setTextColorArgb(argb) }
                showColorPicker = false
            },
        )
    }


    if (showWidgetSizePicker) {
        AlertDialog(
            onDismissRequest = { showWidgetSizePicker = false },
            title = { Text(stringResource(R.string.widget_pin_pick_title)) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showWidgetSizePicker = false
                            requestPinWidget(context, ClockWidgetCompactProvider::class.java) {
                                scope.launch { snackbarHostState.showSnackbar(widgetUnsupportedMsg) }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.widget_pin_compact))
                    }
                    TextButton(
                        onClick = {
                            showWidgetSizePicker = false
                            requestPinWidget(context, ClockWidgetFullProvider::class.java) {
                                scope.launch { snackbarHostState.showSnackbar(widgetUnsupportedMsg) }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.widget_pin_full))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWidgetSizePicker = false }) {
                    Text(stringResource(R.string.color_picker_cancel))
                }
            },
            containerColor = Color(0xFF1E1E1E),
            titleContentColor = Color.White,
            textContentColor = Color.White,
        )
    }
}

/** Historical preset palette (v0.1.11); values written as free ARGB. */
private val TextColorPresetArgb: List<Long> = listOf(
    0xFFFFFFFFL, // WHITE
    0xFFFFC107L, // AMBER
    0xFF4CAF50L, // GREEN
    0xFF00BCD4L, // CYAN
    0xFFE91E63L, // PINK
    0xFFFF9800L, // ORANGE
)

@Composable
private fun TextColorPresetRow(
    selectedArgb: Long,
    onPreset: (Long) -> Unit,
    onOpenPicker: () -> Unit,
) {
    val matchedPreset = TextColorPresetArgb.firstOrNull { it == selectedArgb }
    val pickerSelected = matchedPreset == null
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        TextColorPresetArgb.forEach { argb ->
            val selected = matchedPreset == argb
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .then(
                        if (selected) Modifier.border(2.dp, Color.White, CircleShape)
                        else Modifier.border(1.dp, Color(0xFF444444), CircleShape),
                    )
                    .clickable { onPreset(argb) },
            )
        }
        TextColorPickerButton(
            selectedArgb = selectedArgb,
            selected = pickerSelected,
            onClick = onOpenPicker,
        )
    }
}

/**
 * Donut wheel icon with a Compose circle overlay showing the current text color.
 * Center radius ≈ 125/256 of the icon (matches ColorPicker.svg).
 * When [selected] (custom color, no preset match), draws a white ring.
 */
@Composable
private fun TextColorPickerButton(
    selectedArgb: Long,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val iconSize = 40.dp
    // SVG center circle r=125 in 512 viewport → diameter fraction 250/512
    val centerSize = iconSize * (250f / 512f)
    Box(
        modifier = Modifier
            .size(iconSize)
            .then(
                if (selected) Modifier.border(2.dp, Color.White, CircleShape)
                else Modifier,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_color_picker_wheel),
            contentDescription = stringResource(R.string.text_color),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
        Box(
            modifier = Modifier
                .size(centerSize)
                .clip(CircleShape)
                .background(Color(selectedArgb))
                .border(1.dp, Color(0xFF444444), CircleShape),
        )
    }
}

@Composable
private fun HoloColorPickerDialog(
    initialArgb: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val initialInt = ((initialArgb and 0xFFFFFFFFL).toInt() and 0x00FFFFFF) or android.graphics.Color.BLACK
    // Holder so confirm can read the latest ColorPicker without Compose state writes from the View factory.
    val pickerHolder = remember { arrayOfNulls<ColorPicker>(1) }
    val configuration = LocalConfiguration.current
    val layoutDirection = LocalLayoutDirection.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Natural size of landscape XML: wheel (radius+pointerHalo)*2 beside vertical SVBar.
    // Matches dialog_holo_color_picker_land.xml + HoloColorPicker 1.5 defaults.
    val wheelNatural = 228.dp // 2 * (96dp wheel radius + 18dp pointer halo)
    val barNaturalW = 28.dp // 2 * 14dp bar pointer halo
    val barNaturalH = 228.dp // 200dp bar_length + 2 * 14dp halo
    val rowPad = 8.dp // XML padding 4dp × 2
    val barStartMargin = 12.dp
    val needW = rowPad + wheelNatural + barStartMargin + barNaturalW
    val needH = rowPad + maxOf(wheelNatural, barNaturalH)

    // Available dialog content area: screen − system bars − dialog padding − title/button chrome.
    val sysBars = WindowInsets.systemBars.asPaddingValues()
    val dialogHInset = 48.dp // dialog side margins + text padding
    val dialogVChrome = 172.dp // title row + button row + vertical paddings
    val availW = configuration.screenWidthDp.dp -
        sysBars.calculateLeftPadding(layoutDirection) -
        sysBars.calculateRightPadding(layoutDirection) -
        dialogHInset
    val availH = configuration.screenHeightDp.dp -
        sysBars.calculateTopPadding() -
        sysBars.calculateBottomPadding() -
        dialogVChrome

    val scale = if (isLandscape) {
        minOf(
            1f,
            (availW / needW).coerceAtLeast(0.01f),
            (availH / needH).coerceAtLeast(0.01f),
        )
    } else {
        1f
    }
    val scaledW = needW * scale
    val scaledH = needH * scale

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.text_color)) },
        text = {
            // Primary: shrink wheel|SVBar to fit. Secondary: light scroll if chrome estimate is tight.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isLandscape) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                AndroidView(
                    factory = { context ->
                        if (context.resources.configuration.orientation ==
                            Configuration.ORIENTATION_LANDSCAPE
                        ) {
                            // Side-by-side: wheel | vertical SVBar (XML sets bar_orientation_horizontal=false).
                            val root = LayoutInflater.from(context)
                                .inflate(R.layout.dialog_holo_color_picker_land, null, false)
                            val picker = root.findViewById<ColorPicker>(R.id.color_picker)
                            val svBar = root.findViewById<SVBar>(R.id.sv_bar)
                            picker.addSVBar(svBar)
                            picker.setShowOldCenterColor(false)
                            picker.setColor(initialInt)
                            pickerHolder[0] = picker
                            root
                        } else {
                            LinearLayout(context).apply {
                                orientation = LinearLayout.VERTICAL
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                )
                                val picker = ColorPicker(context).apply {
                                    layoutParams = LinearLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT,
                                    )
                                }
                                val svBar = SVBar(context).apply {
                                    layoutParams = LinearLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ).also {
                                        it.topMargin = (8 * resources.displayMetrics.density).toInt()
                                    }
                                }
                                picker.addSVBar(svBar)
                                picker.setShowOldCenterColor(false)
                                picker.setColor(initialInt)
                                pickerHolder[0] = picker
                                addView(picker)
                                addView(svBar)
                            }
                        }
                    },
                    modifier = if (isLandscape) {
                        // Constrain to scaled natural size; ColorPicker/SVBar onMeasure shrink to fit.
                        Modifier.width(scaledW).height(scaledH)
                    } else {
                        Modifier.fillMaxWidth()
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val fromPicker = pickerHolder[0]?.color ?: initialInt
                    val rgb = (fromPicker and 0x00FFFFFF) or android.graphics.Color.BLACK
                    onConfirm(rgb.toLong() and 0xFFFFFFFFL)
                },
            ) {
                Text(stringResource(R.string.color_picker_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.color_picker_cancel))
            }
        },
        containerColor = Color(0xFF1E1E1E),
        titleContentColor = Color.White,
        textContentColor = Color.White,
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = Color(0xFFCCCCCC),
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        content()
    }
}

@Composable
private fun LangChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThresholdDropdown(current: Int, onSelect: (Int) -> Unit) {
    val offLabel = stringResource(R.string.threshold_off)
    val options = remember { listOf(-1) + (5..100 step 5).toList() }
    val labelOf: (Int) -> String = { v -> if (v < 0) offLabel else "$v%" }
    SimpleDropdown(
        options = options.map { it to labelOf(it) },
        selected = current,
        labelOf = { labelOf(it) },
        onSelect = onSelect,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SimpleDropdown(
    options: List<Pair<T, String>>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = labelOf(selected),
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}


private fun requestPinWidget(
    context: android.content.Context,
    provider: Class<*>,
    onUnsupported: () -> Unit,
) {
    val mgr = AppWidgetManager.getInstance(context)
    if (!mgr.isRequestPinAppWidgetSupported) {
        onUnsupported()
        return
    }
    val ok = mgr.requestPinAppWidget(ComponentName(context, provider), null, null)
    if (!ok) onUnsupported()
}
