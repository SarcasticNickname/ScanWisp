package com.myprojects.scanwisp.ui.screens.folders

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.myprojects.scanwisp.data.local.model.FolderWithDocumentCount
import com.myprojects.scanwisp.domain.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FoldersScreenState(
    val folders: List<FolderWithDocumentCount> = emptyList(),
    val nativeAd: NativeAd? = null,
    // START: AI_MODIFIED_BLOCK
    val isLoading: Boolean = true
    // END: AI_MODIFIED_BLOCK
)

@HiltViewModel
class FoldersViewModel @Inject constructor(
    private val repository: DocumentRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _foldersState = MutableStateFlow(FoldersScreenState())
    val foldersState = _foldersState.asStateFlow()

    private val _isDialogVisible = MutableStateFlow(false)
    val isDialogVisible = _isDialogVisible.asStateFlow()

    init {
        loadNativeAd()
        viewModelScope.launch {
            repository.getFoldersWithDocumentCount().collect { folders ->
                // START: AI_MODIFIED_BLOCK
                _foldersState.update { it.copy(folders = folders, isLoading = false) }
                // END: AI_MODIFIED_BLOCK
            }
        }
    }

    override fun onCleared() {
        _foldersState.value.nativeAd?.destroy()
        super.onCleared()
    }

    private fun loadNativeAd() {
        val adUnitId = "ca-app-pub-3940256099942544/2247696110" // Тестовый ID
        val adLoader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { ad: NativeAd ->
                _foldersState.value.nativeAd?.destroy()
                // START: AI_MODIFIED_BLOCK
                _foldersState.update { it.copy(nativeAd = ad) }
                // END: AI_MODIFIED_BLOCK
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e("AdMob Folders", "Native ad failed to load: ${adError.message}")
                    // START: AI_MODIFIED_BLOCK
                    _foldersState.update { it.copy(nativeAd = null) }
                    // END: AI_MODIFIED_BLOCK
                }
            })
            .build()
        adLoader.loadAd(AdRequest.Builder().build())
    }

    fun onAddFolderRequest() {
        _isDialogVisible.value = true
    }

    fun onDialogDismiss() {
        _isDialogVisible.value = false
    }

    fun createFolder(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createFolder(name)
            onDialogDismiss()
        }
    }
}