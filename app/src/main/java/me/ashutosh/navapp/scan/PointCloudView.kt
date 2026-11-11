package me.ashutosh.navapp.scan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/** Lightweight interactive 3D point-cloud viewer. Drag to orbit; pinch to zoom. */
class PointCloudView(context: Context, private val points: () -> FloatArray) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var yaw = 0.65f
    private var pitch = -0.22f
    private var zoom = 1.0f
    private var lastX = 0f
    private var lastY = 0f
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoom = (zoom * detector.scaleFactor).coerceIn(0.25f, 5f)
            invalidate()
            return true
        }
    })

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(12, 18, 23))
        val data = points()
        val count = data.size / 3
        paint.color = Color.WHITE
        paint.textSize = 34f
        canvas.drawText("3D HOUSE SCAN  ·  $count points", 28f, 48f, paint)
        paint.textSize = 25f
        paint.color = Color.LTGRAY
        canvas.drawText("Drag to rotate  ·  Pinch to zoom  ·  Tap Camera to return", 28f, 84f, paint)
        if (count == 0) {
            paint.color = Color.WHITE
            canvas.drawText("Start walking to build the 3D point cloud", 28f, height * 0.52f, paint)
            return
        }
        // Fit the current scan into the viewport while preserving its metric proportions.
        var maxRadius = 0.5f
        for (i in 0 until count) {
            val x=data[i*3]; val y=data[i*3+1]; val z=data[i*3+2]
            maxRadius = maxOf(maxRadius, kotlin.math.sqrt(x*x+y*y+z*z))
        }
        val scale = minOf(width, height) * 0.42f / maxRadius * zoom
        val cy=cos(yaw); val sy=sin(yaw); val cp=cos(pitch); val sp=sin(pitch)
        val cx=width*0.5f; val centerY=height*0.54f
        // Draw back-to-front approximately by camera-space depth.
        val projected = ArrayList<Pair<Float,Int>>(count)
        for (i in 0 until count) {
            val x=data[i*3]; val y=data[i*3+1]; val z=data[i*3+2]
            val rx=x*cy-z*sy
            val rz=x*sy+z*cy
            val ry=y*cp-rz*sp
            val depth=y*sp+rz*cp
            projected.add(depth to i)
        }
        projected.sortByDescending { it.first }
        for ((depth,i) in projected) {
            val x=data[i*3]; val y=data[i*3+1]; val z=data[i*3+2]
            val rx=x*cy-z*sy
            val rz=x*sy+z*cy
            val ry=y*cp-rz*sp
            val d=y*sp+rz*cp
            val sx=cx+rx*scale
            val syc=centerY-ry*scale
            if(sx < -4 || sx > width+4 || syc < 100 || syc > height+4) continue
            val shade=(205 + (d/maxRadius*35)).toInt().coerceIn(110,255)
            paint.color=Color.rgb(shade, (shade*0.91f).toInt(), (shade*0.78f).toInt())
            paint.strokeWidth=3f
            canvas.drawPoint(sx,syc,paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastX=event.x; lastY=event.y; return true }
            MotionEvent.ACTION_MOVE -> if(!scaleDetector.isInProgress) {
                yaw += (event.x-lastX)*0.008f
                pitch = (pitch+(event.y-lastY)*0.008f).coerceIn(-1.35f,1.35f)
                lastX=event.x; lastY=event.y; invalidate()
            }
        }
        return true
    }
}
