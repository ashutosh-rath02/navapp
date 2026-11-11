package me.ashutosh.navapp.navigation

import me.ashutosh.navapp.mapping.OccupancyGrid
import java.util.PriorityQueue
import kotlin.math.abs

/** Conservative 8-connected A* over known-free cells. Unknown and occupied cells are blocked. */
object AStarPlanner {
    data class Cell(val x: Int, val y: Int)
    private data class Node(val cell: Cell, val g: Float, val f: Float)

    fun plan(grid: OccupancyGrid, start: Cell, goal: Cell, snapshot: FloatArray = grid.snapshot()): List<Cell> {
        if (!grid.contains(start.x, start.y) || !grid.contains(goal.x, goal.y)) return emptyList()
        if (!grid.isKnownFree(start.x, start.y, snapshot) || !grid.isKnownFree(goal.x, goal.y, snapshot)) return emptyList()
        val open = PriorityQueue<Node>(compareBy { it.f })
        val cameFrom = HashMap<Int, Int>()
        val gScore = HashMap<Int, Float>()
        val startId = id(start, grid.width)
        val goalId = id(goal, grid.width)
        gScore[startId] = 0f
        open.add(Node(start, 0f, heuristic(start, goal)))
        val offsets = arrayOf(-1 to -1, 0 to -1, 1 to -1, -1 to 0, 1 to 0, -1 to 1, 0 to 1, 1 to 1)
        while (open.isNotEmpty()) {
            val current = open.poll()
            val currentId = id(current.cell, grid.width)
            if (current.g > (gScore[currentId] ?: Float.POSITIVE_INFINITY)) continue
            if (currentId == goalId) return reconstruct(cameFrom, currentId, grid.width)
            for ((dx, dy) in offsets) {
                val nx = current.cell.x + dx; val ny = current.cell.y + dy
                if (!grid.isKnownFree(nx, ny, snapshot)) continue
                if (dx != 0 && dy != 0 && (!grid.isKnownFree(current.cell.x + dx, current.cell.y, snapshot) || !grid.isKnownFree(current.cell.x, current.cell.y + dy, snapshot))) continue
                val next = Cell(nx, ny); val nextId = id(next, grid.width)
                val tentative = current.g + if (dx == 0 || dy == 0) 1f else 1.4142f
                if (tentative < (gScore[nextId] ?: Float.POSITIVE_INFINITY)) {
                    cameFrom[nextId] = currentId; gScore[nextId] = tentative
                    open.add(Node(next, tentative, tentative + heuristic(next, goal)))
                }
            }
        }
        return emptyList()
    }

    private fun heuristic(a: Cell, b: Cell): Float {
        val dx = abs(a.x - b.x).toFloat(); val dy = abs(a.y - b.y).toFloat()
        return maxOf(dx, dy) + (1.4142f - 1f) * minOf(dx, dy)
    }
    private fun id(c: Cell, width: Int) = c.y * width + c.x
    private fun reconstruct(parent: Map<Int, Int>, end: Int, width: Int): List<Cell> {
        val result = ArrayList<Cell>(); var cursor = end
        result.add(Cell(cursor % width, cursor / width))
        while (parent.containsKey(cursor)) { cursor = parent.getValue(cursor); result.add(Cell(cursor % width, cursor / width)) }
        result.reverse(); return result
    }
}
