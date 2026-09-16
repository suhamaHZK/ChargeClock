package com.kmmm_engineering.chargeclock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kmmm_engineering.chargeclock.R
import com.kmmm_engineering.chargeclock.data.AppLanguage
import com.kmmm_engineering.chargeclock.data.DateFormatOption
import com.kmmm_engineering.chargeclock.data.DisplayScale
import com.kmmm_engineering.chargeclock.data.LandscapeMode
import com.kmmm_engineering.chargeclock.data.MonthFormatOption
import com.kmmm_engineering.chargeclock.data.SettingsRepository
import com.kmmm_engineering.chargeclock.data.TextColorOption
import com.kmmm_engineering.chargeclock.data.UserSettings
import com.kmmm_engineering.chargeclock.data.WeekdayFormatOption
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    repository: SettingsRepository,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var webhookDraft by remember(settings.discordWebhookUrl) {
        mutableStateOf(settings.discordWebhookUrl)
    }

    Scaffold(
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
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
                onValueChange = { v -> scope.launch { repository.setIdleBrightness(v) } },
                valueRange = 0.01f..0.35f,
            )

            SectionTitle(stringResource(R.string.text_color))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextColorOption.entries.forEach { opt ->
                    val selected = settings.textColor == opt
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(opt.argb))
                            .then(
                                if (selected) Modifier.border(2.dp, Color.White, CircleShape)
                                else Modifier.border(1.dp, Color(0xFF444444), CircleShape)
                            )
                            .clickable { scope.launch { repository.setTextColor(opt) } },
                    )
                }
            }

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

            SectionTitle(stringResource(R.string.discord_discharge))
            ThresholdChips(settings.discordDischargeThreshold) { v ->
                scope.launch { repository.setDiscordDischarge(v) }
            }

            SectionTitle(stringResource(R.string.discord_charge))
            ThresholdChips(settings.discordChargeThreshold) { v ->
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
            ChipRow {
                LangChip("YYYY/MM/DD", settings.dateFormat == DateFormatOption.YMD) {
                    scope.launch { repository.setDateFormat(DateFormatOption.YMD) }
                }
                LangChip("MM/DD/YYYY", settings.dateFormat == DateFormatOption.MDY) {
                    scope.launch { repository.setDateFormat(DateFormatOption.MDY) }
                }
                LangChip("DD/MM/YYYY", settings.dateFormat == DateFormatOption.DMY) {
                    scope.launch { repository.setDateFormat(DateFormatOption.DMY) }
                }
            }

            SectionTitle(stringResource(R.string.month_format))
            ChipRow {
                LangChip(stringResource(R.string.month_numeric), settings.monthFormat == MonthFormatOption.NUMERIC) {
                    scope.launch { repository.setMonthFormat(MonthFormatOption.NUMERIC) }
                }
                LangChip(stringResource(R.string.month_abbr), settings.monthFormat == MonthFormatOption.ABBR) {
                    scope.launch { repository.setMonthFormat(MonthFormatOption.ABBR) }
                }
            }

            SectionTitle(stringResource(R.string.weekday_format))
            ChipRow {
                LangChip(stringResource(R.string.weekday_en), settings.weekdayFormat == WeekdayFormatOption.EN) {
                    scope.launch { repository.setWeekdayFormat(WeekdayFormatOption.EN) }
                }
                LangChip(stringResource(R.string.weekday_ja), settings.weekdayFormat == WeekdayFormatOption.JA) {
                    scope.launch { repository.setWeekdayFormat(WeekdayFormatOption.JA) }
                }
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

            Spacer(Modifier.height(32.dp))
        }
    }
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

@Composable
private fun ThresholdChips(current: Int, onSelect: (Int) -> Unit) {
    val options = listOf(-1) + (5..100 step 5).toList()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.chunked(6).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { v ->
                    val label = if (v < 0) stringResource(R.string.threshold_off) else "$v%"
                    FilterChip(
                        selected = current == v,
                        onClick = { onSelect(v) },
                        label = { Text(label) },
                    )
                }
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
