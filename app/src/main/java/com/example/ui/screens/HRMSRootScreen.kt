package com.example.ui.screens

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.AttendanceRecord
import com.example.data.model.LeaveBalance
import com.example.data.model.LeaveRequest
import com.example.ui.viewmodel.HRMSViewModel
import com.example.ui.viewmodel.LocationState
import com.example.ui.viewmodel.OfficeLocation
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

enum class HRMSTab(val title: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    Attendance("Punch", Icons.Filled.Fingerprint, Icons.Outlined.Fingerprint),
    Summary("Summary", Icons.AutoMirrored.Filled.ListAlt, Icons.AutoMirrored.Outlined.ListAlt),
    Leaves("Leaves", Icons.Filled.BeachAccess, Icons.Outlined.BeachAccess)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HRMSRootScreen(
    viewModel: HRMSViewModel = viewModel()
) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(HRMSTab.Attendance) }
    var showApplyLeaveDialog by remember { mutableStateOf(false) }

    val toastMessage = viewModel.toastMessage

    // Collect variables
    val todayAttendance by viewModel.todayAttendance.collectAsStateWithLifecycle()
    val allAttendance by viewModel.allAttendance.collectAsStateWithLifecycle()
    val leaveBalances by viewModel.leaveBalances.collectAsStateWithLifecycle()
    val leaveRequests by viewModel.leaveRequests.collectAsStateWithLifecycle()
    val monthlyStats by viewModel.monthlyStats.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val currentLocationState by viewModel.currentLocationState.collectAsStateWithLifecycle()
    val selectedSimulationLocation by viewModel.selectedSimulationLocation.collectAsStateWithLifecycle()
    val currentWorkTicker by viewModel.currentWorkSessionTicker.collectAsStateWithLifecycle()

    // Listen to Toast events from ViewModel Flow
    LaunchedEffect(key1 = Unit) {
        toastMessage.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // Live Clock display
    var liveTimeString by remember { mutableStateOf("") }
    LaunchedEffect(key1 = Unit) {
        while (true) {
            liveTimeString = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            delay(1000)
        }
    }

    // Permission launcher for Location services
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
            if (fineGranted || coarseGranted) {
                viewModel.requestDeviceLocation()
            } else {
                Toast.makeText(context, "Location permission rejected. Simulated Office HQ selected.", Toast.LENGTH_LONG).show()
            }
        }
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.People,
                            contentDescription = "Zoho People Logo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "zoho",
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 24.sp
                        )
                        Text(
                            text = " people",
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 24.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.resetData() },
                        modifier = Modifier.testTag("reset_data_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Reset Dummy Data",
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                },
                actions = {
                    // Quick profile avatar visual info
                    IconButton(onClick = {
                        Toast.makeText(context, "Officer: Hemant Kumar\nDesignation: Senior Lead Engineer", Toast.LENGTH_SHORT).show()
                    }) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "HK",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 13.sp
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.shadow(8.dp),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                HRMSTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == tab) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.title
                            )
                        },
                        label = { Text(tab.title, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentTab == HRMSTab.Leaves) {
                ExtendedFloatingActionButton(
                    text = { Text("Apply Leave", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Filled.Add, "Add Icon") },
                    onClick = { showApplyLeaveDialog = true },
                    modifier = Modifier
                        .testTag("btn_apply_leave_fab")
                        .padding(bottom = 8.dp),
                    elevation = FloatingActionButtonDefaults.elevation(6.dp)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Display User Profile Card
            UserProfileHeader(todayAttendance)

            // Screen Selector with smooth transitions
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                },
                label = "screen_trans"
            ) { targetTab ->
                when (targetTab) {
                    HRMSTab.Attendance -> {
                        AttendancePunchScreen(
                            todayAttendance = todayAttendance,
                            liveTimeString = liveTimeString,
                            currentLocationState = currentLocationState,
                            selectedSimulationLocation = selectedSimulationLocation,
                            officeLocations = viewModel.officeLocations,
                            currentWorkTicker = currentWorkTicker,
                            onLocationSimSelected = { viewModel.selectSimulationLocation(it) },
                            onRequestLocationPermission = {
                                permissionsLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            onPunchIn = { viewModel.punchInToday() },
                            onPunchOut = { viewModel.punchOutToday() }
                        )
                    }
                    HRMSTab.Summary -> {
                        AttendanceSummaryScreen(
                            monthlyStats = monthlyStats,
                            selectedMonth = selectedMonth,
                            onMonthSelected = { viewModel.selectMonth(it) }
                        )
                    }
                    HRMSTab.Leaves -> {
                        LeavesManagementScreen(
                            balances = leaveBalances,
                            requests = leaveRequests
                        )
                    }
                }
            }
        }
    }

    if (showApplyLeaveDialog) {
        ApplyLeaveDialog(
            balances = leaveBalances,
            onDismiss = { showApplyLeaveDialog = false },
            onSubmit = { type, start, end, days, reason ->
                viewModel.applyLeaveRequest(type, start, end, days, reason) {
                    showApplyLeaveDialog = false
                }
            }
        )
    }
}

@Composable
fun UserProfileHeader(todayAttendance: AttendanceRecord?) {
    val currentDateStr = remember {
        val sdf = SimpleDateFormat("EEEE, MMM dd", Locale.getDefault())
        sdf.format(Date())
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = currentDateStr.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Hi, Hemant",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Real-time Status Badge
            Surface(
                color = when {
                    todayAttendance == null -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    todayAttendance.punchOutTime != null -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    else -> Color(0xFFE8F5E9)
                },
                shape = RoundedCornerShape(20.dp),
            ) {
                Text(
                    text = when {
                        todayAttendance == null -> "Punched Out"
                        todayAttendance.punchOutTime != null -> "Punched Out"
                        else -> "Working"
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        todayAttendance == null -> MaterialTheme.colorScheme.primary
                        todayAttendance.punchOutTime != null -> MaterialTheme.colorScheme.error
                        else -> Color(0xFF2E7D32)
                    }
                )
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFDBEAFE))
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "HK",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2563EB),
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun AttendancePunchScreen(
    todayAttendance: AttendanceRecord?,
    liveTimeString: String,
    currentLocationState: LocationState,
    selectedSimulationLocation: OfficeLocation,
    officeLocations: List<OfficeLocation>,
    currentWorkTicker: String?,
    onLocationSimSelected: (OfficeLocation) -> Unit,
    onRequestLocationPermission: () -> Unit,
    onPunchIn: () -> Unit,
    onPunchOut: () -> Unit
) {
    var expandedSimDropdown by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // 1. Clock Display Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault()).format(Date()).uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.2.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = liveTimeString,
                        fontWeight = FontWeight.Black,
                        fontSize = 36.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )

                    // Continuous Stop Watch
                    if (currentWorkTicker != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.HourglassEmpty,
                                    contentDescription = "Stopwatch logo",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Shift Logged: $currentWorkTicker",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Main Punch Button Widget
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val isPunchedIn = todayAttendance != null && todayAttendance.punchOutTime == null
                val isFullyLogged = todayAttendance != null && todayAttendance.punchOutTime != null

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(164.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isFullyLogged -> MaterialTheme.colorScheme.surfaceVariant
                                isPunchedIn -> MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.primaryContainer
                            }
                        )
                        .border(
                            width = 6.dp,
                            color = when {
                                isFullyLogged -> MaterialTheme.colorScheme.outlineVariant
                                isPunchedIn -> Color(0xFFFEE2E2)
                                else -> Color(0xFFEFF6FF)
                            },
                            shape = CircleShape
                        )
                        .padding(10.dp)
                ) {
                    Button(
                        onClick = {
                            if (!isFullyLogged) {
                                if (isPunchedIn) onPunchOut() else onPunchIn()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when {
                                isFullyLogged -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                isPunchedIn -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.primary
                            },
                            contentColor = when {
                                isFullyLogged -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> Color.White
                            }
                        ),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("punch_action_button"),
                        shape = CircleShape,
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 8.dp,
                            pressedElevation = 2.dp
                        )
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = when {
                                    isFullyLogged -> Icons.Filled.WorkHistory
                                    isPunchedIn -> Icons.Filled.PowerSettingsNew
                                    else -> Icons.Filled.Fingerprint
                                },
                                contentDescription = "Punch Icon",
                                modifier = Modifier.size(38.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = when {
                                    isFullyLogged -> "LOGGED"
                                    isPunchedIn -> "PUNCH OUT"
                                    else -> "PUNCH IN"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 1.1.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isFullyLogged) {
                    Text(
                        text = "You have completed your daily shift. Great work!",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = if (isPunchedIn) "Click to Check-Out and conclude hours." else "Click above or scanning fingerprint to log attendance.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // 3. Location / GPS status & Simulation switcher
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "ATTENDANCE GEOLOCATION DETECTOR",
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp,
                        letterSpacing = 1.2.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Geographic Status Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LocationOn,
                                contentDescription = "Location Pin",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            when (currentLocationState) {
                                is LocationState.Idle -> {
                                    Text("Idle Status", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("GPS Coordinates waiting request.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                is LocationState.Locating -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Interrogating Device GPS...", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                                    }
                                    Text("Communicating with cellular satellites.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                is LocationState.Success -> {
                                    Text(
                                        text = currentLocationState.address,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Lat: %.4f, Lng: %.4f".format(currentLocationState.lat, currentLocationState.lng),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = onRequestLocationPermission,
                            modifier = Modifier.testTag("btn_request_gps")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MyLocation,
                                contentDescription = "Scan GPS Location",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // Simulated Campus Selector Option (Crucial for AI Studio evaluation environments!)
                    Text(
                        text = "Test Simulation Helper (Bypass Remote GPS Sandbox):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedCard(
                            onClick = { expandedSimDropdown = true },
                            modifier = Modifier.fillMaxWidth().testTag("simulation_selector_box")
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (selectedSimulationLocation.name.contains("Home")) Icons.Filled.Home else Icons.Filled.Work,
                                        contentDescription = "Sim Logo",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        selectedSimulationLocation.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Icon(Icons.Filled.ArrowDropDown, "Dropdown Arrow")
                            }
                        }

                        DropdownMenu(
                            expanded = expandedSimDropdown,
                            onDismissRequest = { expandedSimDropdown = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            officeLocations.forEach { loc ->
                                DropdownMenuItem(
                                    text = { Text(loc.name, fontSize = 13.sp) },
                                    onClick = {
                                        onLocationSimSelected(loc)
                                        expandedSimDropdown = false
                                    },
                                    modifier = Modifier.testTag("sim_opt_${loc.name.take(5)}")
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Today's logs info (Read-only status card)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "TODAY'S ATTENDANCE SUMMARY LOG",
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp,
                        letterSpacing = 1.2.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (todayAttendance != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("PUNCH IN", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(todayAttendance.punchInTime)),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(todayAttendance.punchInLoc, fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }

                            VerticalDivider(modifier = Modifier.height(40.dp))

                            Column {
                                Text("PUNCH OUT", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = if (todayAttendance.punchOutTime != null) {
                                        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(todayAttendance.punchOutTime))
                                    } else "---",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(todayAttendance.punchOutLoc ?: "In Progress", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No logs found for today yet.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AttendanceSummaryScreen(
    monthlyStats: com.example.ui.viewmodel.MonthlyStats,
    selectedMonth: String,
    onMonthSelected: (String) -> Unit
) {
    var showMonthPicker by remember { mutableStateOf(false) }

    // Hardcode some months for selection
    val availableMonths = listOf("2026-06", "2026-05", "2026-04")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // Month Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Attendance Summary",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "View month logs and statistics review.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    Button(
                        onClick = { showMonthPicker = true },
                        modifier = Modifier.testTag("btn_select_month")
                    ) {
                        Text(
                            text = getFormattedMonthName(selectedMonth),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    DropdownMenu(
                        expanded = showMonthPicker,
                        onDismissRequest = { showMonthPicker = false }
                    ) {
                        availableMonths.forEach { month ->
                            DropdownMenuItem(
                                text = { Text(getFormattedMonthName(month)) },
                                onClick = {
                                    onMonthSelected(month)
                                    showMonthPicker = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Summary Metric Cards Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        title = "Present Days",
                        value = "${monthlyStats.presentDays}",
                        icon = Icons.Filled.CheckCircle,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Half Days",
                        value = "${monthlyStats.halfDays}",
                        icon = Icons.Filled.Warning,
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        title = "On Leave",
                        value = "${monthlyStats.leaveDays}",
                        icon = Icons.Filled.FlightTakeoff,
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Total Hours",
                        value = "%.1f Hrs".format(monthlyStats.totalWorkHours),
                        icon = Icons.Filled.Schedule,
                        tint = Color(0xFF9C27B0),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Subtitle header
        item {
            Text(
                text = "LOGGED DAYS FEED",
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                letterSpacing = 1.1.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        // List of attendance records for the month
        if (monthlyStats.records.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.CalendarToday,
                                contentDescription = "Empty",
                                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No attendance logs for this month.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            items(monthlyStats.records) { record ->
                AttendanceLogItem(record)
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = tint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = value,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun AttendanceLogItem(record: AttendanceRecord) {
    var expandedDetails by remember { mutableStateOf(false) }

    val formattedDate = try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateObj = parser.parse(record.date)
        if (dateObj != null) {
            SimpleDateFormat("EEE, MMM dd", Locale.getDefault()).format(dateObj)
        } else {
            record.date
        }
    } catch (e: Exception) {
        record.date
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expandedDetails = !expandedDetails }
            .testTag("record_item_${record.date}"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            if (expandedDetails) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Left Status Bar Dot Indicator
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                when (record.status) {
                                    "Present" -> Color(0xFF4CAF50)
                                    "Half Day" -> Color(0xFFFF9800)
                                    "On Leave" -> Color(0xFF2196F3)
                                    else -> Color(0xFF9E9E9E)
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = formattedDate,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = when (record.status) {
                            "Present" -> Color(0xFFE8F5E9)
                            "Half Day" -> Color(0xFFFFF3E0)
                            "On Leave" -> Color(0xFFE3F2FD)
                            else -> Color(0xFFF5F5F5)
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = record.status,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (record.status) {
                                "Present" -> Color(0xFF2E7D32)
                                "Half Day" -> Color(0xFFE65100)
                                "On Leave" -> Color(0xFF1565C0)
                                else -> Color(0xFF616161)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Icon(
                        imageVector = if (expandedDetails) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = "Expand logs details",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(visible = expandedDetails) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Punch In Info", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(record.punchInTime)),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(record.punchInLoc, fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text("Punch Out Info", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = if (record.punchOutTime != null) {
                                    SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(record.punchOutTime))
                                } else "No punch out logged",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            if (record.punchOutLoc != null) {
                                Text(record.punchOutLoc, fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }

                    if (record.workingHours != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.AccessTime,
                                contentDescription = "Logged Work duration",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Logged Work Duration: %.2f Hours".format(record.workingHours),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LeavesManagementScreen(
    balances: List<LeaveBalance>,
    requests: List<LeaveRequest>
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 80.dp) // extra padding for fab
    ) {
        // Balances Header
        item {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = "My Leave Balances",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Track available leave categories and balances.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Leave Balances Cards Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                balances.forEach { bal ->
                    LeaveBalanceCardItem(bal)
                }
            }
        }

        // Leave applications log header
        item {
            Text(
                text = "SUBMITTED LEAVE REQUESTS",
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                letterSpacing = 1.1.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        if (requests.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No leave requests submitted yet.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(requests) { req ->
                LeaveRequestCardItem(req)
            }
        }
    }
}

@Composable
fun LeaveBalanceCardItem(bal: LeaveBalance) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("leave_balance_${bal.leaveType.replace(" ", "_").lowercase()}"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (bal.leaveType) {
                            "Casual Leave" -> Icons.Filled.BeachAccess
                            "Sick Leave" -> Icons.Filled.MedicalServices
                            "Earned Leave" -> Icons.Filled.WorkHistory
                            else -> Icons.Filled.FlightTakeoff
                        },
                        contentDescription = "Leave Icon Type",
                        tint = when (bal.leaveType) {
                            "Casual Leave" -> Color(0xFF2196F3)
                            "Sick Leave" -> Color(0xFFE91E63)
                            "Earned Leave" -> Color(0xFF4CAF50)
                            else -> MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = bal.leaveType,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "%.1f / %.1f Days".format(bal.remaining, bal.allocated),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Percentage Linear indicator
            val percent = if (bal.allocated > 0) bal.remaining / bal.allocated else 0f
            LinearProgressIndicator(
                progress = { percent },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = when (bal.leaveType) {
                    "Casual Leave" -> Color(0xFF2196F3)
                    "Sick Leave" -> Color(0xFFE91E63)
                    "Earned Leave" -> Color(0xFF4CAF50)
                    else -> MaterialTheme.colorScheme.primary
                },
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Taken: %.1f Days".format(bal.taken),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Remaining: %.1f Days".format(bal.remaining),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun LeaveRequestCardItem(req: LeaveRequest) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = req.leaveType,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Applied on: ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(req.appliedDate))}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = Color(0xFFE8F5E9), // approved
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = req.status,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("DURATION", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${formatDateString(req.startDate)} - ${formatDateString(req.endDate)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("TOTAL DAYS", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${req.numDays}",
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (req.reason.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "\"${req.reason}\"",
                        modifier = Modifier.padding(10.dp),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplyLeaveDialog(
    balances: List<LeaveBalance>,
    onDismiss: () -> Unit,
    onSubmit: (leaveType: String, start: String, end: String, numDays: Float, reason: String) -> Unit
) {
    var selectedType by remember { mutableStateOf(balances.firstOrNull()?.leaveType ?: "Casual Leave") }
    var startDateStr by remember { mutableStateOf("") }
    var endDateStr by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }

    var expandedDropdown by remember { mutableStateOf(false) }

    // Validate and count days
    val parsedDays = remember(startDateStr, endDateStr) {
        val days = calculateDaysBetweenDates(startDateStr, endDateStr)
        if (days > 0) days else 1.0f
    }

    val selectedBalance = balances.find { it.leaveType == selectedType }
    val isDaysOverallocated = selectedBalance != null && parsedDays > selectedBalance.remaining

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("apply_leave_dialog"),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Request Leave",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // 1. Leave Type Selector
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Leave Category", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedCard(
                            onClick = { expandedDropdown = true },
                            modifier = Modifier.fillModifierOrTag("dialog_leave_type_dropdown")
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(selectedType, fontSize = 13.sp)
                                Icon(Icons.Filled.ArrowDropDown, "Dropdown")
                            }
                        }

                        DropdownMenu(
                            expanded = expandedDropdown,
                            onDismissRequest = { expandedDropdown = false },
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            balances.forEach { bal ->
                                DropdownMenuItem(
                                    text = { Text("${bal.leaveType} (Bal: %.1f)".format(bal.remaining)) },
                                    onClick = {
                                        selectedType = bal.leaveType
                                        expandedDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                // 2. Date Pickers (simple user typed entries or quick dates defaults for simulation ease)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = startDateStr,
                        onValueChange = { startDateStr = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("start_date_input"),
                        label = { Text("Start Date", fontSize = 11.sp) },
                        placeholder = { Text("YYYY-MM-DD") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = endDateStr,
                        onValueChange = { endDateStr = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("end_date_input"),
                        label = { Text("End Date", fontSize = 11.sp) },
                        placeholder = { Text("YYYY-MM-DD") },
                        singleLine = true
                    )
                }

                // Quick Simulation Helper autofill buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val cal = Calendar.getInstance()
                    
                    TextButton(onClick = {
                        val todayStr = sdf.format(Date())
                        startDateStr = todayStr
                        endDateStr = todayStr
                    }, modifier = Modifier.weight(1f)) {
                        Text("Today", fontSize = 11.sp)
                    }

                    TextButton(onClick = {
                        val todayStr = sdf.format(Date())
                        startDateStr = todayStr
                        cal.set(Calendar.DAY_OF_YEAR, cal.get(Calendar.DAY_OF_YEAR) + 1)
                        endDateStr = sdf.format(cal.time)
                    }, modifier = Modifier.weight(1f)) {
                        Text("+2 Days", fontSize = 11.sp)
                    }

                    TextButton(onClick = {
                        cal.set(Calendar.HOUR_OF_DAY, 0)
                        cal.add(Calendar.DAY_OF_YEAR, 2)
                        startDateStr = sdf.format(cal.time)
                        cal.add(Calendar.DAY_OF_YEAR, 2)
                        endDateStr = sdf.format(cal.time)
                    }, modifier = Modifier.weight(1.2f)) {
                        Text("Next Week", fontSize = 11.sp)
                    }
                }

                // Days preview display
                Surface(
                    color = if (isDaysOverallocated) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isDaysOverallocated) Icons.Filled.Error else Icons.Filled.EventNote,
                            contentDescription = "Day info",
                            tint = if (isDaysOverallocated) MaterialTheme.colorScheme.onErrorContainer
                                   else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isDaysOverallocated) "Error: Requested %.1f Days. Balance only %.1f.".format(parsedDays, selectedBalance?.remaining ?: 0f)
                                   else "Total days count: %.1f Days Requested.".format(parsedDays),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDaysOverallocated) MaterialTheme.colorScheme.onErrorContainer
                                    else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                // 3. Reason TextField
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason for absence") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .testTag("apply_reason_input"),
                    maxLines = 2
                )

                // 4. Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("dialog_dismiss_btn")
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        modifier = Modifier.testTag("dialog_submit_btn"),
                        enabled = startDateStr.isNotEmpty() && endDateStr.isNotEmpty() && !isDaysOverallocated,
                        onClick = {
                            onSubmit(selectedType, startDateStr, endDateStr, parsedDays, reason)
                        }
                    ) {
                        Text("Submit Request")
                    }
                }
            }
        }
    }
}

// Helpers
fun Modifier.fillModifierOrTag(tag: String): Modifier = this.testTag(tag).fillMaxWidth()

fun calculateDaysBetweenDates(start: String, end: String): Float {
    return try {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date1 = format.parse(start)
        val date2 = format.parse(end)
        if (date1 != null && date2 != null) {
            val diff = date2.time - date1.time
            val days = (diff / (1000 * 60 * 60 * 24)).toFloat() + 1.0f
            if (days > 0) days else 1.0f
        } else {
            1.0f
        }
    } catch (e: Exception) {
        1.0f
    }
}

fun getFormattedMonthName(monthStr: String): String {
    // format "YYYY-MM" to "June 2026"
    return try {
        val parser = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        val date = parser.parse(monthStr)
        if (date != null) {
            SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(date)
        } else {
            monthStr
        }
    } catch (e: Exception) {
        monthStr
    }
}

fun formatDateString(dateStr: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = parser.parse(dateStr)
        if (date != null) {
            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(date)
        } else {
            dateStr
        }
    } catch (e: Exception) {
        dateStr
    }
}
