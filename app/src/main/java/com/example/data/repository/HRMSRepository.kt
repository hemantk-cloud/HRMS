package com.example.data.repository

import com.example.data.local.AttendanceDao
import com.example.data.local.LeaveDao
import com.example.data.local.EmployeeDao
import com.example.data.model.AttendanceRecord
import com.example.data.model.LeaveBalance
import com.example.data.model.LeaveRequest
import com.example.data.model.Employee
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HRMSRepository(
    private val attendanceDao: AttendanceDao,
    private val leaveDao: LeaveDao,
    private val employeeDao: EmployeeDao
) {
    val allAttendance: Flow<List<AttendanceRecord>> = attendanceDao.getAllAttendance()
    val allLeaveRequests: Flow<List<LeaveRequest>> = leaveDao.getLeaveRequests()
    val allEmployees: Flow<List<Employee>> = employeeDao.getAllEmployees()
    val allLeaveBalances: Flow<List<LeaveBalance>> = leaveDao.getLeaveBalances()

    fun getAttendanceForEmployee(email: String): Flow<List<AttendanceRecord>> {
        return attendanceDao.getAttendanceForEmployee(email)
    }

    fun getLeaveBalancesForEmployee(email: String): Flow<List<LeaveBalance>> {
        return leaveDao.getLeaveBalancesForEmployee(email)
    }

    fun getLeaveRequestsForEmployee(email: String): Flow<List<LeaveRequest>> {
        return leaveDao.getLeaveRequestsForEmployee(email)
    }

    fun getAttendanceForDateAndEmailFlow(email: String, date: String): Flow<AttendanceRecord?> {
        return attendanceDao.getAttendanceForDateAndEmailFlow(email, date)
    }

    suspend fun getEmployeeByEmail(email: String): Employee? {
        return employeeDao.getEmployeeByEmail(email)
    }

    suspend fun addEmployee(employee: Employee) {
        employeeDao.insertEmployee(employee)
        // Automatically provision leave balances for the new employee
        val defaultBalances = listOf(
            LeaveBalance("${employee.email}_Privilege Leave", employee.email, "Privilege Leave", 15.0f, 0.0f),
            LeaveBalance("${employee.email}_Compensatory Off", employee.email, "Compensatory Off", 0.0f, 0.0f)
        )
        leaveDao.insertLeaveBalances(defaultBalances)
    }

    suspend fun deleteEmployee(employee: Employee) {
        employeeDao.deleteEmployee(employee)
    }

    suspend fun seedDefaultDataIfEmpty() {
        val adminEmail = "hemant.k@allen.in"
        val existingAdmin = employeeDao.getEmployeeByEmail(adminEmail)
        if (existingAdmin == null) {
            // 1. Insert Admin
            val admin = Employee(
                email = adminEmail,
                name = "Hemant Gurjar",
                department = "HR & Tech Architecture",
                emId = "ALLEN-984",
                isAdmin = true
            )
            employeeDao.insertEmployee(admin)
            
            // Seed Admin Leave Balances
            val adminBalances = listOf(
                LeaveBalance("hemant.k@allen.in_Privilege Leave", adminEmail, "Privilege Leave", 15.0f, 0.0f),
                LeaveBalance("hemant.k@allen.in_Compensatory Off", adminEmail, "Compensatory Off", 0.0f, 0.0f)
            )
            leaveDao.insertLeaveBalances(adminBalances)

            // 2. Insert other employees
            val emp1 = Employee(
                email = "developer@allen.in",
                name = "Aarav Sharma",
                department = "Android Development",
                emId = "ALLEN-102",
                isAdmin = false
            )
            val emp2 = Employee(
                email = "designer@allen.in",
                name = "Isha Patel",
                department = "UI/UX Design",
                emId = "ALLEN-105",
                isAdmin = false
            )
            employeeDao.insertEmployee(emp1)
            employeeDao.insertEmployee(emp2)

            // Seed balances for them
            val emp1Balances = listOf(
                LeaveBalance("developer@allen.in_Privilege Leave", "developer@allen.in", "Privilege Leave", 15.0f, 1.5f),
                LeaveBalance("developer@allen.in_Compensatory Off", "developer@allen.in", "Compensatory Off", 2.0f, 0.0f)
            )
            val emp2Balances = listOf(
                LeaveBalance("designer@allen.in_Privilege Leave", "designer@allen.in", "Privilege Leave", 15.0f, 0.0f),
                LeaveBalance("designer@allen.in_Compensatory Off", "designer@allen.in", "Compensatory Off", 0.0f, 0.0f)
            )
            leaveDao.insertLeaveBalances(emp1Balances)
            leaveDao.insertLeaveBalances(emp2Balances)

            // Seed past logs for admin & employees as well to show rich stats!
            seedPastAttendanceLogs(adminEmail)
            seedPastAttendanceLogs("developer@allen.in")

            // Seed a Sunday attendance for developer to practice rewarding Compensatory Off
            val sDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val sunCal = Calendar.getInstance()
            sunCal.time = java.util.Date()
            while (sunCal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
                sunCal.add(Calendar.DAY_OF_YEAR, -1)
            }
            val sundayDateStr = sDateFormat.format(sunCal.time)
            
            sunCal.set(Calendar.HOUR_OF_DAY, 9)
            sunCal.set(Calendar.MINUTE, 0)
            val sunPunchIn = sunCal.timeInMillis
            
            sunCal.set(Calendar.HOUR_OF_DAY, 17)
            sunCal.set(Calendar.MINUTE, 30)
            val sunPunchOut = sunCal.timeInMillis
            
            val sunRecord = AttendanceRecord(
                employeeEmail = "developer@allen.in",
                date = sundayDateStr,
                punchInTime = sunPunchIn,
                punchOutTime = sunPunchOut,
                punchInLat = 12.9716,
                punchInLng = 77.5946,
                punchInLoc = "Prestige Tech Park, Bangalore",
                punchOutLat = 12.9718,
                punchOutLng = 77.5948,
                punchOutLoc = "Prestige Tech Park, Bangalore",
                workingHours = 8.5,
                status = "Present"
            )
            attendanceDao.insertAttendance(sunRecord)
            
            // Seed a past pending Leave Request for developer to let admin practice approval
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val pastCal = Calendar.getInstance()
            pastCal.add(Calendar.DAY_OF_YEAR, 2)
            val lStart = format.format(pastCal.time)
            pastCal.add(Calendar.DAY_OF_YEAR, 1)
            val lEnd = format.format(pastCal.time)

            val pendingRequest = LeaveRequest(
                employeeEmail = "developer@allen.in",
                leaveType = "Privilege Leave",
                startDate = lStart,
                endDate = lEnd,
                numDays = 2.0f,
                reason = "Family gathering at home town",
                status = "Pending",
                appliedDate = System.currentTimeMillis()
            )
            leaveDao.insertLeaveRequest(pendingRequest)
        }
    }

    private suspend fun seedPastAttendanceLogs(email: String) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        
        for (i in 1..15) {
            cal.time = java.util.Date()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            
            if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
                continue
            }

            val dateStr = sdf.format(cal.time)
            
            cal.set(Calendar.HOUR_OF_DAY, 9)
            cal.set(Calendar.MINUTE, (10..40).random())
            val punchIn = cal.timeInMillis

            cal.set(Calendar.HOUR_OF_DAY, 17)
            cal.set(Calendar.MINUTE, (30..55).random())
            val punchOut = cal.timeInMillis

            val diffMs = punchOut - punchIn
            val hrs = diffMs / (1000.0 * 60.0 * 60.0)

            val status = "Present"

            val record = AttendanceRecord(
                employeeEmail = email,
                date = dateStr,
                punchInTime = punchIn,
                punchOutTime = punchOut,
                punchInLat = 12.9716,
                punchInLng = 77.5946,
                punchInLoc = "Prestige Tech Park, Bangalore",
                punchOutLat = 12.9718,
                punchOutLng = 77.5948,
                punchOutLoc = "Prestige Tech Park, Bangalore",
                workingHours = hrs,
                status = status
            )
            attendanceDao.insertAttendance(record)
        }
    }

    suspend fun punchIn(email: String, date: String, time: Long, lat: Double, lng: Double, loc: String) {
        val existing = attendanceDao.getAttendanceForDateAndEmail(email, date)
        if (existing == null) {
            val record = AttendanceRecord(
                employeeEmail = email,
                date = date,
                punchInTime = time,
                punchInLat = lat,
                punchInLng = lng,
                punchInLoc = loc,
                status = "Present"
            )
            attendanceDao.insertAttendance(record)
        } else {
            val updated = existing.copy(
                punchInTime = time,
                punchInLat = lat,
                punchInLng = lng,
                punchInLoc = loc
            )
            attendanceDao.insertAttendance(updated)
        }
    }

    suspend fun punchOut(email: String, date: String, time: Long, lat: Double, lng: Double, loc: String) {
        val existing = attendanceDao.getAttendanceForDateAndEmail(email, date)
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
        email: String,
        leaveType: String,
        startDate: String,
        endDate: String,
        numDays: Float,
        reason: String
    ): Boolean {
        val balance = leaveDao.getLeaveBalanceForEmployeeAndType(email, leaveType)
        if (balance != null && balance.remaining >= numDays) {
            val updatedBalance = balance.copy(taken = balance.taken + numDays)
            leaveDao.updateLeaveBalance(updatedBalance)

            val request = LeaveRequest(
                employeeEmail = email,
                leaveType = leaveType,
                startDate = startDate,
                endDate = endDate,
                numDays = numDays,
                reason = reason,
                status = "Pending",
                appliedDate = System.currentTimeMillis()
            )
            leaveDao.insertLeaveRequest(request)
            return true
        }
        return false
    }

    suspend fun updateLeaveStatus(id: Int, status: String) {
        val request = leaveDao.getLeaveRequestById(id) ?: return
        val currentStatus = request.status
        leaveDao.updateLeaveRequestStatus(id, status)
        
        if (status == "Approved") {
            markDatesAsOnLeave(request.employeeEmail, request.startDate, request.endDate, request.leaveType)
        } else if (status == "Rejected" && currentStatus != "Rejected") {
            // Restore leave balance
            val balance = leaveDao.getLeaveBalanceForEmployeeAndType(request.employeeEmail, request.leaveType)
            if (balance != null) {
                leaveDao.updateLeaveBalance(balance.copy(taken = maxOf(0.0f, balance.taken - request.numDays)))
            }
        }
    }

    private suspend fun markDatesAsOnLeave(email: String, startDateStr: String, endDateStr: String, leaveType: String) {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        try {
            val startDate = format.parse(startDateStr) ?: return
            val endDate = format.parse(endDateStr) ?: return
            
            val cal = Calendar.getInstance()
            cal.time = startDate
            
            while (!cal.time.after(endDate)) {
                val dateStr = format.format(cal.time)
                val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                
                if (dayOfWeek != Calendar.SATURDAY && dayOfWeek != Calendar.SUNDAY) {
                    val existing = attendanceDao.getAttendanceForDateAndEmail(email, dateStr)
                    if (existing == null) {
                        val record = AttendanceRecord(
                            employeeEmail = email,
                            date = dateStr,
                            punchInTime = cal.timeInMillis,
                            punchInLat = 0.0,
                            punchInLng = 0.0,
                            punchInLoc = "On Leave ($leaveType)",
                            status = "On Leave"
                        )
                        attendanceDao.insertAttendance(record)
                    } else {
                        attendanceDao.insertAttendance(existing.copy(status = "On Leave"))
                    }
                }
                cal.add(Calendar.DAY_OF_MONTH, 1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun updateEmployeeLeaveBalances(email: String, privilegeAllocated: Float, compAllocated: Float) {
        val privKey = "${email}_Privilege Leave"
        val compKey = "${email}_Compensatory Off"

        val existingPriv = leaveDao.getLeaveBalanceForEmployeeAndType(email, "Privilege Leave")
        val updatedPriv = if (existingPriv != null) {
            existingPriv.copy(allocated = privilegeAllocated)
        } else {
            LeaveBalance(privKey, email, "Privilege Leave", privilegeAllocated, 0.0f)
        }

        val existingComp = leaveDao.getLeaveBalanceForEmployeeAndType(email, "Compensatory Off")
        val updatedComp = if (existingComp != null) {
            existingComp.copy(allocated = compAllocated)
        } else {
            LeaveBalance(compKey, email, "Compensatory Off", compAllocated, 0.0f)
        }

        leaveDao.insertLeaveBalances(listOf(updatedPriv, updatedComp))
    }

    suspend fun awardCompensatoryOff(email: String, daysToAdd: Float) {
        val existingComp = leaveDao.getLeaveBalanceForEmployeeAndType(email, "Compensatory Off")
        val updatedComp = if (existingComp != null) {
            existingComp.copy(allocated = existingComp.allocated + daysToAdd)
        } else {
            val compKey = "${email}_Compensatory Off"
            LeaveBalance(compKey, email, "Compensatory Off", daysToAdd, 0.0f)
        }
        leaveDao.insertLeaveBalances(listOf(updatedComp))
    }

    suspend fun clearAllData() {
        attendanceDao.clearAll()
        leaveDao.clearRequests()
        leaveDao.clearBalances()
        employeeDao.clearAllEmployees()
        seedDefaultDataIfEmpty()
    }
}
