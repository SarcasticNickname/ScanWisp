package com.myprojects.scanwisp.ui.theme

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

class FabCutoutShape : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val cradleRadius = with(density) {
            // Радиус выемки = половина размера FAB + отступ
            (56.dp.toPx() / 2f) + 8.dp.toPx()
        }

        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width / 2 - cradleRadius, 0f)

            // Рисуем дугу, которая создает вырез.
            arcTo(
                rect = Rect(
                    left = size.width / 2 - cradleRadius,
                    top = -cradleRadius,
                    right = size.width / 2 + cradleRadius,
                    bottom = cradleRadius
                ),
                startAngleDegrees = 180.0f,
                sweepAngleDegrees = -180.0f,
                forceMoveTo = false
            )

            lineTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }

        return Outline.Generic(path)
    }
}