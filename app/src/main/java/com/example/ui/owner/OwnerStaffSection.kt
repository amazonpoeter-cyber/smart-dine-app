package com.example.ui.owner

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.model.*
import com.example.ui.components.StatusPill
import com.example.ui.theme.*
import com.example.util.DateUtils
import com.example.util.WeekdaySlot
import com.example.viewmodel.RestaurantViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerStaffSection(
    uiState: RestaurantUiState,
    viewModel: RestaurantViewModel
) {
    var showAddEmployeeDialog by remember { mutableStateOf(false) }
    var editingEmployee by remember { mutableStateOf<StaffMember?>(null) }
    var showAttendanceDatePicker by remember { mutableStateOf(false) }
    var showDayOffPlanner by remember { mutableStateOf(false) }
    var pendingRelieveStaff by remember { mutableStateOf<StaffMember?>(null) }

    val today = DateUtils.todayIso()
    val yesterday = DateUtils.daysAgoIso(1)
    val selectedDate = uiState.attendanceDateIso

    val activeStaff = remember(uiState.staff) { uiState.staff.filter { !it.isRelieved } }
    val relievedStaff = remember(uiState.staff) { uiState.staff.filter { it.isRelieved } }

    val filteredActiveStaff = remember(
        activeStaff,
        uiState.attendanceRecords,
        selectedDate,
        uiState.attendanceStatusFilter
    ) {
        activeStaff.filter { member ->
            val status = uiState.effectiveAttendanceStatus(member, selectedDate)
            when (uiState.attendanceStatusFilter) {
                AttendanceFilterStatus.ALL -> true
                AttendanceFilterStatus.PRESENT -> status == AttendanceStatus.PRESENT
                AttendanceFilterStatus.ABSENT -> status == AttendanceStatus.ABSENT
                AttendanceFilterStatus.LEAVE -> status == AttendanceStatus.LEAVE
                AttendanceFilterStatus.DAY_OFF -> status == AttendanceStatus.DAY_OFF
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Duty Roster & Attendance", style = MaterialTheme.typography.headlineSmall, color = NavyPrimary)
                    Text(
                        "${uiState.activeStaffOnDutyCount} active Present today • ${activeStaff.size} total staff",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateSubtext
                    )
                }
                Button(
                    onClick = { showAddEmployeeDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary, contentColor = CrispWhite),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("add_employee_button")
                ) {
                    Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("+ Add Employee", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // Monthly Day-Off Roster Planner Card
        item {
            Surface(
                color = CrispWhite,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.EventBusy, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(18.dp))
                            Column {
                                Text("Monthly Day-Off Roster", style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
                                Text(
                                    "Assign weekly/monthly Day-Off per employee",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SlateSubtext
                                )
                            }
                        }
                        TextButton(
                            onClick = { showDayOffPlanner = !showDayOffPlanner },
                            modifier = Modifier.testTag("toggle_day_off_planner")
                        ) {
                            Text(if (showDayOffPlanner) "Hide" else "Configure", color = NavyPrimary)
                        }
                    }

                    if (showDayOffPlanner) {
                        HorizontalDivider(color = SlateBorder)
                        activeStaff.forEach { member ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(member.name, style = MaterialTheme.typography.labelLarge, color = NavyPrimary)
                                    Text(
                                        text = "Off: ${member.scheduledDayOff.fullLabel}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SlateSubtext
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    WeekdaySlot.entries.forEach { slot ->
                                        val selected = member.scheduledDayOff == slot
                                        FilterChip(
                                            selected = selected,
                                            onClick = { viewModel.setEmployeeDayOff(member.id, slot) },
                                            label = { Text(slot.shortLabel, style = MaterialTheme.typography.labelSmall) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = NavyPrimary,
                                                selectedLabelColor = CrispWhite,
                                                containerColor = SlateSurfaceVariant,
                                                labelColor = NavyMuted
                                            ),
                                            border = null,
                                            modifier = Modifier.testTag("day_off_${member.id}_${slot.name.lowercase()}")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Attendance Date & Status Filter Card
        item {
            Surface(
                color = CrispWhite,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Attendance Logs", style = MaterialTheme.typography.labelLarge, color = NavyPrimary)
                        Text(
                            text = DateUtils.formatFriendlyDate(selectedDate),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                            color = SlateSubtext
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedDate == today,
                            onClick = { viewModel.setAttendanceDateFilter(today) },
                            label = { Text("Today", style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NavyPrimary, selectedLabelColor = CrispWhite),
                            modifier = Modifier.testTag("attendance_date_today")
                        )
                        FilterChip(
                            selected = selectedDate == yesterday,
                            onClick = { viewModel.setAttendanceDateFilter(yesterday) },
                            label = { Text("Yesterday", style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NavyPrimary, selectedLabelColor = CrispWhite),
                            modifier = Modifier.testTag("attendance_date_yesterday")
                        )
                        FilterChip(
                            selected = selectedDate != today && selectedDate != yesterday,
                            onClick = { showAttendanceDatePicker = true },
                            label = {
                                Text(
                                    if (selectedDate != today && selectedDate != yesterday) DateUtils.formatFriendlyDate(selectedDate) else "Pick Date",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            leadingIcon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NavyPrimary,
                                selectedLabelColor = CrispWhite,
                                selectedLeadingIconColor = CrispWhite
                            ),
                            modifier = Modifier.testTag("attendance_date_custom")
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AttendanceFilterStatus.entries.forEach { statusFilter ->
                            FilterChip(
                                selected = uiState.attendanceStatusFilter == statusFilter,
                                onClick = { viewModel.setAttendanceStatusFilter(statusFilter) },
                                label = { Text(statusFilter.label, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NavyPrimary,
                                    selectedLabelColor = CrispWhite,
                                    containerColor = SlateSurfaceVariant,
                                    labelColor = NavyMuted
                                ),
                                border = null,
                                modifier = Modifier.testTag("attendance_status_filter_${statusFilter.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        }

        // Active Employee Roster Cards (with Edit + Relieve + Override)
        items(filteredActiveStaff, key = { it.id }) { member ->
            val status = uiState.effectiveAttendanceStatus(member, selectedDate)
            val record = uiState.attendanceRecordFor(member.id, selectedDate)
            val (pillBg, pillFg) = when (status) {
                AttendanceStatus.PRESENT -> EmeraldSoft to EmeraldDark
                AttendanceStatus.ABSENT -> RoseSoft to RoseDark
                AttendanceStatus.LEAVE -> AmberSoft to AmberDark
                AttendanceStatus.DAY_OFF -> SkySoft to SkyDark
            }

            Surface(
                color = CrispWhite,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("staff_card_${member.id}")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(member.name, style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
                            Text(
                                text = "${member.designation} • ${member.portalRole.label} • ${member.shiftLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateSubtext
                            )
                            Text(
                                text = "CNIC (Login ID): ${member.cnic}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                color = NavyPrimary
                            )
                            Text(
                                text = "${member.phone}${if (member.email.isNotBlank()) " • ${member.email}" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = NavyMuted
                            )
                            Text(
                                text = member.address,
                                style = MaterialTheme.typography.labelSmall,
                                color = SlateSubtext
                            )
                            if (member.scheduledDayOff != WeekdaySlot.NONE) {
                                Text(
                                    text = "Day-Off: ${member.scheduledDayOff.fullLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SkyDark
                                )
                            }
                        }

                        val statusText = buildString {
                            append(status.label)
                            if (status == AttendanceStatus.PRESENT && record?.checkInTimeLabel != null) {
                                append(" • ${record.checkInTimeLabel}")
                            }
                        }
                        StatusPill(text = statusText, containerColor = pillBg, contentColor = pillFg)
                    }

                    HorizontalDivider(color = SlateBorder)

                    // Admin Override Quick Buttons + Edit + Relieve
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                AttendanceStatus.PRESENT to "Present",
                                AttendanceStatus.ABSENT to "Absent",
                                AttendanceStatus.LEAVE to "Leave"
                            ).forEach { (overrideStatus, shortLabel) ->
                                val isCurrent = status == overrideStatus
                                Surface(
                                    onClick = { viewModel.overrideAttendance(member.id, selectedDate, overrideStatus) },
                                    color = if (isCurrent) NavyPrimary else SlateSurfaceVariant,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("override_${member.id}_${overrideStatus.name.lowercase()}")
                                ) {
                                    Text(
                                        text = shortLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isCurrent) CrispWhite else NavyMuted,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { editingEmployee = member },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, SlateBorder),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("edit_staff_${member.id}")
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Edit ${member.name}", tint = NavyPrimary, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Edit", style = MaterialTheme.typography.labelSmall, color = NavyPrimary)
                            }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = { pendingRelieveStaff = member },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, RoseAlert.copy(alpha = 0.45f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("relieve_staff_${member.id}")
                            ) {
                                Text("Relieve", style = MaterialTheme.typography.labelSmall, color = RoseDark)
                            }
                        }
                    }
                }
            }
        }

        if (relievedStaff.isNotEmpty()) {
            item {
                Text(
                    text = "Relieved Staff (Historical Logs Preserved)",
                    style = MaterialTheme.typography.titleSmall,
                    color = SlateSubtext,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            items(relievedStaff, key = { it.id }) { former ->
                val pastRecord = uiState.attendanceRecordFor(former.id, selectedDate)
                Surface(
                    color = SlateSurfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, SlateBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(former.name, style = MaterialTheme.typography.titleSmall, color = NavyMuted)
                            Text(
                                text = "${former.designation} • Log ($selectedDate): ${pastRecord?.status?.label ?: "No record"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SlateSubtext
                            )
                        }
                        StatusPill(text = "Relieved", containerColor = CrispWhite, contentColor = SlateSubtext)
                    }
                }
            }
        }
    }

    if (showAddEmployeeDialog) {
        EmployeeEditorDialog(
            existingEmployee = null,
            onDismiss = { showAddEmployeeDialog = false },
            onSave = { name, cnic, password, phone, designation, portalRole, address, email, shift, dayOff ->
                val ok = viewModel.addEmployee(name, cnic, password, phone, designation, portalRole, address, email, shift, dayOff)
                if (ok) showAddEmployeeDialog = false
            }
        )
    }

    editingEmployee?.let { target ->
        EmployeeEditorDialog(
            existingEmployee = target,
            onDismiss = { editingEmployee = null },
            onSave = { name, cnic, password, phone, designation, portalRole, address, email, shift, dayOff ->
                val ok = viewModel.updateEmployee(target.id, name, cnic, password, phone, designation, portalRole, address, email, shift, dayOff)
                if (ok) editingEmployee = null
            }
        )
    }

    pendingRelieveStaff?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingRelieveStaff = null },
            containerColor = CrispWhite,
            title = { Text("Relieve ${target.name}?", style = MaterialTheme.typography.titleLarge, color = NavyPrimary) },
            text = {
                Text(
                    "This marks ${target.name} as Relieved / Inactive and removes them from active duty schedules while preserving all past attendance logs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SlateSubtext
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.relieveEmployee(target.id)
                        pendingRelieveStaff = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseAlert, contentColor = CrispWhite),
                    modifier = Modifier.testTag("confirm_relieve_staff_button")
                ) {
                    Text("Mark Relieved")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRelieveStaff = null }) { Text("Cancel", color = SlateSubtext) }
            }
        )
    }

    if (showAttendanceDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showAttendanceDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            viewModel.setAttendanceDateFilter(DateUtils.millisToIsoDate(millis))
                        }
                        showAttendanceDatePicker = false
                    }
                ) { Text("Apply", color = NavyPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { showAttendanceDatePicker = false }) { Text("Cancel", color = SlateSubtext) }
            }
        ) { DatePicker(state = datePickerState) }
    }
}

@Composable
private fun MandatoryFieldLabel(text: String) {
    Text(
        buildAnnotatedString {
            append(text)
            append(" ")
            withStyle(SpanStyle(color = RoseAlert, fontWeight = FontWeight.Bold)) {
                append("*")
            }
        }
    )
}

@Composable
private fun EmployeeEditorDialog(
    existingEmployee: StaffMember?,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        cnic: String,
        password: String,
        phone: String,
        designation: String,
        portalRole: StaffPortalRole,
        address: String,
        email: String,
        shiftLabel: String,
        dayOff: WeekdaySlot
    ) -> Unit
) {
    val isCreateMode = existingEmployee == null
    var name by remember(existingEmployee) { mutableStateOf(existingEmployee?.name ?: "") }
    var cnic by remember(existingEmployee) { mutableStateOf(existingEmployee?.cnic ?: "") }
    var password by remember(existingEmployee) { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var phone by remember(existingEmployee) { mutableStateOf(existingEmployee?.phone ?: "") }
    var designation by remember(existingEmployee) { mutableStateOf(existingEmployee?.designation ?: "") }
    var portalRole by remember(existingEmployee) { mutableStateOf(existingEmployee?.portalRole ?: StaffPortalRole.GENERAL_STAFF) }
    var address by remember(existingEmployee) { mutableStateOf(existingEmployee?.address ?: "") }
    var email by remember(existingEmployee) { mutableStateOf(existingEmployee?.email ?: "") }
    var shift by remember(existingEmployee) { mutableStateOf(existingEmployee?.shiftLabel ?: "10:00 - 19:00") }
    var dayOff by remember(existingEmployee) { mutableStateOf(existingEmployee?.scheduledDayOff ?: WeekdaySlot.SUNDAY) }
    var showValidationError by remember { mutableStateOf(false) }

    val passwordValid = if (isCreateMode) password.trim().length >= 4 else (password.isBlank() || password.trim().length >= 4)
    val isFormValid = name.isNotBlank() &&
        cnic.isNotBlank() &&
        passwordValid &&
        phone.isNotBlank() &&
        designation.isNotBlank() &&
        address.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CrispWhite,
        title = {
            Text(
                text = if (isCreateMode) "Onboard New Employee" else "Edit ${existingEmployee!!.name}",
                style = MaterialTheme.typography.titleLarge,
                color = NavyPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { MandatoryFieldLabel("Full Name") },
                    singleLine = true,
                    isError = showValidationError && name.isBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("emp_form_name_input")
                )

                OutlinedTextField(
                    value = cnic,
                    onValueChange = { cnic = it },
                    label = { MandatoryFieldLabel("CNIC Number (Login ID)") },
                    placeholder = { Text("34601-1234567-1") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    singleLine = true,
                    isError = showValidationError && cnic.isBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("emp_form_cnic_input")
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = {
                        if (isCreateMode) MandatoryFieldLabel("Password")
                        else Text("New Password (Leave blank to keep current)")
                    },
                    placeholder = { Text("Alphanumeric password (min 4 chars)") },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (showPassword) "Hide password" else "Show password",
                                tint = SlateSubtext
                            )
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    isError = showValidationError && !passwordValid,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("emp_form_password_input")
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { MandatoryFieldLabel("Phone Number") },
                    placeholder = { Text("+92 300 1234567") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    isError = showValidationError && phone.isBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("emp_form_phone_input")
                )

                OutlinedTextField(
                    value = designation,
                    onValueChange = { designation = it },
                    label = { MandatoryFieldLabel("Designation") },
                    placeholder = { Text("e.g., Head Chef, Delivery Rider, Senior Server") },
                    singleLine = true,
                    isError = showValidationError && designation.isBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("emp_form_designation_input")
                )

                MandatoryFieldLabel("Portal Access Role")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StaffPortalRole.entries.forEach { role ->
                        FilterChip(
                            selected = portalRole == role,
                            onClick = { portalRole = role },
                            label = { Text(role.label, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NavyPrimary,
                                selectedLabelColor = CrispWhite
                            ),
                            modifier = Modifier.testTag("emp_form_role_${role.name.lowercase()}")
                        )
                    }
                }

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { MandatoryFieldLabel("Address") },
                    placeholder = { Text("Street, block, city") },
                    minLines = 2,
                    maxLines = 3,
                    isError = showValidationError && address.isBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("emp_form_address_input")
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email (Optional)") },
                    placeholder = { Text("staff@smartdine.app") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("emp_form_email_input")
                )

                OutlinedTextField(
                    value = shift,
                    onValueChange = { shift = it },
                    label = { Text("Shift Timing") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("emp_form_shift_input")
                )

                Text("Monthly Day-Off", style = MaterialTheme.typography.labelMedium, color = NavyPrimary)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    WeekdaySlot.entries.forEach { slot ->
                        FilterChip(
                            selected = dayOff == slot,
                            onClick = { dayOff = slot },
                            label = { Text(slot.shortLabel, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NavyPrimary,
                                selectedLabelColor = CrispWhite
                            )
                        )
                    }
                }

                if (showValidationError && !isFormValid) {
                    Text(
                        text = "Please fill in all mandatory (*) fields (password min 4 characters).",
                        style = MaterialTheme.typography.labelSmall,
                        color = RoseAlert
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!isFormValid) {
                        showValidationError = true
                    } else {
                        onSave(name, cnic, password, phone, designation, portalRole, address, email, shift, dayOff)
                    }
                },
                enabled = isFormValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NavyPrimary,
                    contentColor = CrispWhite,
                    disabledContainerColor = SlateBorder,
                    disabledContentColor = SlateSubtext
                ),
                modifier = Modifier.testTag("emp_form_save_button")
            ) {
                Text(if (isCreateMode) "Add Employee" else "Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = SlateSubtext) }
        }
    )
}
