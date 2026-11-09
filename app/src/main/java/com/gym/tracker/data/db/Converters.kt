package com.gym.tracker.data.db

import androidx.room.TypeConverter
import java.time.LocalDate

class Converters {
    /** ISO yyyy-MM-dd: сортируется как строка в хронологическом порядке. */
    @TypeConverter
    fun dateToString(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun stringToDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun feelToString(feel: Feel?): String? = feel?.name

    @TypeConverter
    fun stringToFeel(value: String?): Feel? = value?.let(Feel::valueOf)
}
