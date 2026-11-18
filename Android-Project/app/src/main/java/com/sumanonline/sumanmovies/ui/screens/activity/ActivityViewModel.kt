package com.sumanonline.sumanmovies.ui.screens.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumanonline.sumanmovies.data.local.entities.WatchHistoryEntity
import com.sumanonline.sumanmovies.data.repository.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class ActivityFilterTab {
    ALL,
    WATCHING,
    COMPLETED
}

data class ActivityUiState(
    val filterQuery: String = "",
    val activeTab: ActivityFilterTab = ActivityFilterTab.ALL,
    val watchingList: List<WatchHistoryEntity> = emptyList(),
    val completedList: List<WatchHistoryEntity> = emptyList(),
    val allList: List<WatchHistoryEntity> = emptyList()
)

class ActivityViewModel(private val repository: MediaRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ActivityUiState())
    val uiState: StateFlow<ActivityUiState> = _uiState.asStateFlow()

    init {
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            repository.getAllHistoryFlow().collectLatest { all ->
                val watching = all.filter { !it.isCompleted }
                val completed = all.filter { it.isCompleted }
                _uiState.value = _uiState.value.copy(
                    allList = all,
                    watchingList = watching,
                    completedList = completed
                )
            }
        }
    }

    fun setFilterQuery(q: String) {
        _uiState.value = _uiState.value.copy(filterQuery = q)
    }

    fun setTab(tab: ActivityFilterTab) {
        _uiState.value = _uiState.value.copy(activeTab = tab)
    }

    fun removeHistoryItem(id: String) {
        viewModelScope.launch {
            repository.removeHistory(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
}
