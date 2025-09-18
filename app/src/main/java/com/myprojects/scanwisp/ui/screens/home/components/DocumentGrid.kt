package com.myprojects.scanwisp.ui.screens.home.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.ads.nativead.NativeAd
import com.myprojects.scanwisp.data.local.DocumentRow
import com.myprojects.scanwisp.ui.model.UiAction

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentGrid(
    // ИЗМЕНЕНИЕ: Вместо `documents`, `nativeAd`, `adPosition` теперь принимаем один смешанный список.
    items: List<Any>,
    selectedIds: Set<String>,
    isSelectionMode: Boolean,
    onDocumentClick: (String) -> Unit,
    onDocumentLongClick: (String) -> Unit,
    documentActionsBuilder: @Composable (String) -> List<UiAction>,
    modifier: Modifier = Modifier,
    widthSizeClass: WindowWidthSizeClass
) {
    val columns = when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> 2
        WindowWidthSizeClass.Medium -> 3
        else -> 4
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        itemsIndexed(
            items = items,
            key = { index, item -> // Неявно выводится тип
                when (item) {
                    is DocumentRow -> "doc-${item.id}"
                    is NativeAd -> "ad-$index"
                    else -> "other-$index"
                }
            },
            contentType = { _, item -> if (item is DocumentRow) "doc" else "ad" }
        ) { _, item ->
            when (item) {
                is DocumentRow -> {
                    val isSelected = item.id in selectedIds
                    DocumentCard(
                        modifier = Modifier.animateItem(),
                        documentRow = item,
                        isSelected = isSelected,
                        isSelectionMode = isSelectionMode,
                        onClick = { onDocumentClick(item.id) },
                        onLongClick = { onDocumentLongClick(item.id) },
                        actions = documentActionsBuilder(item.id)
                    )
                }

                is NativeAd -> {
                    NativeAdCard(nativeAd = item)
                }
            }
        }
    }
}