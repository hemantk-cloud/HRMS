package com.example.ui.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.AttendanceRecord
import com.example.data.model.LeaveBalance
import com.example.data.model.LeaveRequest
import com.example.data.model.Employee
import com.example.data.repository.HRMSRepository
import com.google.android.gms.location.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class HRMSViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = HRMSRepository(database.attendanceDao(), database.leaveDao(), database.employeeDao())
    private val sharedPrefs = application.getSharedPreferences("allen_hr_prefs", Context.MODE_PRIVATE)

    // Logged in employee state
    private val _loggedInUser = MutableStateFlow<Employee?>(null)
    val loggedInUser: StateFlow<Employee?> = _loggedInUser.asStateFlow()

    // Query all employees for admin screen
    val allEmployeesList: StateFlow<List<Employee>> = repository.allEmployees
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allLeaveBalancesList: StateFlow<List<LeaveBalance>> = repository.allLeaveBalances
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All records observed in UI dynamically depending on logged in user
    val allAttendance: StateFlow<List<AttendanceRecord>> = _loggedInUser
        .flatMapLatest { user ->
            if (user == null) flowOf(emptyList())
            else if (user.isAdmin) repository.allAttendance
            else repository.getAttendanceForEmployee(user.email)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val leaveBalances: StateFlow<List<LeaveBalance>> = _loggedInUser
        .flatMapLatest { user ->
            if (user == null) flowOf(emptyList())
            else repository.getLeaveBalancesForEmployee(user.email)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All leave requests dynamically filtered: admins see all, regular employees see theirs
    val leaveRequests: StateFlow<List<LeaveRequest>> = _loggedInUser
        .flatMapLatest { user ->
            if (user == null) flowOf(emptyList())
            else if (user.isAdmin) repository.allLeaveRequests
            else repository.getLeaveRequestsForEmployee(user.email)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Date management
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val todayDateString: String = dateFormatter.format(Date())

    // Today's attendance record dynamically mapped to currently active logged in user
    val todayAttendance: StateFlow<AttendanceRecord?> = _loggedInUser
        .flatMapLatest { user ->
            if (user == null) flowOf(null)
            else repository.getAttendanceForDateAndEmailFlow(user.email, todayDateString)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Selection filters
    private val _selectedMonth = MutableStateFlow(SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date()))
    val selectedMonth: StateFlow<String> = _selectedMonth.asStateFlow()

    // Monthly summary stats computed reactively
    val monthlyStats = combine(allAttendance, selectedMonth, _loggedInUser) { attendanceList, month, user ->
        val filteredList = if (user != null && !user.isAdmin) {
            attendanceList.filter { it.employeeEmail == user.email }
        } else {
            attendanceList
        }
        val monthRecords = filteredList.filter { it.date.startsWith(month) }
        val presentCount = monthRecords.count { it.status == "Present" }
        val halfDayCount = monthRecords.count { it.status == "Half Day" }
        val leaveCount = monthRecords.count { it.status == "On Leave" }
        val totalHours = monthRecords.sumOf { it.workingHours ?: 0.0 }
        
        MonthlyStats(
            presentDays = presentCount,
            halfDays = halfDayCount,
            leaveDays = leaveCount,
            totalWorkHours = totalHours,
            records = monthRecords
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MonthlyStats())

    // GPS & Location Status states
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(application)

    private val _currentLocationState = MutableStateFlow<LocationState>(LocationState.Idle)
    val currentLocationState: StateFlow<LocationState> = _currentLocationState.asStateFlow()

    // List of simulated office coordinates & residential coordinate options
    val officeLocations = listOf(
        OfficeLocation("Prestige Tech Park (Bangalore Main HQ)", 12.9348, 77.6931),
        OfficeLocation("Silicon Valley Hub (San Jose Office)", 37.3382, -121.8863),
        OfficeLocation("DLF CyberCity (Delhi National Office)", 28.4962, 77.0878),
        OfficeLocation("Home / Work from Home (Remote)", 12.9716, 77.5946)
    )

    private val _selectedSimulationLocation = MutableStateFlow(officeLocations.first())
    val selectedSimulationLocation: StateFlow<OfficeLocation> = _selectedSimulationLocation.asStateFlow()

    // Real-time ticking stopwatch for punch hours
    private val _currentWorkSessionTicker = MutableStateFlow<String?>("00:00:00")
    val currentWorkSessionTicker: StateFlow<String?> = _currentWorkSessionTicker.asStateFlow()

    // Feedback notifications
    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage = _toastMessage.asSharedFlow()

    init {
        viewModelScope.launch {
            // Seed base parameters on startup
            repository.seedDefaultDataIfEmpty()
            
            // Check for saved local login session
            val savedEmail = sharedPrefs.getString("logged_in_email", null)
            if (savedEmail != null) {
                val emp = repository.getEmployeeByEmail(savedEmail)
                if (emp != null) {
                    _loggedInUser.value = emp
                }
            }
            
            // Pull initial simulated location right away
            requestDeviceLocation()
        }

        // Start ongoing session timer loop
        viewModelScope.launch {
            while (true) {
                delay(1000)
                updateSessionStopwatch()
            }
        }
    }

    private fun updateSessionStopwatch() {
        val today = todayAttendance.value
        if (today != null && today.punchOutTime == null) {
            val liveMs = System.currentTimeMillis() - today.punchInTime
            if (liveMs > 0) {
                val totalSecs = liveMs / 1000
                val h = totalSecs / 3600
                val m = (totalSecs % 3600) / 60
                val s = totalSecs % 60
                _currentWorkSessionTicker.value = String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
                return
            }
        }
        _currentWorkSessionTicker.value = null
    }

    // Auth actions
    fun loginWithEmail(email: String, onResult: (Boolean, String) -> Unit) {
        val normalizedEmail = email.trim().lowercase()
        if (normalizedEmail.isEmpty()) {
            onResult(false, "Please enter a valid email address.")
            return
        }
        
        viewModelScope.launch {
            val employee = repository.getEmployeeByEmail(normalizedEmail)
            if (employee != null) {
                _loggedInUser.value = employee
                sharedPrefs.edit().putString("logged_in_email", normalizedEmail).apply()
                onResult(true, "Successfully logged in as ${employee.name}!")
            } else {
                onResult(false, "Official Email is not registered. Please contact Admin.")
            }
        }
    }

    fun logout() {
        _loggedInUser.value = null
        sharedPrefs.edit().remove("logged_in_email").apply()
        viewModelScope.launch {
            _toastMessage.emit("Successfully logged out.")
        }
    }

    // Admin Actions
    fun registerNewEmployee(name: String, email: String, department: String, emId: String, isAdmin: Boolean) {
        val cleanEmail = email.trim().lowercase()
        if (name.isEmpty() || cleanEmail.isEmpty() || department.isEmpty() || emId.isEmpty()) {
            viewModelScope.launch { _toastMessage.emit("Error: All fields are required.") }
            return
        }
        viewModelScope.launch {
            val existing = repository.getEmployeeByEmail(cleanEmail)
            if (existing != null) {
                _toastMessage.emit("Error: Email already registered.")
            } else {
                val newEmp = Employee(
                    email = cleanEmail,
                    name = name.trim(),
                    department = department.trim(),
                    emId = emId.trim(),
                    isAdmin = isAdmin
                )
                repository.addEmployee(newEmp)
                _toastMessage.emit("Employee ${name} registered successfully!")
            }
        }
    }

    fun deleteEmployeeByAdmin(employee: Employee) {
        if (employee.email == _loggedInUser.value?.email) {
            viewModelScope.launch { _toastMessage.emit("Error: Cannot delete yourself.") }
            return
        }
        viewModelScope.launch {
            repository.deleteEmployee(employee)
            _toastMessage.emit("Employee ${employee.name} removed.")
        }
    }

    fun handleLeaveApprovalByAdmin(id: Int, approve: Boolean) {
        viewModelScope.launch {
            val status = if (approve) "Approved" else "Rejected"
            repository.updateLeaveStatus(id, status)
            _toastMessage.emit("Leave request was $status!")
        }
    }

    fun updateEmployeeLeaveBalances(email: String, privilegeAllocated: Float, compAllocated: Float) {
        viewModelScope.launch {
            repository.updateEmployeeLeaveBalances(email, privilegeAllocated, compAllocated)
            _toastMessage.emit("Successfully updated leave balances for $email.")
        }
    }

    fun awardCompensatoryOff(email: String, daysToAdd: Float, dateStr: String) {
        viewModelScope.launch {
            repository.awardCompensatoryOff(email, daysToAdd)
            _toastMessage.emit("Successfully awarded $daysToAdd Comp Off to $email for Sunday duty on $dateStr.")
        }
    }

    // Export to Excel/CSV
    fun exportDataToExcel(onCompleted: (File?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val csvFile = File(getApplication<Application>().cacheDir, "allen_hrms_attendance_logs.csv")
                val writer = FileWriter(csvFile)
                
                // Write Header
                writer.append("Employee Email,Employee ID,Date,Punch In Time,Punch Out Time,Punch In Location,Punch Out Location,Total Working Hours,Status\n")
                
                // Fetch current details
                val attendanceLogs = repository.allAttendance.first()
                val employees = repository.allEmployees.first().associateBy { it.email }
                
                for (log in attendanceLogs) {
                    val emp = employees[log.employeeEmail]
                    val empId = emp?.emId ?: "N/A"
                    val sdf = SimpleDateFormat("yyyy-MM-dd hh:mm:ss a", Locale.getDefault())
                    val inTime = sdf.format(Date(log.punchInTime))
                    val outTime = log.punchOutTime?.let { sdf.format(Date(it)) } ?: "N/A"
                    val hrsStr = log.workingHours?.let { String.format(Locale.getDefault(), "%.2f", it) } ?: "0.00"
                    
                    writer.append("\"${log.employeeEmail}\",")
                    writer.append("\"$empId\",")
                    writer.append("\"${log.date}\",")
                    writer.append("\"$inTime\",")
                    writer.append("\"$outTime\",")
                    writer.append("\"${log.punchInLoc}\",")
                    writer.append("\"${log.punchOutLoc ?: "N/A"}\",")
                    writer.append("\"$hrsStr\",")
                    writer.append("\"${log.status}\"\n")
                }
                
                writer.flush()
                writer.close()
                
                withContext(Dispatchers.Main) {
                    onCompleted(csvFile)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onCompleted(null)
                }
            }
        }
    }

    fun selectMonth(month: String) {
        _selectedMonth.value = month
    }

    fun selectSimulationLocation(location: OfficeLocation) {
        _selectedSimulationLocation.value = location
        _currentLocationState.value = LocationState.Success(
            lat = location.lat,
            lng = location.lng,
            address = location.name
        )
    }

    @SuppressLint("MissingPermission")
    fun requestDeviceLocation() {
        _currentLocationState.value = LocationState.Locating
        viewModelScope.launch {
            try {
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location: Location? ->
                        if (location != null) {
                            resolveGeocodedAddress(location.latitude, location.longitude)
                        } else {
                            requestSingleLocationUpdate()
                        }
                    }
                    .addOnFailureListener {
                        useFallbackSimulationLocation("Failed to secure GPS. Using Office Sim.")
                    }
            } catch (e: SecurityException) {
                useFallbackSimulationLocation("Permission restricted. Using Office Sim.")
            } catch (e: Exception) {
                useFallbackSimulationLocation("GPS Offline. Using Office Sim.")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestSingleLocationUpdate() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 5000)
            .setMaxUpdates(1)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation
                if (loc != null) {
                    resolveGeocodedAddress(loc.latitude, loc.longitude)
                } else {
                    useFallbackSimulationLocation("Location result empty. Using Office Sim.")
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: Exception) {
            useFallbackSimulationLocation("Failed single update. Using Office Sim.")
        }
    }

    private fun useFallbackSimulationLocation(reason: String) {
        val sim = _selectedSimulationLocation.value
        _currentLocationState.value = LocationState.Success(
            lat = sim.lat,
            lng = sim.lng,
            address = sim.name
        )
        Log.d("HRMSViewModel", "Location Fallback triggered: $reason. Mocking: ${sim.name}")
    }

    private fun resolveGeocodedAddress(lat: Double, lng: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            var resolvedAddress = "Latitude: %.4f, Longitude: %.4f".format(lat, lng)
            try {
                @Suppress("DEPRECATION")
                val geocoder = Geocoder(getApplication(), Locale.getDefault())
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    resolvedAddress = address.getAddressLine(0) ?: address.locality ?: resolvedAddress
                }
            } catch (e: Exception) {
                resolvedAddress = "HQ Campus Bangalore (GPS Match: %.4f, %.4f)".format(lat, lng)
            }

            withContext(Dispatchers.Main) {
                _currentLocationState.value = LocationState.Success(
                    lat = lat,
                    lng = lng,
                    address = resolvedAddress
                )
            }
        }
    }

    // Actions
    fun punchInToday() {
        val user = _loggedInUser.value ?: return
        val loc = _currentLocationState.value
        if (loc is LocationState.Success) {
            viewModelScope.launch {
                repository.punchIn(
                    email = user.email,
                    date = todayDateString,
                    time = System.currentTimeMillis(),
                    lat = loc.lat,
                    lng = loc.lng,
                    loc = loc.address
                )
                _toastMessage.emit("Successfully Punched In!")
            }
        } else {
            viewModelScope.launch {
                val fallbackSim = _selectedSimulationLocation.value
                repository.punchIn(
                    email = user.email,
                    date = todayDateString,
                    time = System.currentTimeMillis(),
                    lat = fallbackSim.lat,
                    lng = fallbackSim.lng,
                    loc = fallbackSim.name
                )
                _toastMessage.emit("Punched In with Sim Location!")
            }
        }
    }

    fun punchOutToday() {
        val user = _loggedInUser.value ?: return
        val loc = _currentLocationState.value
        if (loc is LocationState.Success) {
            viewModelScope.launch {
                repository.punchOut(
                    email = user.email,
                    date = todayDateString,
                    time = System.currentTimeMillis(),
                    lat = loc.lat,
                    lng = loc.lng,
                    loc = loc.address
                )
                _toastMessage.emit("Successfully Punched Out!")
            }
        } else {
            viewModelScope.launch {
                val fallbackSim = _selectedSimulationLocation.value
                repository.punchOut(
                    email = user.email,
                    date = todayDateString,
                    time = System.currentTimeMillis(),
                    lat = fallbackSim.lat,
                    lng = fallbackSim.lng,
                    loc = fallbackSim.name
                )
                _toastMessage.emit("Punched Out with Sim Location!")
            }
        }
    }

    fun applyLeaveRequest(
        leaveType: String,
        startDateStr: String,
        endDateStr: String,
        numDays: Float,
        reason: String,
        onSuccess: () -> Unit
    ) {
        val user = _loggedInUser.value ?: return
        viewModelScope.launch {
            val outcome = repository.applyLeave(user.email, leaveType, startDateStr, endDateStr, numDays, reason)
            if (outcome) {
                _toastMessage.emit("Leave request submitted! Awaiting Admin approval.")
                onSuccess()
            } else {
                _toastMessage.emit("Error: Inadequate remaining leave balance.")
            }
        }
    }

    fun resetData() {
        viewModelScope.launch {
            repository.clearAllData()
            _toastMessage.emit("Database and settings restored to default.")
            _loggedInUser.value = null
            sharedPrefs.edit().remove("logged_in_email").apply()
        }
    }
}

// State Containers
sealed interface LocationState {
    object Idle : LocationState
    object Locating : LocationState
    data class Success(val lat: Double, val lng: Double, val address: String) : LocationState
}

data class OfficeLocation(val name: String, val lat: Double, val lng: Double)

data class MonthlyStats(
    val presentDays: Int = 0,
    val halfDays: Int = 0,
    val leaveDays: Int = 0,
    val totalWorkHours: Double = 0.0,
    val records: List<AttendanceRecord> = emptyList()
)
