package com.example.studentmanagementapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.studentmanagementapp.domain.model.Student
import com.example.studentmanagementapp.domain.model.SyncStatus
import com.example.studentmanagementapp.viewmodel.StudentViewModel
import com.example.studentmanagementapp.worker.SyncScheduler

@Composable
fun StudentScreen(
    modifier: Modifier = Modifier,
    onLogout: () -> Unit = {},
    viewModel: StudentViewModel = hiltViewModel()
)  {
    val students by viewModel.students.collectAsState()
    val visibleStudents by viewModel.filteredStudents.collectAsState()
    val pendingCount = students.count { it.syncStatus == SyncStatus.PENDING }
    val failedCount = students.count { it.syncStatus == SyncStatus.FAILED }
    val context = LocalContext.current

    // Server ล่มก็ยังแสดงข้อมูลจาก Room
    LaunchedEffect(Unit) { viewModel.loadStudents() }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Student Management",
                    style = MaterialTheme.typography.headlineSmall
                )
                TextButton(onClick = onLogout) { Text("Logout") }
            }
        }
        item {
            Button(onClick = { SyncScheduler.syncNow(context) }) {
                Text("Sync in Background")
            }
        }

        viewModel.isOnline?.let { online ->
            item { OnlineStatus(isOnline = online) }
        }

        item {
            Text(
                text = "รอ Sync (PENDING): $pendingCount   |   ล้มเหลว (FAILED): $failedCount",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { viewModel.loadStudents() },
                    enabled = !viewModel.isSyncing
                ) { Text("Load Students") }

                Button(
                    onClick = { viewModel.syncStudents() },
                    enabled = !viewModel.isSyncing
                ) { Text("Sync Now") }

                if (viewModel.isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }

        viewModel.message?.let { m ->
            item {
                Text(
                    text = m.text,
                    color = if (m.isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item { AddStudentForm(onAdd = viewModel::addStudent) }

        item {
            OutlinedTextField(
                value = viewModel.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                label = { Text("ค้นหา (รหัส / ชื่อ / สาขา)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (visibleStudents.isEmpty() && viewModel.searchQuery.isNotBlank()) {
            item {
                Text(
                    text = "ไม่พบนักศึกษาที่ตรงกับ \"${viewModel.searchQuery}\"",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(visibleStudents, key = { it.id }) { student ->
            StudentRow(
                student = student,
                onClick = { viewModel.selectStudent(student) },
                onDelete = { viewModel.deleteStudent(student) }
            )
        }

        item { Box(modifier = Modifier.padding(bottom = 16.dp)) }
    }

    viewModel.selectedStudent?.let { selected ->
        EditStudentDialog(
            student = selected,
            onSave = viewModel::updateStudent,
            onDismiss = { viewModel.selectStudent(null) }
        )
    }
}

@Composable
private fun OnlineStatus(isOnline: Boolean) {
    val color = if (isOnline) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
    Text(
        text = if (isOnline) "● ออนไลน์: เชื่อมต่อ Server ได้"
        else "● ออฟไลน์: Server ไม่พร้อม แสดงข้อมูลจากเครื่อง",
        color = color,
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun AddStudentForm(onAdd: (code: String, name: String, major: String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var major by rememberSaveable { mutableStateOf("") }
    val canAdd = code.isNotBlank() && name.isNotBlank() && major.isNotBlank()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("เพิ่มนักศึกษา", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("รหัสนักศึกษา") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("ชื่อ") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = major,
            onValueChange = { major = it },
            label = { Text("สาขา") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                onAdd(code, name, major)
                code = ""
                name = ""
                major = ""
            },
            enabled = canAdd
        ) {
            Text("เพิ่มนักศึกษา")
        }
    }
}

@Composable
private fun StudentRow(
    student: Student,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val statusColor = when (student.syncStatus) {
        SyncStatus.SYNCED -> Color(0xFF2E7D32)
        SyncStatus.PENDING -> Color(0xFFEF6C00)
        else -> MaterialTheme.colorScheme.error
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${student.studentCode} - ${student.name}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(text = student.major, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "Sync: ${student.syncStatus}",
                    color = statusColor,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("ลบ")
            }
        }
    }
}

@Composable
private fun EditStudentDialog(
    student: Student,
    onSave: (Student) -> Unit,
    onDismiss: () -> Unit
) {
    var code by remember(student.id) { mutableStateOf(student.studentCode) }
    var name by remember(student.id) { mutableStateOf(student.name) }
    var major by remember(student.id) { mutableStateOf(student.major) }

    val edited = student.copy(studentCode = code.trim(), name = name.trim(), major = major.trim())
    val canSave = code.isNotBlank() && name.isNotBlank() && major.isNotBlank() && edited != student

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ข้อมูลนักศึกษา") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("รหัสนักศึกษา") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("ชื่อ") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = major,
                    onValueChange = { major = it },
                    label = { Text("สาขา") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(edited) }, enabled = canSave) {
                Text("บันทึก")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ปิด")
            }
        }
    )
}