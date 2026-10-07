package com.example.hypermemo

import com.example.hypermemo.calendar.ReminderPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ReminderPolicyTest {
    @Test fun rejectsPastStart() {
        assertThrows(IllegalArgumentException::class.java) { ReminderPolicy.validate(1000, 0, 1000) }
    }

    @Test fun rejectsAdvanceReminderAlreadyPast() {
        assertThrows(IllegalArgumentException::class.java) { ReminderPolicy.validate(120_000, 5, 0) }
    }

    @Test fun acceptsFutureAdvanceReminder() { ReminderPolicy.validate(600_001, 10, 0) }

    @Test fun rejectsNegativeMinutes() {
        assertThrows(IllegalArgumentException::class.java) { ReminderPolicy.validate(600_001, -1, 0) }
    }

    @Test fun pickerDateIsUtcButChosenTimeIsShanghaiLocal() {
        val utcDate = ReminderPolicy.datePickerMillis(LocalDate.of(2030, 1, 2))
        val result = ReminderPolicy.toLocalInstant(utcDate, 9, 30, ZoneId.of("Asia/Shanghai"))
        assertEquals(Instant.parse("2030-01-02T01:30:00Z").toEpochMilli(), result)
    }

    @Test fun westernZoneDoesNotShiftSelectedDateToPreviousDay() {
        val utcDate = ReminderPolicy.datePickerMillis(LocalDate.of(2030, 1, 2))
        val result = ReminderPolicy.toLocalInstant(utcDate, 9, 30, ZoneId.of("America/Los_Angeles"))
        assertEquals(Instant.parse("2030-01-02T17:30:00Z").toEpochMilli(), result)
    }

    @Test fun rejectsNonexistentDstLocalTime() {
        val utcDate = ReminderPolicy.datePickerMillis(LocalDate.of(2030, 3, 10))
        assertThrows(IllegalArgumentException::class.java) {
            ReminderPolicy.toLocalInstant(utcDate, 2, 30, ZoneId.of("America/New_York"))
        }
    }
}
