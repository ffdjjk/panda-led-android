package com.biexi.pandaled.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.biexi.pandaled.data.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class JsonFileManager(private val context: Context) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val projectsDir: File
        get() = File(context.filesDir, "projects").also { it.mkdirs() }

    /**
     * Save a Project to a JSON file.
     * @return the file name (not full path) used.
     */
    suspend fun saveProject(project: Project, fileName: String? = null): String =
        withContext(Dispatchers.IO) {
            val name = fileName ?: "${project.name}_${System.currentTimeMillis()}.json"
            val file = File(projectsDir, name)
            file.writeText(gson.toJson(project))
            name
        }

    /**
     * Load a Project from a JSON file by its filename.
     */
    suspend fun loadProject(fileName: String): Project? = withContext(Dispatchers.IO) {
        val file = File(projectsDir, fileName)
        if (!file.exists()) null
        else try {
            gson.fromJson(file.readText(), Project::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Delete a project JSON file.
     */
    suspend fun deleteProject(fileName: String): Boolean = withContext(Dispatchers.IO) {
        File(projectsDir, fileName).delete()
    }

    /**
     * Parse a Project from a raw JSON string (used for QR import).
     */
    fun parseFromJson(json: String): Project? {
        return try {
            gson.fromJson(json, Project::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Serialize a Project to a JSON string.
     */
    fun toJson(project: Project): String = gson.toJson(project)
}
