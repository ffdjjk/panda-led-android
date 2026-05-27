package com.biexi.pandaled.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.biexi.pandaled.data.model.ProjectIndex
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    @Query("SELECT * FROM project_index ORDER BY orderIndex ASC")
    fun getAllProjects(): Flow<List<ProjectIndex>>

    @Query("SELECT * FROM project_index ORDER BY orderIndex ASC")
    suspend fun getAllProjectsSnapshot(): List<ProjectIndex>

    @Query("SELECT * FROM project_index WHERE id = :id")
    suspend fun getProjectById(id: String): ProjectIndex?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectIndex)

    @Update
    suspend fun updateProject(project: ProjectIndex)

    @Delete
    suspend fun deleteProject(project: ProjectIndex)

    @Query("DELETE FROM project_index WHERE id = :id")
    suspend fun deleteProjectById(id: String)
}
