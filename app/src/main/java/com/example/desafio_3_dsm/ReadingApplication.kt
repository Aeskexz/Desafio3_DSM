package com.example.desafio_3_dsm

import android.app.Application
import com.google.firebase.database.FirebaseDatabase

class ReadingApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseDatabase.getInstance().setPersistenceEnabled(true)
    }
}
