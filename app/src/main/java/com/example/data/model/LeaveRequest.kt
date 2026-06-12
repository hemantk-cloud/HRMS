package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "leave_requests")
data class LeaveRequest(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val leaveType: String,
    val startDate: String, // format: YYYY-MM-DD
    val endDate: String, // format: YYYY-MM-DD
    val numDays: Float,
    val reason: String,
    val status: String = "Approved", // Pending, Approved, Rejected (automatically Approved in our self-service app simulation)
    val appliedDate: Long = System.currentTimeMillis()
)
