package com.sumanonline.sumanmovies.ui.screens.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumanonline.sumanmovies.data.models.MediaItem
import com.sumanonline.sumanmovies.data.repository.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CategoryMediaUiState(
    val title: String = "Popular Movies",
    val categoryKey: String = "popular_movies",
    val items: List<MediaItem> = emptyList(),
    val totalCount: Int = 20001,
    val isLoading: Boolean = true
)

class CategoryMediaViewModel(
    private val repository: MediaRepository,
    private val categoryKey: String,
    private val title: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CategoryMediaUiState(
            title = title,
            categoryKey = categoryKey
        )
    )
    val uiState: StateFlow<CategoryMediaUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = repository.getCategoryMedia(categoryKey, 1)
            _uiState.value = _uiState.value.copy(
                items = result.first,
                totalCount = result.second,
                isLoading = false
            )
        }
    }
}
