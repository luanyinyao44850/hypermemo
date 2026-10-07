package com.example.hypermemo

import com.example.hypermemo.calendar.CalendarGateway
import com.example.hypermemo.data.NoteDao
import com.example.hypermemo.data.NoteEntity
import com.example.hypermemo.data.NoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class NoteRepositoryTest {
    private class FakeDao : NoteDao {
        val rows = linkedMapOf<Long, NoteEntity>()
        val flow = MutableStateFlow<List<NoteEntity>>(emptyList())
        var failSyncWrite = false
        private var nextId = 1L
        override fun observeAll() = flow
        override suspend fun get(id: Long) = rows[id]
        override suspend fun insert(note: NoteEntity): Long {
            val id = nextId++
            rows[id] = note.copy(id = id)
            publish()
            return id
        }
        override suspend fun update(note: NoteEntity): Int {
            if (failSyncWrite && !note.calendarSyncPending) error("disk full")
            if (note.id !in rows) return 0
            rows[note.id] = note
            publish()
            return 1
        }
        override suspend fun delete(id: Long) { rows.remove(id); publish() }
        private fun publish() { flow.value = rows.values.sortedByDescending { it.createdAt } }
    }

    private class FakeCalendar : CalendarGateway {
        var fail = false
        var calls = 0
        val events = mutableMapOf<String, Long>()
        override suspend fun upsert(note: NoteEntity): Long {
            calls++
            if (fail) error("permission denied")
            return events.getOrPut(note.calendarKey) { events.size + 100L }
        }
        override suspend fun delete(note: NoteEntity) {
            calls++
            if (fail) error("permission denied")
            events.remove(note.calendarKey)
        }
    }

    @Test fun ordinaryNotesNeverTouchCalendar() = runTest {
        val dao = FakeDao(); val calendar = FakeCalendar()
        val saved = NoteRepository(dao, calendar).save(NoteEntity(title = "普通笔记"))
        assertEquals(0, calendar.calls)
        assertNotEquals(0L, saved.note.id)
        assertFalse(saved.note.calendarSyncPending)
    }

    @Test fun permissionFailureKeepsTextAndRetryDoesNotDuplicateNote() = runTest {
        val dao = FakeDao(); val calendar = FakeCalendar(); val repo = NoteRepository(dao, calendar)
        calendar.fail = true
        val first = repo.save(NoteEntity(title = "记得喝水", reminderAt = 4_000_000_000_000))
        assertNotNull(first.warning)
        assertTrue(dao.get(first.note.id)!!.calendarSyncPending)
        calendar.fail = false
        val second = repo.save(first.note)
        assertNull(second.warning)
        assertEquals(1, dao.rows.size)
        assertEquals(100L, second.note.calendarEventId)
    }

    @Test fun failedDeleteKeepsLocalNoteAndEventAssociation() = runTest {
        val dao = FakeDao(); val calendar = FakeCalendar(); val repo = NoteRepository(dao, calendar)
        val saved = repo.save(NoteEntity(title = "重要", reminderAt = 4_000_000_000_000)).note
        calendar.fail = true
        try { repo.delete(saved.id); fail("Deletion must fail") } catch (_: IllegalStateException) { }
        assertEquals(saved, dao.get(saved.id))
    }

    @Test fun successfulDeleteRemovesEventAndNote() = runTest {
        val dao = FakeDao(); val calendar = FakeCalendar(); val repo = NoteRepository(dao, calendar)
        val saved = repo.save(NoteEntity(title = "重要", reminderAt = 4_000_000_000_000)).note
        repo.delete(saved.id)
        assertNull(dao.get(saved.id))
        assertTrue(calendar.events.isEmpty())
    }

    @Test fun clearingReminderDeletesOnlyCalendarEvent() = runTest {
        val dao = FakeDao(); val calendar = FakeCalendar(); val repo = NoteRepository(dao, calendar)
        val saved = repo.save(NoteEntity(title = "保留正文", reminderAt = 4_000_000_000_000)).note
        val result = repo.save(saved.copy(reminderAt = null))
        assertNull(result.note.calendarEventId)
        assertFalse(result.note.calendarSyncPending)
        assertEquals("保留正文", dao.get(saved.id)!!.title)
        assertTrue(calendar.events.isEmpty())
    }

    @Test fun editingPreservesCreationTimeAndStableIdentity() = runTest {
        val dao = FakeDao(); val calendar = FakeCalendar(); val repo = NoteRepository(dao, calendar)
        val first = repo.save(NoteEntity(title = "一", createdAt = 1234)).note
        val edited = repo.save(first.copy(title = "二", createdAt = 9999, calendarKey = "unexpected")).note
        assertEquals(1234L, edited.createdAt)
        assertEquals(first.calendarKey, edited.calendarKey)
    }

    @Test fun backlinkWriteFailureCanBeRetriedUsingSameKey() = runTest {
        val dao = FakeDao(); val calendar = FakeCalendar(); val repo = NoteRepository(dao, calendar)
        dao.failSyncWrite = true
        val failed = repo.save(NoteEntity(title = "重试", reminderAt = 4_000_000_000_000))
        assertNotNull(failed.warning)
        assertEquals(1, calendar.events.size)
        dao.failSyncWrite = false
        val retried = repo.save(dao.get(failed.note.id)!!)
        assertNull(retried.warning)
        assertEquals(1, calendar.events.size)
        assertNotNull(retried.note.calendarEventId)
    }

    @Test fun failedReminderRemovalKeepsOldEventIdForRetry() = runTest {
        val dao = FakeDao(); val calendar = FakeCalendar(); val repo = NoteRepository(dao, calendar)
        val saved = repo.save(NoteEntity(title = "移除", reminderAt = 4_000_000_000_000)).note
        calendar.fail = true
        val failed = repo.save(saved.copy(reminderAt = null))
        assertTrue(failed.note.calendarSyncPending)
        assertEquals(saved.calendarEventId, failed.note.calendarEventId)
        calendar.fail = false
        val retried = repo.save(failed.note)
        assertNull(retried.note.calendarEventId)
        assertTrue(calendar.events.isEmpty())
    }
}
