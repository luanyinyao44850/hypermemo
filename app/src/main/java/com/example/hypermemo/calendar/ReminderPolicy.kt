package com.example.hypermemo.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

object ReminderPolicy {
    fun validate(start: Long, minutes: Int, now: Long = System.currentTimeMillis()) {
        require(minutes in 0..10080) { "提前分钟数须在 0～10080 之间。" }
        require(start > now) { "请选择未来的日程时间。" }
        require(start - minutes * 60_000L > now) { "实际提醒时刻已经过去，请减少提前分钟数或选择更晚的时间。" }
    }

    // Material3 DatePicker 的毫秒数表示 UTC 日期，不能直接当作本地午夜。
    fun datePickerMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun toLocalInstant(utcDate: Long, hour: Int, minute: Int, zone: ZoneId): Long {
        val date = Instant.ofEpochMilli(utcDate).atZone(ZoneOffset.UTC).toLocalDate()
        val local = LocalDateTime.of(date, LocalTime.of(hour, minute))
        val offsets = zone.rules.getValidOffsets(local)
        require(offsets.isNotEmpty()) { "该时刻处于夏令时跳变区间，请重新选择。" }
        // 秋季重复时间选择较早的一次；UI 会显示最终的本地日期时间。
        return local.toInstant(offsets.first()).toEpochMilli()
    }
}
