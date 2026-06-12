package com.example.data.local

import androidx.room.*
import com.example.data.model.LeaveBalance
import com.example.data.model.LeaveRequest
import kotlinx.coroutines.flow.Flow

@Dao
interface LeaveDao {
    @Query("SELECT * FROM leave_balances")
    fun getLeaveBalances(): Flow<List<LeaveBalance>>

    @Query("SELECT * FROM leave_balances WHERE leaveType = :leaveType LIMIT 1")
    suspend fun getLeaveBalanceByType(leaveType: String): LeaveBalance?

    @Query("SELECT * FROM leave_requests ORDER BY startDate DESC")
    fun getLeaveRequests(): Flow<List<LeaveRequest>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLeaveRequest(request: LeaveRequest)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLeaveBalances(balances: List<LeaveBalance>)

    @Update
    suspend fun updateLeaveBalance(balance: LeaveBalance)

    @Query("DELETE FROM leave_requests")
    suspend fun clearRequests()

    @Query("DELETE FROM leave_balances")
    suspend fun clearBalances()
}
