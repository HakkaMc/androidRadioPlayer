package com.example.androidradioplayer

import android.content.Intent

class NotificationManager private constructor() {
    companion object {
        private var instance: NotificationManager? = null

        fun getInstance() = instance ?: synchronized(this) {
            instance ?: NotificationManager().also { instance = it }
        }
    }

    private val notificationLiveData = NotificationLiveData()

    public fun sendNotificationMessage(message: Intent) {
        notificationLiveData.sendNotification(message)
    }

    public fun sendNotificationMessage(from: String, messageName: String) {
        val intent = Intent(from)
        intent.putExtra("messageName", messageName)

        notificationLiveData.sendNotification(intent)
    }

    public fun getNotificationLiveData(): NotificationLiveData? {
        return notificationLiveData
    }
}