package com.myprojects.scanwisp.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.myprojects.scanwisp.R
import com.myprojects.scanwisp.domain.model.PdfExportProfile
import com.myprojects.scanwisp.domain.model.ThemePreference
import com.myprojects.scanwisp.ui.screens.home.components.ScanWispBottomAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val currentProfile by viewModel.pdfExportProfile.collectAsState()
    val currentTheme by viewModel.themePreference.collectAsState()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showPdfProfileDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
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
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            SettingsCategory(stringResource(R.string.settings_category_main))
            SettingsItem(
                icon = Icons.Outlined.BrightnessMedium,
                title = stringResource(R.string.settings_item_theme),
                subtitle = currentTheme?.toReadableString() ?: stringResource(R.string.loading),
                onClick = if (currentTheme != null) {
                    { showThemeDialog = true }
                } else null
            )
            SettingsItem(
                icon = Icons.Outlined.FileDownload,
                title = stringResource(R.string.settings_item_pdf_quality),
                subtitle = currentProfile?.toReadableString() ?: stringResource(R.string.loading),
                onClick = if (currentProfile != null) {
                    { showPdfProfileDialog = true }
                } else null
            )
            Divider(modifier = Modifier.padding(horizontal = 16.dp))
            SettingsCategory(stringResource(R.string.settings_category_about))
            SettingsItem(
                icon = Icons.Outlined.Info,
                title = stringResource(R.string.settings_item_version),
                subtitle = stringResource(R.string.settings_item_version_number)
            )
        }

        if (showThemeDialog && currentTheme != null) {
            SettingsRadioDialog(
                title = stringResource(R.string.settings_dialog_title_theme),
                options = ThemePreference.entries,
                selectedOption = currentTheme!!,
                onOptionSelected = {
                    viewModel.onThemeSelected(it)
                    showThemeDialog = false
                },
                onDismissRequest = { showThemeDialog = false },
                optionToReadableString = { it.toReadableString() }
            )
        }

        if (showPdfProfileDialog && currentProfile != null) {
            SettingsRadioDialog(
                title = stringResource(R.string.settings_dialog_title_pdf),
                options = PdfExportProfile.entries,
                selectedOption = currentProfile!!,
                onOptionSelected = {
                    viewModel.onProfileSelected(it)
                    showPdfProfileDialog = false
                },
                onDismissRequest = { showPdfProfileDialog = false },
                optionToReadableString = { it.toReadableString() },
                optionToDescription = { it.getDescription() }
            )
        }
    }
}

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

@OptIn(ExperimentalMaterial3Api::class)
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
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismissRequest) {
        Scaffold { padding ->
            Column(modifier = Modifier.padding(padding)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(24.dp)
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
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (option == selectedOption),
                                onClick = null
                            )
                            Column(Modifier.padding(start = 24.dp)) {
                                Text(text = optionToReadableString(option))
                                optionToDescription?.let {
                                    Text(
                                        text = it(option),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ThemePreference.toReadableString(): String = when (this) {
    ThemePreference.SYSTEM -> stringResource(R.string.settings_theme_system)
    ThemePreference.LIGHT -> stringResource(R.string.settings_theme_light)
    ThemePreference.DARK -> stringResource(R.string.settings_theme_dark)
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