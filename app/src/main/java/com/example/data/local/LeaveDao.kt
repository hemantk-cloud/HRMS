package com.example.data.local

import androidx.room.*
import com.example.data.model.LeaveBalance
import com.example.data.model.LeaveRequest
import kotlinx.coroutines.flow.Flow

@Dao
interface LeaveDao {
    @Query("SELECT * FROM leave_balances")
    fun getLeaveBalances(): Flow<List<LeaveBalance>>

    @Query("SELECT * FROM leave_balances WHERE employeeEmail = :email")
    fun getLeaveBalancesForEmployee(email: String): Flow<List<LeaveBalance>>

    @Query("SELECT * FROM leave_balances WHERE leaveType = :leaveType LIMIT 1")
    suspend fun getLeaveBalanceByType(leaveType: String): LeaveBalance?

    @Query("SELECT * FROM leave_balances WHERE employeeEmail = :email AND leaveType = :leaveType LIMIT 1")
    suspend fun getLeaveBalanceForEmployeeAndType(email: String, leaveType: String): LeaveBalance?

    @Query("SELECT * FROM leave_requests ORDER BY startDate DESC")
    fun getLeaveRequests(): Flow<List<LeaveRequest>>

    @Query("SELECT * FROM leave_requests WHERE employeeEmail = :email ORDER BY startDate DESC")
    fun getLeaveRequestsForEmployee(email: String): Flow<List<LeaveRequest>>

    @Query("UPDATE leave_requests SET status = :status WHERE id = :id")
    suspend fun updateLeaveRequestStatus(id: Int, status: String)

    @Query("SELECT * FROM leave_requests WHERE id = :id LIMIT 1")
    suspend fun getLeaveRequestById(id: Int): LeaveRequest?

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
