package com.example.data.repository

import com.example.data.local.AttendanceDao
import com.example.data.local.LeaveDao
import com.example.data.model.AttendanceRecord
import com.example.data.model.LeaveBalance
import com.example.data.model.LeaveRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HRMSRepository(
    private val attendanceDao: AttendanceDao,
    private val leaveDao: LeaveDao
) {
    val allAttendance: Flow<List<AttendanceRecord>> = attendanceDao.getAllAttendance()
    val leaveBalances: Flow<List<LeaveBalance>> = leaveDao.getLeaveBalances()
    val leaveRequests: Flow<List<LeaveRequest>> = leaveDao.getLeaveRequests()

    fun getAttendanceForDateFlow(date: String): Flow<AttendanceRecord?> {
        return attendanceDao.getAttendanceForDateFlow(date)
    }

    suspend fun seedDefaultDataIfEmpty() {
        val currentBalances = leaveDao.getLeaveBalances().first()
        if (currentBalances.isEmpty()) {
            val defaults = listOf(
                LeaveBalance("Casual Leave", 12.0f, 0.0f),
                LeaveBalance("Sick Leave", 8.0f, 0.0f),
                LeaveBalance("Earned Leave", 15.0f, 0.0f)
            )
            leaveDao.insertLeaveBalances(defaults)

            // Seed some past attendance logs to make the summary graph/calendar look rich and realistic immediately
            seedPastAttendanceLogs()
        }
    }

    private suspend fun seedPastAttendanceLogs() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        
        // Let's seed some realistic attendance records for the last 15 days
        for (i in 1..20) {
            cal.time = java.util.Date()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            
            // Skip weekends for natural seeding, or make weekends "Weekly Off" (optional)
            if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
                continue
            }

            val dateStr = sdf.format(cal.time)
            
            // Simulating staggered punch in/out
            // Punch in around 09:00 AM - 09:30 AM
            cal.set(Calendar.HOUR_OF_DAY, 9)
            cal.set(Calendar.MINUTE, (10..40).random())
            val punchIn = cal.timeInMillis

            // Punch out around 05:30 PM - 06:30 PM
            cal.set(Calendar.HOUR_OF_DAY, 17)
            cal.set(Calendar.MINUTE, (30..55).random())
            val punchOut = cal.timeInMillis

            val diffMs = punchOut - punchIn
            val hrs = diffMs / (1000.0 * 60.0 * 60.0)

            val statusList = listOf("Present")
            // occasional sick leave seeded
            val status = if (i == 12) "On Leave" else "Present"

            val record = AttendanceRecord(
                date = dateStr,
                punchInTime = punchIn,
                punchOutTime = if (status == "Present") punchOut else null,
                punchInLat = 12.9716, // Bangalore default coordination
                punchInLng = 77.5946,
                punchInLoc = if (status == "Present") "Prestige Tech Park, Bangalore" else "N/A",
                punchOutLat = if (status == "Present") 12.9718 else null,
                punchOutLng = if (status == "Present") 77.5948 else null,
                punchOutLoc = if (status == "Present") "Prestige Tech Park, Bangalore" else null,
                workingHours = if (status == "Present") hrs else null,
                status = status
            )
            attendanceDao.insertAttendance(record)
        }

        // Also seed a couple of historical leave requests
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val pastCal = Calendar.getInstance()
        pastCal.add(Calendar.DAY_OF_YEAR, -13)
        val lStart = format.format(pastCal.time)
        pastCal.add(Calendar.DAY_OF_YEAR, 1)
        val lEnd = format.format(pastCal.time)

        val pastRequest = LeaveRequest(
            leaveType = "Sick Leave",
            startDate = lStart,
            endDate = lStart,
            numDays = 1.0f,
            reason = "Seasonal flu and doctor recommended rest",
            status = "Approved",
            appliedDate = System.currentTimeMillis() - 13L * 24 * 60 * 60 * 1000
        )
        leaveDao.insertLeaveRequest(pastRequest)
        
        // Update balance
        val sickBalance = leaveDao.getLeaveBalanceByType("Sick Leave")
        if (sickBalance != null) {
            leaveDao.updateLeaveBalance(sickBalance.copy(taken = sickBalance.taken + 1.0f))
        }
    }

    suspend fun punchIn(date: String, time: Long, lat: Double, lng: Double, loc: String) {
        val existing = attendanceDao.getAttendanceForDate(date)
        if (existing == null) {
            val record = AttendanceRecord(
                date = date,
                punchInTime = time,
                punchInLat = lat,
                punchInLng = lng,
                punchInLoc = loc,
                status = "Present"
            )
            attendanceDao.insertAttendance(record)
        } else {
            // Already punched in details but updating coordinates/time if allowed, or keep existing
            val updated = existing.copy(
                punchInTime = time,
                punchInLat = lat,
                punchInLng = lng,
                punchInLoc = loc
            )
            attendanceDao.insertAttendance(updated)
        }
    }

    suspend fun punchOut(date: String, time: Long, lat: Double, lng: Double, loc: String) {
        val existing = attendanceDao.getAttendanceForDate(date)
        if (existing != null) {
            val diffMs = time - existing.punchInTime
            val hrs = diffMs / (1000.0 * 60.0 * 60.0)
            val status = if (hrs < 4.0) "Half Day" else "Present"

            val updated = existing.copy(
                punchOutTime = time,
                punchOutLat = lat,
                punchOutLng = lng,
                punchOutLoc = loc,
                workingHours = hrs,
                status = status
            )
            attendanceDao.insertAttendance(updated)
        }
    }

    suspend fun applyLeave(
        leaveType: String,
        startDate: String,
        endDate: String,
        numDays: Float,
        reason: String
    ): Boolean {
        val balance = leaveDao.getLeaveBalanceByType(leaveType)
        if (balance != null && balance.remaining >= numDays) {
            // Update the taken count
            val updatedBalance = balance.copy(taken = balance.taken + numDays)
            leaveDao.updateLeaveBalance(updatedBalance)

            // Insert Request
            val request = LeaveRequest(
                leaveType = leaveType,
                startDate = startDate,
                endDate = endDate,
                numDays = numDays,
                reason = reason,
                status = "Approved", // Auto approved for prototype simulation
                appliedDate = System.currentTimeMillis()
            )
            leaveDao.insertLeaveRequest(request)

            // Log attendance records for those dates as "On Leave" so they show up beautifully in the logs
            markDatesAsOnLeave(startDate, endDate, leaveType)
            return true
        }
        return false
    }

    private suspend fun markDatesAsOnLeave(startDateStr: String, endDateStr: String, leaveType: String) {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        try {
            val startDate = format.parse(startDateStr) ?: return
            val endDate = format.parse(endDateStr) ?: return
            
            val cal = Calendar.getInstance()
            cal.time = startDate
            
            while (!cal.time.after(endDate)) {
                val dateStr = format.format(cal.time)
                val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                
                // Skip checking weekends for active punch logs, or just mark them
                if (dayOfWeek != Calendar.SATURDAY && dayOfWeek != Calendar.SUNDAY) {
                    val existing = attendanceDao.getAttendanceForDate(dateStr)
                    if (existing == null) {
                        val record = AttendanceRecord(
                            date = dateStr,
                            punchInTime = cal.timeInMillis, // Dummy
                            punchInLat = 0.0,
                            punchInLng = 0.0,
                            punchInLoc = "On Leave ($leaveType)",
                            status = "On Leave"
                        )
                        attendanceDao.insertAttendance(record)
                    } else {
                        // Override/update status
                        attendanceDao.insertAttendance(existing.copy(status = "On Leave"))
                    }
                }
                cal.add(Calendar.DAY_OF_MONTH, 1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun clearAllData() {
        attendanceDao.clearAll()
        leaveDao.clearRequests()
        leaveDao.clearBalances()
        seedDefaultDataIfEmpty()
    }
}
