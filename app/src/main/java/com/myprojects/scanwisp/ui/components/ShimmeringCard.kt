package com.myprojects.scanwisp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import com.myprojects.scanwisp.ui.theme.Dimens
import com.valentinilk.shimmer.shimmer

/**
 * Компонент-плейсхолдер с shimmer-эффектом для карточек.
 * Используется, пока Coil загружает изображение.
 *
 * @param cornerRadius Радиус скругления углов.
 */
@Composable
fun ShimmeringCard(cornerRadius: Dp) {
    Box(
        modifier = Modifier
            .aspectRatio(Dimens.A_SERIES_PAPER_ASPECT_RATIO)
            .clip(RoundedCornerShape(cornerRadius))
            .shimmer()
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
    )
}