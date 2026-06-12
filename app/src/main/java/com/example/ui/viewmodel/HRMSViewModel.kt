package com.example.ui.viewmodel

import android.annotation.SuppressLint
import android.app.Application
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
import com.example.data.repository.HRMSRepository
import com.google.android.gms.location.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class HRMSViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = HRMSRepository(database.attendanceDao(), database.leaveDao())

    // All records observed in UI
    val allAttendance: StateFlow<List<AttendanceRecord>> = repository.allAttendance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val leaveBalances: StateFlow<List<LeaveBalance>> = repository.leaveBalances
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val leaveRequests: StateFlow<List<LeaveRequest>> = repository.leaveRequests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Date management
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val todayDateString: String = dateFormatter.format(Date())

    // Today's attendance record
    val todayAttendance: StateFlow<AttendanceRecord?> = repository.getAttendanceForDateFlow(todayDateString)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Selection filters
    private val _selectedMonth = MutableStateFlow(SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date()))
    val selectedMonth: StateFlow<String> = _selectedMonth.asStateFlow()

    // Monthly summary stats computed reactively
    val monthlyStats = combine(allAttendance, selectedMonth) { attendanceList, month ->
        val monthRecords = attendanceList.filter { it.date.startsWith(month) }
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

    fun selectMonth(month: String) {
        _selectedMonth.value = month
    }

    fun selectSimulationLocation(location: OfficeLocation) {
        _selectedSimulationLocation.value = location
        // Automatically fetch coordinate mock representation
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
                            // If lastLocation is null, attempt a quick single update request
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
                // Return simple coordinate representation if geocoding fails in sandbox
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
        val loc = _currentLocationState.value
        if (loc is LocationState.Success) {
            viewModelScope.launch {
                repository.punchIn(
                    date = todayDateString,
                    time = System.currentTimeMillis(),
                    lat = loc.lat,
                    lng = loc.lng,
                    loc = loc.address
                )
                _toastMessage.emit("Successfully Punched In!")
            }
        } else {
            // Force fetch simulated location to carry out punch
            viewModelScope.launch {
                val fallbackSim = _selectedSimulationLocation.value
                repository.punchIn(
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
        val loc = _currentLocationState.value
        if (loc is LocationState.Success) {
            viewModelScope.launch {
                repository.punchOut(
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
        viewModelScope.launch {
            val outcome = repository.applyLeave(leaveType, startDateStr, endDateStr, numDays, reason)
            if (outcome) {
                _toastMessage.emit("Leave request submitted and auto-approved!")
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
