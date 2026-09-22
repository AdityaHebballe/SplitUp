package com.aditya.splitup

import android.app.Application
import android.util.Log
import com.aditya.splitup.data.AppDatabase
import com.aditya.splitup.data.sync.FirestoreService
import com.aditya.splitup.data.sync.MigrationWorker
import com.aditya.splitup.data.sync.SyncRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SplitTrackerApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    val firestoreService: FirestoreService by lazy { FirestoreService() }
    val syncRepository: SyncRepository by lazy { SyncRepository(firestoreService, database) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ensureAnonymousAuth()
    }

    private fun ensureAnonymousAuth() {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnSuccessListener {
                    Log.d("SplitTrackerApp", "Signed in anonymously: ${it.user?.uid}")
                    runMigration()
                }
                .addOnFailureListener { e ->
                    Log.e("SplitTrackerApp", "Anonymous auth failed", e)
                }
        } else {
            Log.d("SplitTrackerApp", "Already signed in: ${auth.currentUser?.uid}")
            runMigration()
        }
    }

    private fun runMigration() {
        appScope.launch {
            try {
                MigrationWorker(syncRepository, database).migrateLocalGroups()
            } catch (e: Exception) {
                Log.e("SplitTrackerApp", "Migration failed", e)
            }
        }
    }
}
