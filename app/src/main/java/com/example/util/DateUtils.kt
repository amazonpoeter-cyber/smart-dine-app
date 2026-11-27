package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class WeekdaySlot(val shortLabel: String, val fullLabel: String, val calendarDay: Int) {
    NONE("None", "No Fixed Off", -1),
    MONDAY("Mon", "Monday", Calendar.MONDAY),
    TUESDAY("Tue", "Tuesday", Calendar.TUESDAY),
    WEDNESDAY("Wed", "Wednesday", Calendar.WEDNESDAY),
    THURSDAY("Thu", "Thursday", Calendar.THURSDAY),
    FRIDAY("Fri", "Friday", Calendar.FRIDAY),
    SATURDAY("Sat", "Saturday", Calendar.SATURDAY),
    SUNDAY("Sun", "Sunday", Calendar.SUNDAY)
}

object DateUtils {
    private val isoFormat get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val monthFormat get() = SimpleDateFormat("yyyy-MM", Locale.US)
    private val friendlyDateFormat get() = SimpleDateFormat("MMM dd, yyyy", Locale.US)
    private val friendlyMonthFormat get() = SimpleDateFormat("MMMM yyyy", Locale.US)
    private val timeFormat get() = SimpleDateFormat("hh:mm a", Locale.US)

    fun todayIso(): String = isoFormat.format(Date())

    fun daysAgoIso(daysAgo: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        return isoFormat.format(cal.time)
    }

    fun currentMonthIso(): String = monthFormat.format(Date())

    fun previousMonthIso(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -1)
        return monthFormat.format(cal.time)
    }

    fun currentTimeLabel(): String = timeFormat.format(Date())

    fun formatFriendlyDate(isoDate: String): String {
        val parsed = runCatching { isoFormat.parse(isoDate) }.getOrNull() ?: return isoDate
        return friendlyDateFormat.format(parsed)
    }

    fun formatFriendlyMonth(isoMonth: String): String {
        val parsed = runCatching { monthFormat.parse(isoMonth) }.getOrNull() ?: return isoMonth
        return friendlyMonthFormat.format(parsed)
    }

    fun millisToIsoDate(utcMillis: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = utcMillis
        return isoFormat.format(cal.time)
    }

    fun weekdayForIso(isoDate: String): WeekdaySlot {
        val parsed = runCatching { isoFormat.parse(isoDate) }.getOrNull() ?: return WeekdaySlot.NONE
        val cal = Calendar.getInstance().apply { time = parsed }
        val day = cal.get(Calendar.DAY_OF_WEEK)
        return WeekdaySlot.entries.firstOrNull { it.calendarDay == day } ?: WeekdaySlot.NONE
    }

    fun monthDaysInCurrentMonth(): List<String> {
        val cal = Calendar.getInstance()
        val todayDay = cal.get(Calendar.DAY_OF_MONTH)
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)
        return (1..todayDay).map { day ->
            val dayCal = Calendar.getInstance().apply {
                set(year, month, day)
            }
            isoFormat.format(dayCal.time)
        }
    }
}
