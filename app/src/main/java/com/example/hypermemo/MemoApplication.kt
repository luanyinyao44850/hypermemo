package com.example.hypermemo

import android.app.Application
import androidx.room.Room
import com.example.hypermemo.calendar.CalendarHelper
import com.example.hypermemo.data.NoteDatabase
import com.example.hypermemo.data.NoteRepository

class MemoApplication : Application() {
    private val database by lazy {
        Room.databaseBuilder(this, NoteDatabase::class.java, "notes.db").build()
    }
    val repository by lazy { NoteRepository(database.noteDao(), CalendarHelper(this)) }
}
