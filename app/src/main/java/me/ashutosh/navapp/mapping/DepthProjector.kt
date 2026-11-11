package me.ashutosh.navapp.mapping

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

/**
 * Converts an ARCore-style 16-bit depth image (millimetres) into 2D occupancy rays.
 * Pose is expressed in the map frame: x=right, y=forward, yaw=heading from +Y.
 * Camera is assumed level and aligned with the robot. Calibrate the real camera-to-base
 * transform before using this for navigation; this class deliberately does not estimate pose.
 */
class DepthProjector(
    private val grid: OccupancyGrid,
    private val minDepthM: Float = 0.18f,
    private val maxDepthM: Float = 4.0f,
    private val sampleStep: Int = 8
) {
    data class Intrinsics(val fx: Float, val fy: Float, val cx: Float, val cy: Float)
    data class Pose2D(val x: Float, val y: Float, val yawRadians: Float)

    fun integrate(
        depth: ByteBuffer,
        width: Int,
        height: Int,
        rowStrideBytes: Int,
        pixelStrideBytes: Int,
        intrinsics: Intrinsics,
        pose: Pose2D
    ): Int {
        require(width > 0 && height > 0 && rowStrideBytes >= width * pixelStrideBytes)
        require(intrinsics.fx > 0f && intrinsics.fy > 0f)
        val buffer = depth.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val c = cos(pose.yawRadians); val s = sin(pose.yawRadians)
        var integrated = 0
        for (v in sampleStep until height step sampleStep) {
            for (u in sampleStep until width step sampleStep) {
                val offset = v * rowStrideBytes + u * pixelStrideBytes
                if (offset + 1 >= buffer.limit()) continue
                val packedDepth = buffer.getShort(offset).toInt() and 0xffff
                val millimetres = packedDepth and 0x1fff
                if (millimetres == 0) continue
                val z = millimetres / 1000f
                if (z !in minDepthM..maxDepthM) continue
                val cameraRight = (u - intrinsics.cx) / intrinsics.fx * z
                val cameraForward = z
                val worldX = pose.x + cameraRight * c + cameraForward * s
                val worldY = pose.y - cameraRight * s + cameraForward * c
                grid.updateRay(pose.x, pose.y, worldX, worldY, hit = true)
                integrated++
            }
        }
        return integrated
    }
}
