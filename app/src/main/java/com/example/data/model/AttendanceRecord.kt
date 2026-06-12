package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attendance_records",
    indices = [Index(value = ["date"], unique = true)]
)
data class AttendanceRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String, // format: YYYY-MM-DD
    val punchInTime: Long, // timestamp
    val punchOutTime: Long? = null, // timestamp
    val punchInLat: Double,
    val punchInLng: Double,
    val punchInLoc: String,
    val punchOutLat: Double? = null,
    val punchOutLng: Double? = null,
    val punchOutLoc: String? = null,
    val workingHours: Double? = null, // in hours
    val status: String = "Present" // Present, Half Day, Absent, On Leave
)
