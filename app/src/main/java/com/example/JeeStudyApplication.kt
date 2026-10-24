package com.example

import android.app.Application
import android.util.Log
import com.example.data.cloud.FirestoreCloudSyncRepository

class JeeStudyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val initialized = FirestoreCloudSyncRepository.ensureFirebaseInitialized(this)
        if (initialized) {
            Log.i("JeeStudyApplication", "Default FirebaseApp initialized in process $packageName.")
        } else {
            Log.w(
                "JeeStudyApplication",
                "FirebaseApp not configured in process $packageName; running in offline-first Room mode."
            )
        }
    }
}
