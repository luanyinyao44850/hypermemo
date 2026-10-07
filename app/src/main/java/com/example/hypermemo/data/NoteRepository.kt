package com.example.hypermemo.data

import com.example.hypermemo.calendar.CalendarGateway
import com.example.hypermemo.calendar.ReminderPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class SaveResult(val note: NoteEntity, val warning: String? = null)

class NoteRepository(private val dao: NoteDao, private val calendar: CalendarGateway) {
    val notes = dao.observeAll()
    private val mutex = Mutex()

    suspend fun save(draft: NoteEntity): SaveResult = mutex.withLock {
        require(draft.title.isNotBlank() || draft.content.isNotBlank()) { "标题和正文不能同时为空。" }
        val old = if (draft.id == 0L) null else requireNotNull(dao.get(draft.id)) { "备忘录已不存在。" }
        if (draft.reminderAt != null &&
            (old?.reminderAt != draft.reminderAt || old?.reminderMinutes != draft.reminderMinutes || old?.calendarSyncPending == true)) {
            ReminderPolicy.validate(draft.reminderAt, draft.reminderMinutes)
        }
        val needsCalendar = draft.reminderAt != null || old?.calendarEventId != null || old?.calendarSyncPending == true
        var local = draft.copy(
            title = draft.title.trim(),
            createdAt = old?.createdAt ?: draft.createdAt,
            calendarKey = old?.calendarKey ?: draft.calendarKey,
            calendarEventId = old?.calendarEventId,
            calendarSyncPending = needsCalendar
        )
        // 先保存正文及同步意图。日历拒绝访问时，用户的文字不能丢失。
        if (old == null) local = local.copy(id = dao.insert(local))
        else check(dao.update(local) == 1) { "备忘录保存失败。" }
        if (needsCalendar) synchronize(local) else SaveResult(local)
    }

    private suspend fun synchronize(note: NoteEntity): SaveResult {
        return try {
            // 一次很短的跨存储操作不因旋转/界面退出而中途取消；进程退出仍由 pending + key 恢复。
            withContext(NonCancellable) {
                val eventId = if (note.reminderAt == null) {
                    calendar.delete(note)
                    null
                } else calendar.upsert(note)
                val synced = note.copy(calendarEventId = eventId, calendarSyncPending = false)
                check(dao.update(synced) == 1) { "日历已处理，但本地关联未能保存。" }
                SaveResult(synced)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SaveResult(note, "正文已保存；提醒尚未同步。${e.message ?: "请稍后重试。"}")
        }
    }

    suspend fun delete(id: Long) = mutex.withLock {
        val note = dao.get(id) ?: return@withLock
        withContext(NonCancellable) {
            // 必须先删日历：权限不足时保留笔记和事件关联，避免留下无法追踪的提醒。
            if (note.calendarEventId != null || note.calendarSyncPending) calendar.delete(note)
            dao.delete(id)
        }
    }
}
