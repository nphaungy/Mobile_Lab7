package com.example.studentmanagementapp.domain.model

/** ผลของงานที่ต้องติดต่อ Server (ย้ายมาจาก data.repository เพราะเป็นแนวคิดระดับ Domain) */
enum class SyncOutcome { OK, OFFLINE, UNAUTHORIZED, SERVER_ERROR }

/** ผลของการส่งรายการ PENDING ขึ้น Server หนึ่งรอบ */
data class PushResult(val outcome: SyncOutcome, val synced: Int, val failed: Int)