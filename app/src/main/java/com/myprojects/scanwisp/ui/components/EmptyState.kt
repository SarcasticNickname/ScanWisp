package com.myprojects.scanwisp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
// START: AI_MODIFIED_BLOCK
import androidx.compose.material3.Button
// END: AI_MODIFIED_BLOCK
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Компонент для отображения "пустого состояния" на экране.
 * Используется, когда нет данных для отображения (например, нет документов или папок).
 *
 * @param modifier Модификатор для настройки внешнего вида.
 * @param icon Иконка, которая будет отображаться в центре.
 * @param title Основной заголовок состояния.
 * @param subtitle Опциональный подзаголовок с дополнительным пояснением.
 * @param ctaText Опциональный текст для кнопки призыва к действию (CTA).
 * @param onCtaClick Опциональный обработчик нажатия на кнопку CTA.
 */
@Composable
fun EmptyState(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    // START: AI_MODIFIED_BLOCK
    ctaText: String? = null,
    onCtaClick: (() -> Unit)? = null
    // END: AI_MODIFIED_BLOCK
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title, // Описание для Accessibility
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            subtitle?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // START: AI_MODIFIED_BLOCK
            if (ctaText != null && onCtaClick != null) {
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onCtaClick) {
                    Text(text = ctaText)
                }
            }
            // END: AI_MODIFIED_BLOCK
        }
    }
}