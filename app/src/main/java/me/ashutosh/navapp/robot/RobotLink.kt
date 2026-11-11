package me.ashutosh.navapp.robot

import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Transport adapter. ESP32 firmware protocol is intentionally configurable; do not assume OpenBot framing. */
class RobotLink(private val listener: Listener) {
    interface Listener { fun onState(connected: Boolean, message: String); fun onTelemetry(json: String) }
    private val client = OkHttpClient.Builder().pingInterval(2, TimeUnit.SECONDS).build()
    private var socket: WebSocket? = null

    @Synchronized fun connect(host: String, port: Int = 81) {
        disconnect()
        val request = Request.Builder().url("ws://$host:$port/").build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = listener.onState(true, "Connected to $host:$port")
            override fun onMessage(webSocket: WebSocket, text: String) = listener.onTelemetry(text)
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = Unit
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason); listener.onState(false, "Closing") }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = listener.onState(false, "Disconnected")
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = listener.onState(false, t.message ?: "Connection failed")
        })
    }

    @Synchronized fun sendDrive(linear: Float, angular: Float) {
        val payload = JSONObject().put("type", "drive").put("v", linear.coerceIn(-0.25f, 0.25f))
            .put("w", angular.coerceIn(-1.0f, 1.0f)).put("ttl_ms", 300).toString()
        socket?.send(payload)
    }

    @Synchronized fun stop() { socket?.send(JSONObject().put("type", "stop").toString()) }
    @Synchronized fun disconnect() { stop(); socket?.close(1000, "user disconnect"); socket = null }
}
