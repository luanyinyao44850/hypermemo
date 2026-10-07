package com.example.hypermemo.calendar

import com.example.hypermemo.data.NoteEntity

interface CalendarGateway {
    suspend fun upsert(note: NoteEntity): Long
    suspend fun delete(note: NoteEntity)
}

class CalendarAccessException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
