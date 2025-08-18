package com.example.kanjireader.database

import androidx.room.TypeConverter
import com.example.kanjireader.VerbType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Type converters for Room database to handle complex data types.
 */
class Converters {
    private val gson = Gson()
    
    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return value?.let { gson.toJson(it) }
    }
    
    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        return value?.let {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(it, type)
        }
    }
    
    @TypeConverter
    fun fromVerbType(value: VerbType?): String? {
        return value?.name
    }
    
    @TypeConverter
    fun toVerbType(value: String?): VerbType? {
        return value?.let {
            try {
                VerbType.valueOf(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}