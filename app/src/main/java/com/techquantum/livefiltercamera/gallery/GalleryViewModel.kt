package com.techquantum.livefiltercamera.gallery

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GalleryUiState(
    val mediaItems: List<MediaItem> = emptyList(),
    val selectedItemIndex: Int? = null,
    val isLoading: Boolean = false,
    val itemToDelete: MediaItem? = null
) {
    val selectedItem: MediaItem?
        get() = selectedItemIndex?.let { index ->
            if (index in mediaItems.indices) mediaItems[index] else null
        }
}

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GalleryRepository(application)
    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    init {
        loadMedia()
    }

    fun loadMedia() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val items = repository.getMediaItems()
            _uiState.update { it.copy(mediaItems = items, isLoading = false) }
        }
    }

    fun selectItem(item: MediaItem) {
        val index = _uiState.value.mediaItems.indexOfFirst { it.id == item.id }
        if (index != -1) {
            _uiState.update { it.copy(selectedItemIndex = index) }
        }
    }

    fun selectItemByIndex(index: Int) {
        if (index in _uiState.value.mediaItems.indices) {
            _uiState.update { it.copy(selectedItemIndex = index) }
        }
    }

    fun closeFullscreenViewer() {
        _uiState.update { it.copy(selectedItemIndex = null) }
    }

    fun promptDelete(item: MediaItem) {
        _uiState.update { it.copy(itemToDelete = item) }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(itemToDelete = null) }
    }

    fun confirmDelete(onDeleted: () -> Unit = {}) {
        val item = _uiState.value.itemToDelete ?: return
        viewModelScope.launch {
            val success = repository.deleteMedia(item.uri)
            if (success) {
                val updated = _uiState.value.mediaItems.filter { it.id != item.id }
                val newIndex = if (updated.isEmpty()) {
                    null
                } else {
                    _uiState.value.selectedItemIndex?.let { oldIdx ->
                        oldIdx.coerceAtMost(updated.size - 1)
                    }
                }
                _uiState.update {
                    it.copy(
                        mediaItems = updated,
                        selectedItemIndex = newIndex,
                        itemToDelete = null
                    )
                }
                onDeleted()
            } else {
                _uiState.update { it.copy(itemToDelete = null) }
            }
        }
    }

    fun createShareIntent(item: MediaItem): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = if (item.isVideo) "video/*" else "image/*"
            putExtra(Intent.EXTRA_STREAM, item.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
