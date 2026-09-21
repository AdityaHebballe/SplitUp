package com.example.expensetracker

import android.app.Application
import com.example.expensetracker.data.AppDatabase

class SplitTrackerApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
}
