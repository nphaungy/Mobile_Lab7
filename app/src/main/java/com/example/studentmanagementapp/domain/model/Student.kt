package com.example.studentmanagementapp.domain.model

data class Student(
    val id: Int = 0,
    val serverId: Int? = null,
    val studentCode: String,
    val name: String,
    val major: String,
    val syncStatus: SyncStatus = SyncStatus.PENDING
)