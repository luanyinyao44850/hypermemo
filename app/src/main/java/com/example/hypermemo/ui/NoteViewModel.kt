package com.example.hypermemo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.hypermemo.data.NoteEntity
import com.example.hypermemo.data.NoteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorState(
    val draft: NoteEntity? = null,
    val dirty: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null
)

class NoteViewModel(private val repository: NoteRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(EditorState())
    val state = mutableState.asStateFlow()
    val notes = repository.notes.catch {
        report("读取备忘录失败，请检查存储空间后重新打开应用。")
        emit(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun edit(note: NoteEntity = NoteEntity()) {
        if (!state.value.busy) mutableState.value = EditorState(draft = note)
    }

    fun change(transform: (NoteEntity) -> NoteEntity) {
        mutableState.update { current ->
            val draft = current.draft
            if (current.busy || draft == null) current
            else current.copy(draft = transform(draft), dirty = true)
        }
    }

    fun closeEditor() {
        if (!state.value.busy) mutableState.value = EditorState()
    }

    fun report(message: String) { mutableState.update { it.copy(message = message) } }
    fun dismissMessage() { mutableState.update { it.copy(message = null) } }

    fun save() {
        val draft = state.value.draft ?: return
        if (state.value.busy) return
        mutableState.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                val result = repository.save(draft)
                mutableState.value = if (result.warning == null) EditorState(message = "已保存。")
                else EditorState(draft = result.note, message = result.warning)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                report("保存失败：${e.message ?: "请检查存储空间后重试。"}")
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    fun delete() {
        val draft = state.value.draft ?: return
        if (state.value.busy || draft.id == 0L) return
        mutableState.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                repository.delete(draft.id)
                mutableState.value = EditorState(message = "已删除备忘录及关联日程。")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                report("删除未完成，备忘录已保留：${e.message ?: "请稍后重试。"}")
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    class Factory(private val repository: NoteRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(NoteViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return NoteViewModel(repository) as T
        }
    }
}
