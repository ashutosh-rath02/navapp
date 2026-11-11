package me.ashutosh.navapp.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class PhoneSensors(context: Context, private val onImu: (FloatArray, FloatArray, Long) -> Unit) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accel = FloatArray(3); private val gyro = FloatArray(3)
    fun start() {
        manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }
    fun stop() = manager.unregisterListener(this)
    override fun onSensorChanged(event: SensorEvent) {
        val target = if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) accel else gyro
        event.values.copyInto(target)
        onImu(accel.copyOf(), gyro.copyOf(), event.timestamp)
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
