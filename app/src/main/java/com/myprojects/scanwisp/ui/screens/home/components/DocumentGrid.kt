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
import com.myprojects.scanwisp.data.local.model.DocumentWithPages

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentGrid(
    documents: List<DocumentWithPages>,
    nativeAd: NativeAd?,
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

    val gridItems = remember(documents, nativeAd, columns) {
        val items: MutableList<Any> = documents.toMutableList()
        // START: AI_MODIFIED_BLOCK
        val adPosition = 1 // Снижаем порог до 1
        // END: AI_MODIFIED_BLOCK
        if (nativeAd != null && items.size >= adPosition) {
            items.add(adPosition, nativeAd)
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
            key = { item -> if (item is DocumentWithPages) item.document.id else "ad" },
            contentType = { item -> if (item is DocumentWithPages) "doc" else "ad" }
        ) { item ->
            when (item) {
                is DocumentWithPages -> {
                    val document = item.document
                    val isSelected = document.id in selectedIds
                    DocumentCard(
                        modifier = Modifier.animateItemPlacement(),
                        documentWithPages = item,
                        isSelected = isSelected,
                        isSelectionMode = isSelectionMode,
                        onClick = { onDocumentClick(document.id) },
                        onLongClick = { onDocumentLongClick(document.id) },
                        onRenameRequest = { onRenameRequest(document.id) },
                        onMoveRequest = { onMoveRequest(document.id) },
                        onShareRequest = { onShareRequest(document.id) },
                        onDownloadRequest = { onDownloadRequest(document.id) },
                        onDeleteRequest = { onDeleteRequest(document.id) }
                    )
                }

                is NativeAd -> NativeAdCard(nativeAd = item)
            }
        }
    }
}