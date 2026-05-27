package com.biexi.pandaled.data.repository

import com.biexi.pandaled.data.local.JsonFileManager
import com.biexi.pandaled.data.local.ProjectDao
import com.biexi.pandaled.data.model.Project
import com.biexi.pandaled.data.model.ProjectIndex
import kotlinx.coroutines.flow.Flow

class ProjectRepository(
    private val projectDao: ProjectDao,
    private val jsonFileManager: JsonFileManager
) {

    /** Observe all project indices (name + pointer). */
    fun getAllProjectIndices(): Flow<List<ProjectIndex>> = projectDao.getAllProjects()

    /** Get a single index by id. */
    suspend fun getProjectIndex(id: String): ProjectIndex? = projectDao.getProjectById(id)

    /** Load full project detail from JSON. */
    suspend fun loadProject(fileName: String): Project? = jsonFileManager.loadProject(fileName)

    /** Save a new project (both index + JSON file). Assigns next orderIndex. */
    suspend fun saveProject(project: Project): String {
        val fileName = jsonFileManager.saveProject(project)
        val all = projectDao.getAllProjectsSnapshot()
        val nextOrder = (all.maxOfOrNull { it.orderIndex } ?: -1) + 1
        val index = ProjectIndex(
            name = project.name,
            jsonFileName = fileName,
            orderIndex = nextOrder
        )
        projectDao.insertProject(index)
        return index.id
    }

    /** Update an existing project. Updates lastModified to now. */
    suspend fun updateProject(index: ProjectIndex, project: Project) {
        jsonFileManager.saveProject(project, index.jsonFileName)
        projectDao.updateProject(index.copy(name = project.name, lastModified = System.currentTimeMillis()))
    }

    /** Move a project to a new position. Reassigns all orderIndex values. */
    suspend fun moveProject(from: ProjectIndex, to: ProjectIndex) {
        val all = projectDao.getAllProjectsSnapshot().toMutableList()
        val fromIdx = all.indexOfFirst { it.id == from.id }
        val toIdx = all.indexOfFirst { it.id == to.id }
        if (fromIdx < 0 || toIdx < 0) return
        all.removeAt(fromIdx)
        all.add(toIdx, from)
        all.forEachIndexed { i, p ->
            if (p.orderIndex != i) {
                projectDao.updateProject(p.copy(orderIndex = i))
            }
        }
    }

    /** Delete a project (index + JSON file). */
    suspend fun deleteProject(index: ProjectIndex) {
        jsonFileManager.deleteProject(index.jsonFileName)
        projectDao.deleteProject(index)
    }

    /** Import a project from a JSON string (e.g. from QR code). */
    suspend fun importFromJson(json: String): Project? {
        val project = jsonFileManager.parseFromJson(json) ?: return null
        saveProject(project)
        return project
    }

    /** Serialize a project to JSON string (for QR export). */
    fun projectToJson(project: Project): String = jsonFileManager.toJson(project)
}
