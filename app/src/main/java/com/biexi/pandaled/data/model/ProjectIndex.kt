package com.biexi.pandaled.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "project_index")
data class ProjectIndex(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val jsonFileName: String,          // relative path inside app's projects dir
    val lastModified: Long = System.currentTimeMillis(),
    val orderIndex: Int = 0
)
