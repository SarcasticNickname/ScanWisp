package com.myprojects.scanwisp.ui.screens.detail.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.myprojects.scanwisp.R
import com.myprojects.scanwisp.data.local.model.PageEntity
import com.myprojects.scanwisp.domain.model.OcrStatus
import com.myprojects.scanwisp.ui.components.ShimmeringCard
import com.myprojects.scanwisp.ui.theme.Dimens

/**
 * Карточка-превью для одной страницы документа.
 * Отображает изображение, номер, состояния выбора и сортировки.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PageThumbnailCard(
    page: PageEntity,
    pageIndex: Int,
    isSelected: Boolean,
    showDragHandle: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val elevation by animateDpAsState(
        targetValue = if (isPressed) 8.dp else 2.dp,
        label = "page_elevation_animation"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "page_border_color_animation"
    )

    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 3.dp else 0.dp,
        label = "page_border_width_animation"
    )

    val stateDescSelected = stringResource(R.string.state_selected)
    val stateDescNotSelected = stringResource(R.string.state_not_selected)
    val stateDescDraggable = stringResource(R.string.detail_page_card_state_draggable)
    val cdPage = stringResource(R.string.detail_page_card_cd, pageIndex + 1)

    Card(
        modifier = modifier
            .aspectRatio(Dimens.A_SERIES_PAPER_ASPECT_RATIO)
            .semantics {
                contentDescription = cdPage
                role = Role.Button

                val selectionState = if (isSelected) stateDescSelected else stateDescNotSelected
                val dragState = if (showDragHandle) stateDescDraggable else ""
                stateDescription = selectionState + dragState
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(borderWidth, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            SubcomposeAsyncImage(

                model = page.thumbnailPath.ifBlank { page.processedImagePath },
                contentDescription = stringResource(id = R.string.cd_page_preview_thumbnail),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    ShimmeringCard(cornerRadius = 12.dp)
                },
                error = {
                    Image(
                        painter = painterResource(id = R.drawable.card_placeholder),
                        contentDescription = stringResource(R.string.error_loading_image)
                    )
                }
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                            startY = 130f
                        )
                    )
            )
            IconButton(
                onClick = onMoreClick,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    stringResource(R.string.detail_page_card_cd_actions, pageIndex + 1),
                    tint = Color.White
                )
            }
            Text(
                text = "${pageIndex + 1}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            )
            val effectiveStatus =
                if (page.ocrStatus != OcrStatus.DONE && !page.extractedText.isNullOrBlank()) {
                    OcrStatus.DONE
                } else {
                    page.ocrStatus
                }
            when (effectiveStatus) {
                OcrStatus.IN_PROGRESS -> {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(20.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .padding(3.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.fillMaxSize(),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    }
                }

                OcrStatus.FAILED -> {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Ошибка распознавания",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(20.dp)
                    )
                }

                OcrStatus.DONE -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Распознано",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(20.dp)
                    )
                }

                OcrStatus.PENDING -> {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f)
                    ) {
                        Text(
                            text = stringResource(R.string.ocr_status_not_recognized),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.state_selected),
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(4.dp)
                )
            }
            if (showDragHandle) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = stringResource(R.string.detail_page_card_state_draggable),
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp)
                )
            }
        }
    }
}