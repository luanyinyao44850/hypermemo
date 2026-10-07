package com.example.hypermemo.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val calendarEventId: Long? = null,
    // 以下字段记录用户期望的提醒，未授权时也可以离线保存、稍后重试。
    val reminderAt: Long? = null,
    val reminderMinutes: Int = 0,
    val calendarSyncPending: Boolean = false,
    // 稳定标识用于找回“已插入日历但尚未回写 Room”的事件，降低重复插入风险。
    val calendarKey: String = UUID.randomUUID().toString()
)
