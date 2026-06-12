package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "employees")
data class Employee(
    @PrimaryKey val email: String,
    val name: String,
    val department: String,
    val emId: String,
    val isAdmin: Boolean = false
)
