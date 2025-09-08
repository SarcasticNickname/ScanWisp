package com.myprojects.scanwisp.ui.screens.home.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.myprojects.scanwisp.R
import com.myprojects.scanwisp.data.local.DocumentRow
import com.myprojects.scanwisp.ui.components.ShimmeringCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentListItem(
    documentRow: DocumentRow,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRenameRequest: () -> Unit,
    onMoveRequest: () -> Unit,
    onShareRequest: () -> Unit,
    onDownloadRequest: () -> Unit,
    onDeleteRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    var menuExpanded by remember { mutableStateOf(false) }

    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = tween(durationMillis = 200),
        label = "list_item_background_color_animation"
    )

    val formattedDate = formatDate(documentRow.creationTimestamp)
    val pagesCountText =
        stringResource(R.string.document_card_pages_count_format, documentRow.pageCount)
    val fullContentDescription = "${documentRow.title}, $pagesCountText, $formattedDate"
    val stateDescSelected = stringResource(R.string.state_selected)
    val stateDescNotSelected = stringResource(R.string.state_not_selected)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = fullContentDescription
                role = Role.Button
                stateDescription = if (isSelected) stateDescSelected else stateDescNotSelected
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
    ) {
        Row(
            modifier = Modifier.height(88.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .fillMaxHeight()
                    .aspectRatio(1f / 1.41f) // A4 ratio
            ) {
                SubcomposeAsyncImage(
                    model = documentRow.coverImagePath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth(),
                    loading = { ShimmeringCard(cornerRadius = 8.dp) },
                    error = {
                        Image(
                            painter = painterResource(id = R.drawable.card_placeholder),
                            contentDescription = stringResource(R.string.error_loading_image),
                            contentScale = ContentScale.Crop
                        )
                    }
                )
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = documentRow.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$pagesCountText • $formattedDate",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    enabled = !isSelectionMode
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(
                            R.string.document_card_cd_more_actions,
                            documentRow.title
                        )
                    )
                }
                DocumentActionMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    onRenameClick = onRenameRequest,
                    onMoveClick = onMoveRequest,
                    onDeleteClick = onDeleteRequest,
                    onShareClick = onShareRequest,
                    onDownloadClick = onDownloadRequest
                )
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val date = Date(timestamp)
    val format = SimpleDateFormat("dd MMM yyyy", Locale("ru"))
    return format.format(date)
}