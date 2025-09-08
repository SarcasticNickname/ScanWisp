package com.myprojects.scanwisp.ui.screens.home.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.ads.nativead.NativeAd
import com.myprojects.scanwisp.data.local.DocumentRow

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentGrid(
    documents: List<DocumentRow>,
    nativeAd: NativeAd?,
    adPosition: Int,
    selectedIds: Set<String>,
    isSelectionMode: Boolean,
    onDocumentClick: (String) -> Unit,
    onDocumentLongClick: (String) -> Unit,
    onRenameRequest: (String) -> Unit,
    onMoveRequest: (String) -> Unit,
    onShareRequest: (String) -> Unit,
    onDownloadRequest: (String) -> Unit,
    onDeleteRequest: (String) -> Unit,
    modifier: Modifier = Modifier,
    widthSizeClass: WindowWidthSizeClass
) {
    val columns = when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> 2
        WindowWidthSizeClass.Medium -> 3
        else -> 4
    }

    val gridItems = remember(documents, nativeAd, adPosition) {
        val items: MutableList<Any> = documents.toMutableList()
        if (nativeAd != null && items.size >= adPosition) {
            items.add(adPosition, nativeAd)
        } else if (nativeAd != null && items.isNotEmpty()) {
            items.add(nativeAd)
        } else if (nativeAd != null && items.isEmpty()) {
            items.add(nativeAd)
        }
        items
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(
            items = gridItems,
            key = { item -> if (item is DocumentRow) item.id else "ad" },
            contentType = { item -> if (item is DocumentRow) "doc" else "ad" }
        ) { item ->
            when (item) {
                is DocumentRow -> {
                    val isSelected = item.id in selectedIds
                    DocumentCard(
                        modifier = Modifier.animateItemPlacement(),
                        documentRow = item,
                        isSelected = isSelected,
                        isSelectionMode = isSelectionMode,
                        onClick = { onDocumentClick(item.id) },
                        onLongClick = { onDocumentLongClick(item.id) },
                        onRenameRequest = { onRenameRequest(item.id) },
                        onMoveRequest = { onMoveRequest(item.id) },
                        onShareRequest = { onShareRequest(item.id) },
                        onDownloadRequest = { onDownloadRequest(item.id) },
                        onDeleteRequest = { onDeleteRequest(item.id) }
                    )
                }

                is NativeAd -> NativeAdCard(nativeAd = item)
            }
        }
    }
}