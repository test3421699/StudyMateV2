package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.ui.StudyMateViewModel

class AICancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("AICancelReceiver", "Cancel broadcast received")
        StudyMateViewModel.activeInstance?.cancelActiveAIGeneration()
    }
}
