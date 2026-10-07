package com.example.hypermemo.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.hypermemo.calendar.CalendarHelper
import com.example.hypermemo.data.NoteEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun formatTime(value: Long): String = Instant.ofEpochMilli(value)
    .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoApp(vm: NoteViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val draft = state.draft
    var picker by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var permissionAction by rememberSaveable { mutableStateOf<String?>(null) }
    var denied by rememberSaveable { mutableStateOf(false) }

    fun execute(action: String) { if (action == "delete") vm.delete() else vm.save() }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (CalendarHelper.hasPermissions(context)) {
            permissionAction?.let(::execute)
            permissionAction = null
        } else denied = true
    }
    fun requestAction(action: String) {
        val needsCalendar = draft?.let { it.reminderAt != null || it.calendarEventId != null || it.calendarSyncPending } == true
        if (needsCalendar && !CalendarHelper.hasPermissions(context)) {
            denied = false
            permissionAction = action
        } else execute(action)
    }
    fun leave() {
        if (!state.busy) {
            if (state.dirty) confirmLeave = true else vm.closeEditor()
        }
    }
    BackHandler(enabled = draft != null) { leave() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (draft == null) "轻记" else if (draft.id == 0L) "新建备忘录" else "编辑备忘录") },
                navigationIcon = {
                    if (draft != null) TextButton(onClick = ::leave, enabled = !state.busy) { Text("返回") }
                },
                actions = {
                    if (draft != null) TextButton(onClick = { requestAction("save") }, enabled = !state.busy) {
                        Text(if (state.busy) "处理中…" else "保存")
                    }
                }
            )
        },
        floatingActionButton = {
            if (draft == null) ExtendedFloatingActionButton(onClick = { vm.edit() }) { Text("＋ 新建") }
        }
    ) { padding ->
        if (draft == null) {
            if (notes.isEmpty()) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("还没有备忘录，点击右下角新建。")
            } else LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(notes, key = { it.id }) { note ->
                    Card(onClick = { vm.edit(note) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(note.title.ifBlank { "未命名备忘录" }, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(formatTime(note.createdAt), style = MaterialTheme.typography.labelMedium)
                            if (note.content.isNotBlank()) Text(note.content, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (note.calendarSyncPending) Text("提醒待同步 · 打开后保存重试", color = MaterialTheme.colorScheme.error)
                            else note.reminderAt?.let { Text("日程 ${formatTime(it)}", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 使用原生文本组件，不拦截长按手势，保留选择、复制粘贴和输入法能力。
                OutlinedTextField(
                    value = draft.title, onValueChange = { text -> vm.change { it.copy(title = text) } },
                    label = { Text("标题") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !state.busy
                )
                OutlinedTextField(
                    value = draft.content, onValueChange = { text -> vm.change { it.copy(content = text) } },
                    label = { Text("正文") }, modifier = Modifier.fillMaxWidth(), minLines = 10, maxLines = 20, enabled = !state.busy
                )
                ReminderCard(draft, state.busy,
                    onChoose = { picker = true },
                    onClear = { vm.change { it.copy(reminderAt = null, reminderMinutes = 0) } },
                    onMinutes = { minutes -> vm.change { it.copy(reminderMinutes = minutes) } }
                )
                if (draft.id != 0L) OutlinedButton(
                    onClick = { confirmDelete = true }, enabled = !state.busy,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("删除备忘录") }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }

    if (picker && draft != null) ReminderPicker(draft.reminderAt, { picker = false }) { selected ->
        vm.change { it.copy(reminderAt = selected) }
        picker = false
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, title = { Text("删除备忘录？") },
        text = { Text("关联的日历事件会一并删除。日历删除失败时会保留备忘录。") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; requestAction("delete") }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
    )
    if (confirmLeave) AlertDialog(
        onDismissRequest = { confirmLeave = false }, title = { Text("放弃未保存的修改？") },
        confirmButton = { TextButton(onClick = { confirmLeave = false; vm.closeEditor() }) { Text("放弃") } },
        dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("继续编辑") } }
    )
    if (permissionAction != null) AlertDialog(
        onDismissRequest = { permissionAction = null },
        title = { Text(if (denied) "尚未获得日历权限" else "需要日历读写权限") },
        text = {
            Column {
                Text(if (denied) "你可以在应用设置 → 权限中允许日历访问，然后返回并再次保存或删除。"
                else "用于把提醒写入系统日历，以及更新或删除关联日程。拒绝授权仍可保存文字，提醒将等待同步。")
                TextButton(onClick = { permissionAction = null }) { Text("返回编辑") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (denied) {
                    try {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                    } catch (_: Exception) { vm.report("无法打开设置，请手动进入系统设置 → 应用 → 轻记 → 权限。") }
                    permissionAction = null
                } else CalendarHelper.requestPermissions(permissionLauncher)
            }) { Text(if (denied) "打开设置" else "授权日历") }
        },
        dismissButton = {
            TextButton(onClick = {
                val action = permissionAction
                permissionAction = null
                if (action == "save") vm.save()
            }) { Text(if (permissionAction == "save") "仅保存本地" else "取消删除") }
        }
    )
    state.message?.let { message ->
        AlertDialog(onDismissRequest = vm::dismissMessage, title = { Text("提示") }, text = { Text(message) },
            confirmButton = { TextButton(onClick = vm::dismissMessage) { Text("知道了") } })
    }
}

@Composable
private fun ReminderCard(note: NoteEntity, busy: Boolean, onChoose: () -> Unit, onClear: () -> Unit, onMinutes: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("系统日历提醒", style = MaterialTheme.typography.titleMedium)
            Text(note.reminderAt?.let { "日程开始：${formatTime(it)}" } ?: "未设置提醒")
            if (note.calendarSyncPending) Text("待同步；旧日历提醒可能仍然有效。保存可重试。", color = MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onChoose, enabled = !busy) { Text("选择日期和时间") }
                if (note.reminderAt != null) TextButton(onClick = onClear, enabled = !busy) { Text("移除提醒") }
            }
            if (note.reminderAt != null) {
                Box {
                    OutlinedButton(onClick = { expanded = true }, enabled = !busy) {
                        Text(if (note.reminderMinutes == 0) "准点提醒" else "提前 ${note.reminderMinutes} 分钟")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf(0, 5, 10, 15, 30, 60).forEach { minutes ->
                            DropdownMenuItem(text = { Text(if (minutes == 0) "准点提醒" else "提前 $minutes 分钟") },
                                onClick = { expanded = false; onMinutes(minutes) })
                        }
                    }
                }
                Text("实际提醒：${formatTime(note.reminderAt - note.reminderMinutes * 60_000L)}", style = MaterialTheme.typography.bodySmall)
            }
            Text("保存后由系统日历发送提醒。请在澎湃 OS 日历中开启通知、声音和弹窗；静音或勿扰可能影响提醒。",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
