package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "leave_requests")
data class LeaveRequest(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val employeeEmail: String = "hemant.k@allen.in",
    val leaveType: String,
    val startDate: String, // format: YYYY-MM-DD
    val endDate: String, // format: YYYY-MM-DD
    val numDays: Float,
    val reason: String,
    val status: String = "Pending", // Default is now Pending for admin management
    val appliedDate: Long = System.currentTimeMillis()
)
