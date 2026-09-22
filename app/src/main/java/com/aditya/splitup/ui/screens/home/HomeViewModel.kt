package com.aditya.splitup.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aditya.splitup.data.AppDatabase
import com.aditya.splitup.data.model.SplitGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val groupDao = db.groupDao()

    val groups: StateFlow<List<SplitGroup>> = groupDao.getAllGroups()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun deleteGroup(group: SplitGroup) {
        viewModelScope.launch {
            groupDao.deleteGroup(group)
        }
    }
}
