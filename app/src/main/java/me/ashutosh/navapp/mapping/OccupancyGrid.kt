package me.ashutosh.navapp.mapping

import kotlin.math.floor

/** Log-odds occupancy grid in the robot's local map frame. Unknown=0, free<0, occupied>0. */
class OccupancyGrid(
    val width: Int = 240,
    val height: Int = 240,
    val resolutionMeters: Float = 0.05f
) {
    private val cells = FloatArray(width * height)
    var originX = width / 2
        private set
    var originY = height / 2
        private set

    @Synchronized fun clear() { cells.fill(0f); originX = width / 2; originY = height / 2 }

    @Synchronized fun updateRay(x0m: Float, y0m: Float, x1m: Float, y1m: Float, hit: Boolean) {
        val x0 = originX + floor(x0m / resolutionMeters).toInt()
        val y0 = originY - floor(y0m / resolutionMeters).toInt()
        val x1 = originX + floor(x1m / resolutionMeters).toInt()
        val y1 = originY - floor(y1m / resolutionMeters).toInt()
        val dx = kotlin.math.abs(x1 - x0); val sx = if (x0 < x1) 1 else -1
        val dy = -kotlin.math.abs(y1 - y0); val sy = if (y0 < y1) 1 else -1
        var err = dx + dy; var x = x0; var y = y0
        while (true) {
            val isEnd = x == x1 && y == y1
            add(x, y, if (isEnd && hit) 0.85f else -0.35f)
            if (isEnd) break
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x += sx }
            if (e2 <= dx) { err += dx; y += sy }
        }
    }

    @Synchronized private fun add(x: Int, y: Int, delta: Float) {
        if (x !in 0 until width || y !in 0 until height) return
        val i = y * width + x
        cells[i] = (cells[i] + delta).coerceIn(-4f, 4f)
    }

    @Synchronized fun snapshot(): FloatArray = cells.copyOf()
    fun contains(x: Int, y: Int) = x in 0 until width && y in 0 until height
    fun valueAt(x: Int, y: Int, snapshot: FloatArray): Float = if (contains(x, y)) snapshot[y * width + x] else 4f
    fun isKnownFree(x: Int, y: Int, snapshot: FloatArray): Boolean = contains(x, y) && snapshot[y * width + x] < -0.15f

    @Synchronized fun replace(values: FloatArray, newOriginX: Int, newOriginY: Int) {
        require(values.size == cells.size)
        require(newOriginX in 0 until width && newOriginY in 0 until height)
        values.copyInto(cells); originX = newOriginX; originY = newOriginY
    }
}
