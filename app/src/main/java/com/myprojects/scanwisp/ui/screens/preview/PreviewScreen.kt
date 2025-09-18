package com.myprojects.scanwisp.ui.screens.preview

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.SubcomposeAsyncImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.myprojects.scanwisp.R
import com.myprojects.scanwisp.domain.model.AppError
import com.myprojects.scanwisp.ui.components.ErrorDialog
import com.myprojects.scanwisp.ui.components.ErrorState
import com.myprojects.scanwisp.ui.components.FullScreenLoader
import com.myprojects.scanwisp.ui.events.UiEvent
import kotlinx.coroutines.flow.collectLatest
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    navController: NavController,
    viewModel: PreviewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val loadingState by viewModel.loadingState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity

    var errorToShowInDialog by remember { mutableStateOf<AppError?>(null) }

    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)

            // Берём первую страницу — нам нужна одна картинка для замены
            val newUri = scanningResult?.pages?.firstOrNull()?.imageUri

            if (newUri != null) {
                val flags =
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                // Закрепляем доступ к content:// URI на всякий случай
                try {
                    context.contentResolver.takePersistableUriPermission(newUri, flags)
                } catch (_: SecurityException) { /* провайдер мог не дать persist */
                }

                try {
                    context.grantUriPermission(context.packageName, newUri, flags)
                } catch (_: SecurityException) {
                }

                // Собственно, замена изображения текущей страницы
                viewModel.replaceImage(newUri)
            } else {
                Toast.makeText(context, R.string.toast_replace_cancelled, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.replaceImage(uri)
        } else {
            Toast.makeText(context, R.string.toast_replace_cancelled, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(key1 = true) {
        viewModel.uiEventFlow.collectLatest { event ->
            when (event) {
                is UiEvent.LaunchScanner -> {
                    val req = IntentSenderRequest.Builder(event.intentSender)
                        .setFillInIntent(
                            Intent().addFlags(
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                            )
                        )
                        .build()
                    scannerLauncher.launch(req)
                }

                is UiEvent.ShowSnackbar -> {
                    if (event.actionLabel == "OPEN_GALLERY") {
                        galleryLauncher.launch("image/*")
                        Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                    }
                }

                is UiEvent.ShowErrorDialog -> {
                    errorToShowInDialog = event.error
                }

                else -> Unit
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.preview_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.preview_cd_back)
                        )
                    }
                },
                actions = {
                    TextButton(onClick = {
                        if (activity != null) {
                            viewModel.onRescanClicked(activity)
                        } else {
                            Timber.e(
                                "Context is not an Activity, cannot start scanner."
                            )
                        }
                    }) {
                        Icon(
                            Icons.Default.Replay,
                            contentDescription = stringResource(R.string.preview_action_rescan)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.preview_action_rescan))
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when (val state = uiState) {
                is PreviewUiState.Loading -> {
                    CircularProgressIndicator()
                }

                is PreviewUiState.Error -> {
                    ErrorState(error = state.error, modifier = Modifier.fillMaxSize())
                }

                is PreviewUiState.Success -> {
                    SubcomposeAsyncImage(
                        model = state.page.processedImagePath,
                        contentDescription = stringResource(R.string.preview_cd_page_content),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        loading = {
                            CircularProgressIndicator()
                        },
                        error = {
                            Image(
                                painter = painterResource(id = R.drawable.card_placeholder),
                                contentDescription = stringResource(R.string.error_loading_image)
                            )
                        }
                    )
                }
            }
            FullScreenLoader(loadingState = loadingState)
        }
    }

    errorToShowInDialog?.let { error ->
        ErrorDialog(
            error = error,
            onDismiss = { errorToShowInDialog = null }
        )
    }
}