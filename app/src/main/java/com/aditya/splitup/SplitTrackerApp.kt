package com.aditya.splitup

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ensureAnonymousAuth()
        registerConnectivityRetry()
    }

    // Retry any Firestore writes that failed while offline as soon as connectivity
    // returns, instead of leaving local/remote data permanently diverged until the
    // user happens to revisit a screen that triggers a sync.
    private fun registerConnectivityRetry() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                retryUnsyncedGroups()
            }
        })
    }

    private fun retryUnsyncedGroups() {
        appScope.launch {
            try {
                val groups = database.groupDao().getAllGroupsOnce()
                for (group in groups) {
                    if (group.firestoreId != null) {
                        syncRepository.syncLocalUnsyncedData(group.id)
                    }
                }
            } catch (e: Exception) {
                Log.e("SplitTrackerApp", "Failed to retry unsynced groups", e)
            }
        }
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
