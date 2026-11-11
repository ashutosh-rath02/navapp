package me.ashutosh.navapp.persistence

import android.content.Context
import me.ashutosh.navapp.mapping.OccupancyGrid
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Versioned local map persistence. A map is saved only when explicitly requested. */
object MapStore {
    private const val FILE_NAME = "navapp-map.json"
    fun save(context: Context, grid: OccupancyGrid): File {
        val values = grid.snapshot(); val cells = JSONArray()
        values.forEach { cells.put(it.toDouble()) }
        val root = JSONObject().put("version", 1).put("width", grid.width)
            .put("height", grid.height).put("resolution_m", grid.resolutionMeters)
            .put("origin_x", grid.originX).put("origin_y", grid.originY).put("cells", cells)
        return File(context.filesDir, FILE_NAME).apply { writeText(root.toString()) }
    }

    fun load(context: Context, grid: OccupancyGrid): Boolean {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return false
        return runCatching {
            val root = JSONObject(file.readText())
            require(root.getInt("version") == 1)
            require(root.getInt("width") == grid.width && root.getInt("height") == grid.height)
            require(root.getDouble("resolution_m").toFloat() == grid.resolutionMeters)
            val cells = root.getJSONArray("cells")
            require(cells.length() == grid.width * grid.height)
            val values = FloatArray(cells.length()) { cells.getDouble(it).toFloat().coerceIn(-4f, 4f) }
            grid.replace(values, root.getInt("origin_x"), root.getInt("origin_y"))
            true
        }.getOrDefault(false)
    }
}
