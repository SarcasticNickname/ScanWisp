package com.myprojects.scanwisp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myprojects.scanwisp.ui.state.LoadingState
import kotlin.math.roundToInt

/**
 * Полноэкранный индикатор загрузки, который может отображать как определенный,
 * так и неопределенный прогресс.
 *
 * @param loadingState Состояние загрузки, определяющее видимость и вид индикатора.
 */
@Composable
fun FullScreenLoader(loadingState: LoadingState) {
    AnimatedVisibility(
        visible = loadingState.isBusy,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300))
    ) {
        // Используем пустой clickable, чтобы перехватывать все нажатия за оверлеем
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
                .clickable(
                    enabled = true,
                    onClick = {},
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 320.dp)
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    loadingState.message?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                    }

                    if (loadingState.progress != null) {
                        // Определенный прогресс
                        val progressValue = loadingState.progress.coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progressValue },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .semantics {
                                    progressBarRangeInfo =
                                        androidx.compose.ui.semantics.ProgressBarRangeInfo(
                                            current = progressValue,
                                            range = 0f..1f,
                                        )
                                }
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "${(progressValue * 100).roundToInt()}%",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        // Неопределенный прогресс
                        CircularProgressIndicator(strokeWidth = 4.dp)
                    }
                }
            }
        }
    }
}