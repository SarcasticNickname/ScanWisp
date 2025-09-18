package com.myprojects.scanwisp.ui.screens.home.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
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
import com.myprojects.scanwisp.ui.model.UiAction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentCard(
    documentRow: DocumentRow,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    actions: List<UiAction>,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    var menuExpanded by remember { mutableStateOf(false) }

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "border_color_animation"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 3.dp else 0.dp,
        animationSpec = tween(durationMillis = 200),
        label = "border_width_animation"
    )

    val formattedDate = remember(documentRow.creationTimestamp) {
        val date = Date(documentRow.creationTimestamp)
        val format = SimpleDateFormat("dd MMM", Locale("ru"))
        format.format(date)
    }

    val pagesCountText =
        stringResource(R.string.document_card_pages_count_format, documentRow.pageCount)
    val fullContentDescription = "${documentRow.title}, $pagesCountText, $formattedDate"
    val stateDescSelected = stringResource(R.string.state_selected)
    val stateDescNotSelected = stringResource(R.string.state_not_selected)

    ElevatedCard(
        modifier = modifier
            .border(
                BorderStroke(borderWidth, borderColor),
                shape = RoundedCornerShape(16.dp)
            )
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.2f)
            ) {
                SubcomposeAsyncImage(
                    model = documentRow.coverImagePath,
                    contentDescription = stringResource(id = R.string.cd_page_preview_thumbnail),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = { ShimmeringCard(cornerRadius = 0.dp) },
                    error = {
                        Image(
                            painter = painterResource(id = R.drawable.card_placeholder),
                            contentDescription = stringResource(R.string.error_loading_image),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                )
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
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = documentRow.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
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
                    ActionsMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        actions = actions
                    )
                }
            }

            Text(
                text = "$pagesCountText • $formattedDate",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}