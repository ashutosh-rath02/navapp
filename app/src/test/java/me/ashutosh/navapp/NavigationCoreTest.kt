package me.ashutosh.navapp

import me.ashutosh.navapp.mapping.OccupancyGrid
import me.ashutosh.navapp.navigation.AStarPlanner
import org.junit.Assert.*
import org.junit.Test

class NavigationCoreTest {
    @Test fun astarFindsRouteAcrossKnownFreeRay() {
        val grid = OccupancyGrid(40, 40, 0.1f)
        grid.updateRay(0f, 0f, 0.8f, 0f, hit = false)
        val start = AStarPlanner.Cell(grid.originX, grid.originY)
        val goal = AStarPlanner.Cell(grid.originX + 8, grid.originY)
        val path = AStarPlanner.plan(grid, start, goal)
        assertEquals(start, path.first())
        assertEquals(goal, path.last())
    }

    @Test fun astarDoesNotCrossOccupiedCell() {
        val grid = OccupancyGrid(40, 40, 0.1f)
        grid.updateRay(0f, 0f, 0.8f, 0f, hit = false)
        grid.updateRay(0f, 0f, 0.4f, 0f, hit = true)
        val path = AStarPlanner.plan(
            grid,
            AStarPlanner.Cell(grid.originX, grid.originY),
            AStarPlanner.Cell(grid.originX + 8, grid.originY)
        )
        assertTrue(path.isEmpty())
    }

    @Test fun unknownCellsAreNotTraversable() {
        val grid = OccupancyGrid(40, 40, 0.1f)
        val path = AStarPlanner.plan(
            grid,
            AStarPlanner.Cell(grid.originX, grid.originY),
            AStarPlanner.Cell(grid.originX + 4, grid.originY)
        )
        assertTrue(path.isEmpty())
    }
}
