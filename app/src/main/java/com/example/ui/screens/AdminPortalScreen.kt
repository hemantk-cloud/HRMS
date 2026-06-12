package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AttendanceRecord
import com.example.data.model.Employee
import com.example.data.model.LeaveRequest
import com.example.data.model.LeaveBalance
import com.example.ui.viewmodel.HRMSViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AdminPortalScreen(
    viewModel: HRMSViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var adminSectionTab by remember { mutableStateOf(0) } // 0 = Employees, 1 = Leaves, 2 = Attendance

    val employees by viewModel.allEmployeesList.collectAsStateWithLifecycle()
    val leaveRequests by viewModel.leaveRequests.collectAsStateWithLifecycle()
    val attendanceLogs by viewModel.allAttendance.collectAsStateWithLifecycle()
    val allBalances by viewModel.allLeaveBalancesList.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Tab row for Admin sub-sections
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val adminTabs = listOf("Employees", "Leaves", "Attendance")
            adminTabs.forEachIndexed { index, title ->
                val selected = adminSectionTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else Color.Transparent
                        )
                        .clickable { adminSectionTab = index }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        when (adminSectionTab) {
            0 -> AdminEmployeesTab(
                employees = employees,
                balancesList = allBalances,
                onAddEmployee = { name, email, dept, emId, isAdmin ->
                    viewModel.registerNewEmployee(name, email, dept, emId, isAdmin)
                },
                onDeleteEmployee = { viewModel.deleteEmployeeByAdmin(it) },
                onUpdateLeaveBalances = { email, priv, comp ->
                    viewModel.updateEmployeeLeaveBalances(email, priv, comp)
                }
            )
            1 -> AdminLeavesTab(
                leaveRequests = leaveRequests,
                onAction = { id, approve ->
                    viewModel.handleLeaveApprovalByAdmin(id, approve)
                }
            )
            2 -> AdminAttendanceTab(
                attendanceLogs = attendanceLogs,
                onExportExcel = {
                    viewModel.exportDataToExcel { file ->
                        if (file != null) {
                            try {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(Intent.EXTRA_SUBJECT, "ALLEN HRMS-Excel Export Attendance Logs")
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Export / Share Logs"))
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(context, "Sharing failed: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(context, "Failed to compile export file.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onAwardCompOff = { email, days, dateStr ->
                    viewModel.awardCompensatoryOff(email, days, dateStr)
                }
            )
        }
    }
}

@Composable
fun AdminEmployeesTab(
    employees: List<Employee>,
    balancesList: List<LeaveBalance>,
    onAddEmployee: (String, String, String, String, Boolean) -> Unit,
    onDeleteEmployee: (Employee) -> Unit,
    onUpdateLeaveBalances: (String, Float, Float) -> Unit
) {
    var expandedAddForm by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    var nameInput by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var departmentInput by remember { mutableStateOf("") }
    var empIdInput by remember { mutableStateOf("") }
    var isAdminSelected by remember { mutableStateOf(false) }

    var editingEmployeeBalances by remember { mutableStateOf<Employee?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedAddForm = !expandedAddForm },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.PersonAdd,
                                contentDescription = "Add Icon",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Add New Employee Profile",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { expandedAddForm = !expandedAddForm }) {
                            Icon(
                                imageVector = if (expandedAddForm) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Collapse Arrow"
                            )
                        }
                    }

                    AnimatedVisibility(visible = expandedAddForm) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it },
                                label = { Text("Full Name", fontSize = 13.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("admin_add_emp_name")
                            )

                            OutlinedTextField(
                                value = emailInput,
                                onValueChange = { emailInput = it },
                                label = { Text("Official Email ID", fontSize = 13.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("admin_add_emp_email")
                            )

                            OutlinedTextField(
                                value = departmentInput,
                                onValueChange = { departmentInput = it },
                                label = { Text("Department", fontSize = 13.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("admin_add_emp_dept")
                            )

                            OutlinedTextField(
                                value = empIdInput,
                                onValueChange = { empIdInput = it },
                                label = { Text("Employee ID (e.g. ALLEN-101)", fontSize = 13.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("admin_add_emp_id")
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { isAdminSelected = !isAdminSelected }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isAdminSelected,
                                    onCheckedChange = { isAdminSelected = it },
                                    modifier = Modifier.testTag("admin_add_emp_is_admin")
                                )
                                Text(
                                    "Grant Administrator Portal Access",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    onAddEmployee(nameInput, emailInput, departmentInput, empIdInput, isAdminSelected)
                                    // Clear fields on submit
                                    nameInput = ""
                                    emailInput = ""
                                    departmentInput = ""
                                    empIdInput = ""
                                    isAdminSelected = false
                                    expandedAddForm = false
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("admin_add_emp_submit"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Register Account", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Registered Employees (${employees.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (employees.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No Registered Employees. Add above.", color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            items(employees) { employee ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    val empBalances = remember(balancesList, employee.email) {
                        balancesList.filter { it.employeeEmail == employee.email }
                    }
                    val privilegeLeave = empBalances.find { it.leaveType == "Privilege Leave" }
                    val compOff = empBalances.find { it.leaveType == "Compensatory Off" }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (employee.isAdmin) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.secondaryContainer
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (employee.isAdmin) Icons.Filled.SupervisorAccount else Icons.Filled.Badge,
                                    contentDescription = "Emp icon",
                                    tint = if (employee.isAdmin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        employee.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (employee.isAdmin) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        ) {
                                            Text(
                                                "ADMIN",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    "${employee.department} • ${employee.emId}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    employee.email,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )

                                // Current Leave Balances
                                Row(
                                    modifier = Modifier.padding(top = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Star,
                                        contentDescription = null,
                                        tint = Color(0xFFF1C40F),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "PL: ${privilegeLeave?.remaining ?: 0.0f}/${privilegeLeave?.allocated ?: 0.0f} d",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.EventAvailable,
                                        contentDescription = null,
                                        tint = Color(0xFF2ECC71),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "CO: ${compOff?.remaining ?: 0.0f}/${compOff?.allocated ?: 0.0f} d",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { editingEmployeeBalances = employee },
                                modifier = Modifier.testTag("manage_leaves_${employee.email}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.EditCalendar,
                                    contentDescription = "Adjust Balances",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            IconButton(
                                onClick = { onDeleteEmployee(employee) },
                                modifier = Modifier.testTag("delete_emp_${employee.email}")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "Delete Employee",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingEmployeeBalances != null) {
        val emp = editingEmployeeBalances!!
        val empBalances = balancesList.filter { it.employeeEmail == emp.email }
        val plAllocated = empBalances.find { it.leaveType == "Privilege Leave" }?.allocated ?: 15f
        val coAllocated = empBalances.find { it.leaveType == "Compensatory Off" }?.allocated ?: 0f

        var plInput by remember { mutableStateOf(plAllocated.toString()) }
        var coInput by remember { mutableStateOf(coAllocated.toString()) }

        AlertDialog(
            onDismissRequest = { editingEmployeeBalances = null },
            title = {
                Text(
                    text = "Decide Leave Balances",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Set total allocated days for ${emp.name} (${emp.emId}). Current usage will be preserved.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = plInput,
                        onValueChange = { plInput = it },
                        label = { Text("Privilege Leave (Allocated)") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = coInput,
                        onValueChange = { coInput = it },
                        label = { Text("Compensatory Off (Allocated)") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pl = plInput.toFloatOrNull() ?: plAllocated
                        val co = coInput.toFloatOrNull() ?: coAllocated
                        onUpdateLeaveBalances(emp.email, pl, co)
                        editingEmployeeBalances = null
                    }
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingEmployeeBalances = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AdminLeavesTab(
    leaveRequests: List<LeaveRequest>,
    onAction: (Int, Boolean) -> Unit
) {
    val pendingRequests = remember(leaveRequests) { leaveRequests.filter { it.status == "Pending" } }
    val historyRequests = remember(leaveRequests) { leaveRequests.filter { it.status != "Pending" } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text(
                "Pending Decisions (${pendingRequests.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (pendingRequests.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("All requests are processed and updated.", color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)
                }
            }
        } else {
            items(pendingRequests) { request ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.BeachAccess,
                                        contentDescription = "Leave",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        request.employeeEmail,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        request.leaveType,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    "${request.numDays} Days",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "${request.startDate} to ${request.endDate}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "\"${request.reason}\"",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onAction(request.id, false) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("leave_reject_${request.id}"),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Reject", fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { onAction(request.id, true) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("leave_approve_${request.id}"),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Approve", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Decision History (${historyRequests.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        if (historyRequests.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No past logs found.", color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)
                }
            }
        } else {
            items(historyRequests) { request ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                request.employeeEmail,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                "${request.leaveType} • ${request.numDays} Days",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${request.startDate} to ${request.endDate}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        Surface(
                            color = if (request.status == "Approved") Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                request.status,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (request.status == "Approved") Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminAttendanceTab(
    attendanceLogs: List<AttendanceRecord>,
    onExportExcel: () -> Unit,
    onAwardCompOff: (String, Float, String) -> Unit
) {
    val sundayLogs = remember(attendanceLogs) {
        attendanceLogs.filter { log ->
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            try {
                val date = format.parse(log.date)
                if (date != null) {
                    val cal = Calendar.getInstance()
                    cal.time = date
                    cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
                } else false
            } catch (e: Exception) {
                false
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Export Enterprise Roster",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Compile spreadsheet containing complete punch times, locations and total hours.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = { onExportExcel() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        elevation = ButtonDefaults.buttonElevation(4.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("admin_export_excel_btn")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Share, "Share", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Excel", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (sundayLogs.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.EventAvailable,
                                contentDescription = "Sunday Comp Off Icon",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Sunday Compensation Roster",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Manually award Compensatory Off for employees present on Sundays on the basis of working hours.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        sundayLogs.forEach { log ->
                            var showAwardDialog by remember { mutableStateOf(false) }
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        log.employeeEmail,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "Sunday: ${log.date} • Worked: ${String.format("%.2f Hrs", log.workingHours ?: 0.0)}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                Button(
                                    onClick = { showAwardDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp).testTag("award_comp_off_${log.employeeEmail}")
                                ) {
                                    Text("Award", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            
                            if (showAwardDialog) {
                                val workingHrs = log.workingHours ?: 0.0
                                val suggestedComp = if (workingHrs >= 6.0) 1.0f else if (workingHrs >= 3.0) 0.5f else 0.0f
                                var awardDaysInput by remember { mutableStateOf(suggestedComp.toString()) }
                                
                                AlertDialog(
                                    onDismissRequest = { showAwardDialog = false },
                                    title = { Text("Award Compensatory Off", fontWeight = FontWeight.Bold) },
                                    text = {
                                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text(
                                                "Employee: ${log.employeeEmail}\n" +
                                                "Worked on Sunday (${log.date}): ${String.format("%.2f Hrs", workingHrs)}",
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                "Recommended action: Award ${suggestedComp} days.",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            OutlinedTextField(
                                                value = awardDaysInput,
                                                onValueChange = { awardDaysInput = it },
                                                label = { Text("Compensatory Off Days to Add") },
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.fillMaxWidth().testTag("award_comp_off_input")
                                            )
                                        }
                                    },
                                    confirmButton = {
                                        Button(
                                            onClick = {
                                                val days = awardDaysInput.toFloatOrNull() ?: suggestedComp
                                                onAwardCompOff(log.employeeEmail, days, log.date)
                                                showAwardDialog = false
                                            },
                                            modifier = Modifier.testTag("award_comp_off_submit")
                                        ) {
                                            Text("Settle & Award")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showAwardDialog = false }) {
                                            Text("Cancel")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Access Logs History (${attendanceLogs.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
            )
        }

        if (attendanceLogs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No logs found in roster DB.", color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            items(attendanceLogs) { log ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    log.employeeEmail,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    log.date,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }

                            // Status Badge
                            Surface(
                                color = when (log.status) {
                                    "Present" -> Color(0xFFE8F5E9)
                                    "Half Day" -> Color(0xFFFFF3E0)
                                    "On Leave" -> Color(0xFFE3F2FD)
                                    else -> Color(0xFFFFEBEE)
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    log.status,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (log.status) {
                                        "Present" -> Color(0xFF2E7D32)
                                        "Half Day" -> Color(0xFFE65100)
                                        "On Leave" -> Color(0xFF1565C0)
                                        else -> Color(0xFFC62828)
                                    }
                                )
                            }
                        }

                        if (log.status != "On Leave" && log.status != "Absent") {
                            Spacer(modifier = Modifier.height(8.dp))
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(8.dp))

                            val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
                            val inStr = timeFormat.format(Date(log.punchInTime))
                            val outStr = log.punchOutTime?.let { timeFormat.format(Date(it)) } ?: "Ongoing"
                            val diffStr = log.workingHours?.let { "• %.2f hrs".format(it) } ?: "• Active"

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.AccessTime,
                                        contentDescription = "Time",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "$inStr - $outStr $diffStr",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Place,
                                    contentDescription = "Place",
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = log.punchInLoc,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
