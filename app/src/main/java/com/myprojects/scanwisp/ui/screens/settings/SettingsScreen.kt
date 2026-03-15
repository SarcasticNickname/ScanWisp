package com.myprojects.scanwisp.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.myprojects.scanwisp.R
import com.myprojects.scanwisp.domain.model.AppError
import com.myprojects.scanwisp.domain.model.OcrLanguage
import com.myprojects.scanwisp.domain.model.PdfExportProfile
import com.myprojects.scanwisp.domain.model.ThemePreference
import com.myprojects.scanwisp.ui.components.ErrorDialog
import com.myprojects.scanwisp.ui.components.ErrorState
import com.myprojects.scanwisp.ui.events.UiEvent
import com.myprojects.scanwisp.ui.screens.home.components.ScanWispBottomAppBar
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showPdfProfileDialog by remember { mutableStateOf(false) }
    var showOcrLanguageDialog by remember { mutableStateOf(false) }

    var errorToShowInDialog by remember { mutableStateOf<AppError?>(null) }

    LaunchedEffect(key1 = true) {
        viewModel.uiEventFlow.collectLatest { event ->
            if (event is UiEvent.ShowErrorDialog) {
                errorToShowInDialog = event.error
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_cd_back)
                        )
                    }
                }
            )
        },
        bottomBar = {
            ScanWispBottomAppBar(navController = navController)
        }
    ) { innerPadding ->
        when (val state = uiState) {
            SettingsUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is SettingsUiState.Success -> {
                val settings = state.settings
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // --- Основные ---
                    SettingsCategory(stringResource(R.string.settings_category_main))
                    SettingsItem(
                        icon = Icons.Outlined.BrightnessMedium,
                        title = stringResource(R.string.settings_item_theme),
                        subtitle = settings.themePreference.toReadableString(),
                        onClick = { showThemeDialog = true }
                    )
                    SettingsItem(
                        icon = Icons.Outlined.FileDownload,
                        title = stringResource(R.string.settings_item_pdf_quality),
                        subtitle = settings.pdfExportProfile.toReadableString(),
                        onClick = { showPdfProfileDialog = true }
                    )
                    SettingsSwitchItem(
                        icon = Icons.Outlined.AspectRatio,
                        title = stringResource(R.string.settings_item_fit_to_a4),
                        subtitle = stringResource(R.string.settings_item_fit_to_a4_desc),
                        checked = settings.fitToA4,
                        onCheckedChange = viewModel::onFitToA4Changed
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // --- Распознавание текста ---
                    SettingsCategory(stringResource(R.string.settings_category_ocr))
                    SettingsItem(
                        icon = Icons.Outlined.Translate,
                        title = stringResource(R.string.settings_item_ocr_language),
                        subtitle = settings.defaultOcrLanguage.toReadableString(),
                        onClick = { showOcrLanguageDialog = true }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // --- О приложении ---
                    SettingsCategory(stringResource(R.string.settings_category_about))
                    SettingsItem(
                        icon = Icons.Outlined.Info,
                        title = stringResource(R.string.settings_item_version),
                        subtitle = stringResource(R.string.settings_item_version_number)
                    )
                }

                // Диалог темы
                if (showThemeDialog) {
                    SettingsRadioDialog(
                        title = stringResource(R.string.settings_dialog_title_theme),
                        options = ThemePreference.entries.toList(),
                        selectedOption = settings.themePreference,
                        onOptionSelected = {
                            viewModel.onThemeSelected(it); showThemeDialog = false
                        },
                        onDismissRequest = { showThemeDialog = false },
                        optionToReadableString = { it.toReadableString() }
                    )
                }

                // Диалог качества PDF
                if (showPdfProfileDialog) {
                    SettingsRadioDialog(
                        title = stringResource(R.string.settings_dialog_title_pdf),
                        options = PdfExportProfile.entries.toList(),
                        selectedOption = settings.pdfExportProfile,
                        onOptionSelected = {
                            viewModel.onProfileSelected(it); showPdfProfileDialog = false
                        },
                        onDismissRequest = { showPdfProfileDialog = false },
                        optionToReadableString = { it.toReadableString() },
                        optionToDescription = { it.getDescription() }
                    )
                }

                // Диалог языка OCR
                if (showOcrLanguageDialog) {
                    SettingsRadioDialog(
                        title = stringResource(R.string.settings_dialog_title_ocr_language),
                        options = OcrLanguage.entries.toList(),
                        selectedOption = settings.defaultOcrLanguage,
                        onOptionSelected = {
                            viewModel.onOcrLanguageSelected(it); showOcrLanguageDialog = false
                        },
                        onDismissRequest = { showOcrLanguageDialog = false },
                        optionToReadableString = { it.toReadableString() }
                    )
                }
            }

            is SettingsUiState.Error -> {
                ErrorState(
                    error = state.error,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
        }
    }

    errorToShowInDialog?.let { error ->
        ErrorDialog(
            error = error,
            onDismiss = { errorToShowInDialog = null }
        )
    }
}

// ─── Компоненты ──────────────────────────────────────────────────────────────

@Composable
private fun SettingsCategory(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = { onCheckedChange(!checked) })
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
private fun <T> SettingsRadioDialog(
    title: String,
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    onDismissRequest: () -> Unit,
    optionToReadableString: @Composable (T) -> String,
    optionToDescription: @Composable ((T) -> String)? = null
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 8.dp)
                )
                Column(Modifier.selectableGroup()) {
                    options.forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = (option == selectedOption),
                                    onClick = { onOptionSelected(option) },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (option == selectedOption),
                                onClick = null,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                            Column(Modifier.padding(start = 16.dp)) {
                                Text(text = optionToReadableString(option))
                                optionToDescription?.let {
                                    Text(
                                        text = it(option),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Extension functions ──────────────────────────────────────────────────────

@Composable
private fun ThemePreference.toReadableString(): String = when (this) {
    ThemePreference.SYSTEM -> stringResource(R.string.settings_theme_system)
    ThemePreference.BORDEAUX_LIGHT -> stringResource(R.string.settings_theme_bordeaux_light)
    ThemePreference.BORDEAUX_DARK -> stringResource(R.string.settings_theme_bordeaux_dark)
    ThemePreference.GREEN_LIGHT -> stringResource(R.string.settings_theme_green_light)
    ThemePreference.GREEN_DARK -> stringResource(R.string.settings_theme_green_dark)
    ThemePreference.BLUE_LIGHT -> stringResource(R.string.settings_theme_blue_light)
    ThemePreference.BLUE_DARK -> stringResource(R.string.settings_theme_blue_dark)
}

@Composable
private fun PdfExportProfile.toReadableString(): String = when (this) {
    PdfExportProfile.SMALL -> stringResource(R.string.settings_pdf_quality_small)
    PdfExportProfile.BALANCED -> stringResource(R.string.settings_pdf_quality_balanced)
    PdfExportProfile.HIGH -> stringResource(R.string.settings_pdf_quality_high)
}

@Composable
private fun PdfExportProfile.getDescription(): String = when (this) {
    PdfExportProfile.SMALL -> stringResource(R.string.settings_pdf_quality_small_desc)
    PdfExportProfile.BALANCED -> stringResource(R.string.settings_pdf_quality_balanced_desc)
    PdfExportProfile.HIGH -> stringResource(R.string.settings_pdf_quality_high_desc)
}

@Composable
private fun OcrLanguage.toReadableString(): String = when (this) {
    OcrLanguage.RUSSIAN -> stringResource(R.string.ocr_language_russian)
    OcrLanguage.ENGLISH -> stringResource(R.string.ocr_language_english)
    OcrLanguage.RUSSIAN_ENGLISH -> stringResource(R.string.ocr_language_both)
}