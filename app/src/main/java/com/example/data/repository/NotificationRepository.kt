package com.example.data.repository

import com.example.data.db.NotificationDao
import com.example.data.db.SmallStoreDatabase
import com.example.data.db.toEntity
import com.example.data.db.toModel
import com.example.model.NotificationItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NotificationRepository(
    private val notificationDao: NotificationDao
) {
    constructor(database: SmallStoreDatabase) : this(database.notificationDao())

    val notifications: Flow<List<NotificationItem>> = notificationDao.getAllNotifications().map { list ->
        list.map { it.toModel() }
    }

    suspend fun addNotification(notification: NotificationItem) {
        notificationDao.insertNotification(notification.toEntity())
    }

    suspend fun markNotificationsAsRead() {
        notificationDao.markAllAsRead()
    }
}
