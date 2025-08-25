package com.myprojects.scanwisp.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Объект для хранения общих размеров (отступов, высот и т.д.),
 * используемых в приложении, для обеспечения консистентности.
 */
object Dimens {

    /**
     * Соотношение сторон для карточек документов и страниц,
     * имитирующее формат бумаги A-серии (например, A4).
     * Рассчитывается как 1 / sqrt(2).
     */
    const val A_SERIES_PAPER_ASPECT_RATIO = 0.707f

    /**
     * Вертикальное смещение для FloatingActionButton, чтобы она корректно
     * располагалась над вырезом в BottomAppBar.
     * Рассчитано как половина высоты BottomAppBar (80dp / 2 = 40dp) + небольшой запас.
     */
    val FabOffsetY = 42.dp
}