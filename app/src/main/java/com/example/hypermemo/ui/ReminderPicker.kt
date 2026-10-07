package com.example.hypermemo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.hypermemo.calendar.ReminderPolicy
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderPicker(initialMillis: Long?, onDismiss: () -> Unit, onSelected: (Long) -> Unit) {
    val zone = ZoneId.systemDefault()
    val initial = remember { Instant.ofEpochMilli(initialMillis ?: (System.currentTimeMillis() + 3_600_000)).atZone(zone) }
    val dateState = rememberDatePickerState(initialSelectedDateMillis = ReminderPolicy.datePickerMillis(initial.toLocalDate()))
    val timeState = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    var showTime by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    if (!showTime) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { showTime = true }, enabled = dateState.selectedDateMillis != null) { Text("选择时间") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
        ) { DatePicker(state = dateState) }
    } else {
        TimePickerDialog(
            onDismiss = onDismiss,
            onBack = { showTime = false },
            onConfirm = {
                try {
                    val millis = ReminderPolicy.toLocalInstant(
                        requireNotNull(dateState.selectedDateMillis), timeState.hour, timeState.minute, zone
                    )
                    ReminderPolicy.validate(millis, 0)
                    onSelected(millis)
                } catch (e: IllegalArgumentException) { error = e.message }
            }
        ) {
            var useKeyboard by rememberSaveable { mutableStateOf(false) }
            if (useKeyboard) TimeInput(state = timeState) else TimePicker(state = timeState)
            TextButton(onClick = { useKeyboard = !useKeyboard }) { Text(if (useKeyboard) "使用钟面" else "输入时间") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

/** 当前固定的 Material3 1.3.2 用官方文档模式封装 Dialog + TimePicker，非平台 TimePickerDialog。 */
@Composable
private fun TimePickerDialog(
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.padding(16.dp).widthIn(max = 400.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                Text("选择提醒时间", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                content()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onBack) { Text("上一步") }
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(onClick = onConfirm) { Text("确定") }
                }
            }
        }
    }
}
