package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "leave_balances")
data class LeaveBalance(
    @PrimaryKey val id: String, // format: "email_leaveType"
    val employeeEmail: String = "hemant.k@allen.in",
    val leaveType: String, // Casual Leave, Sick Leave, Earned Leave, etc.
    val allocated: Float,
    val taken: Float
) {
    val remaining: Float
        get() = allocated - taken
}
