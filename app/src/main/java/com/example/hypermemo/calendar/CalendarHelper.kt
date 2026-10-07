package com.example.hypermemo.calendar

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import com.example.hypermemo.data.NoteEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TimeZone

/** 只使用应用 Context；Provider 操作统一放到 IO，权限弹窗由 Activity 生命周期托管。 */
// Lint 无法跨 access 高阶函数推断权限；access 每次操作前检查，且捕获中途撤销权限的异常。
@SuppressLint("MissingPermission")
class CalendarHelper(context: Context) : CalendarGateway {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    companion object {
        val permissions = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

        fun hasPermissions(context: Context): Boolean = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        fun requestPermissions(launcher: ActivityResultLauncher<Array<String>>) {
            launcher.launch(permissions)
        }
    }

    private suspend fun <T> access(block: () -> T): T = withContext(Dispatchers.IO) {
        if (!hasPermissions(appContext)) {
            throw CalendarAccessException("未获得日历读写权限。请授权后重试同步。")
        }
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: CalendarAccessException) {
            throw e
        } catch (e: SecurityException) {
            throw CalendarAccessException("日历权限已被撤回或系统禁止访问，请到应用设置中检查。", e)
        } catch (e: IllegalArgumentException) {
            throw CalendarAccessException(e.message ?: "系统日历不支持本次操作。", e)
        } catch (e: Exception) {
            throw CalendarAccessException("日历读写失败，请检查系统日历账户及存储状态后重试。", e)
        }
    }

    private fun eventKey(note: NoteEntity) = "hypermemo://note/${note.calendarKey}"

    private data class EventRef(val id: Long, val calendarId: Long)

    private fun findEvent(note: NoteEntity): EventRef? {
        // 除 ID 外校验归属，避免日历重建、ID 复用时误改其他事件。
        // 用稳定 key 搜索还能恢复进程在 Provider 成功后、Room 回写前退出的情况。
        val cursor = resolver.query(
            Events.CONTENT_URI,
            arrayOf(Events._ID, Events.CALENDAR_ID),
            "${Events.CUSTOM_APP_PACKAGE} = ? AND ${Events.CUSTOM_APP_URI} = ? AND ${Events.DELETED} = 0",
            arrayOf(appContext.packageName, eventKey(note)),
            "${Events._ID} ASC"
        ) ?: throw CalendarAccessException("系统日历无法读取，请稍后重试。")
        cursor.use {
            var first: EventRef? = null
            while (it.moveToNext()) {
                val event = EventRef(it.getLong(0), it.getLong(1))
                if (event.id == note.calendarEventId) return event
                if (first == null) first = event
            }
            return first
        }
    }

    private fun writableCalendar(existingId: Long? = null): Long {
        // Android 没有“全设备唯一默认日历”API。优先可写主日历，再回退到可写日历。
        val cursor = resolver.query(
            Calendars.CONTENT_URI,
            arrayOf(Calendars._ID, Calendars.ALLOWED_REMINDERS, Calendars.MAX_REMINDERS),
            "${Calendars.VISIBLE} = 1 AND ${Calendars.CALENDAR_ACCESS_LEVEL} >= ?" +
                if (existingId != null) " AND ${Calendars._ID} = ?" else "",
            if (existingId == null) arrayOf(Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
            else arrayOf(Calendars.CAL_ACCESS_CONTRIBUTOR.toString(), existingId.toString()),
            "${Calendars.IS_PRIMARY} DESC, ${Calendars._ID} ASC"
        ) ?: throw CalendarAccessException("无法查询系统日历账户。")
        cursor.use {
            while (it.moveToNext()) {
                val allowed = it.getString(1)?.split(',')?.map(String::trim).orEmpty()
                val maxReminders = if (it.isNull(2)) 0 else it.getInt(2)
                if (Reminders.METHOD_ALERT.toString() in allowed && maxReminders > 0) return it.getLong(0)
            }
        }
        throw CalendarAccessException(
            if (existingId == null) "找不到可写且支持弹窗提醒的日历。请先打开系统日历，启用或添加账户后重试。"
            else "原日历不可写、已隐藏或不支持弹窗提醒。请在系统日历恢复账户后重试。"
        )
    }

    override suspend fun upsert(note: NoteEntity): Long = access {
        val start = requireNotNull(note.reminderAt) { "尚未选择提醒时间。" }
        val existing = findEvent(note)
        if (existing == null) ReminderPolicy.validate(start, note.reminderMinutes)
        val calendarId = writableCalendar(existing?.calendarId)
        val values = ContentValues().apply {
            put(Events.CALENDAR_ID, calendarId)
            put(Events.TITLE, note.title.ifBlank { "未命名备忘录" })
            put(Events.DESCRIPTION, note.content)
            put(Events.DTSTART, start)
            put(Events.DTEND, start + 10 * 60_000L)
            put(Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(Events.ALL_DAY, 0)
            put(Events.HAS_ALARM, 1)
            put(Events.CUSTOM_APP_PACKAGE, appContext.packageName)
            put(Events.CUSTOM_APP_URI, eventKey(note))
        }
        val operations = arrayListOf<ContentProviderOperation>()
        if (existing == null) {
            operations += ContentProviderOperation.newInsert(Events.CONTENT_URI).withValues(values).build()
            operations += ContentProviderOperation.newInsert(Reminders.CONTENT_URI)
                .withValueBackReference(Reminders.EVENT_ID, 0)
                .withValue(Reminders.MINUTES, note.reminderMinutes)
                .withValue(Reminders.METHOD, Reminders.METHOD_ALERT).build()
        } else {
            operations += ContentProviderOperation.newUpdate(ContentUris.withAppendedId(Events.CONTENT_URI, existing.id))
                .withValues(values).withExpectedCount(1).build()
            operations += ContentProviderOperation.newDelete(Reminders.CONTENT_URI)
                .withSelection("${Reminders.EVENT_ID} = ?", arrayOf(existing.id.toString())).build()
            operations += ContentProviderOperation.newInsert(Reminders.CONTENT_URI)
                .withValue(Reminders.EVENT_ID, existing.id)
                .withValue(Reminders.MINUTES, note.reminderMinutes)
                .withValue(Reminders.METHOD, Reminders.METHOD_ALERT).build()
        }
        // 同一个 Provider 内批量提交事件和提醒，避免插入事件成功却没有提醒规则。
        val result = resolver.applyBatch(CalendarContract.AUTHORITY, operations)
        existing?.id ?: ContentUris.parseId(
            result[0].uri ?: throw CalendarAccessException("日历未返回事件 ID，请重试同步。")
        )
    }

    override suspend fun delete(note: NoteEntity) = access {
        val event = findEvent(note)
        if (event != null) {
            // Events 删除时 Provider 级联清理 Reminders；不要求日历支持新增提醒。
            resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, event.id), null, null)
            if (findEvent(note) != null) throw CalendarAccessException("日程尚未删除，请检查账户权限后重试。")
        }
        Unit // 事件已在系统日历中被删除时，也视为成功。
    }
}
