package me.ashutosh.navapp

import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import me.ashutosh.navapp.camera.CameraPreview
import me.ashutosh.navapp.mapping.OccupancyGrid
import me.ashutosh.navapp.robot.RobotLink
import me.ashutosh.navapp.navigation.AStarPlanner
import me.ashutosh.navapp.persistence.MapStore

private val Bg = Color(0xFF101719); private val Panel = Color(0xFF1A2528); private val Mint = Color(0xFF45D6A5)

class MainActivity : ComponentActivity(), RobotLink.Listener {
    private val grid = OccupancyGrid()
    private lateinit var robot: RobotLink
    private var connection by mutableStateOf("Not connected")
    private var connected by mutableStateOf(false)
    private var telemetry by mutableStateOf("No telemetry")
    private var mapMessage by mutableStateOf("No saved map")
    private var route by mutableStateOf(emptyList<AStarPlanner.Cell>())
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        robot = RobotLink(this)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.CAMERA)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Mint, background = Bg, surface = Panel)) { NavScreen(connection, connected, telemetry, mapMessage, grid, route, { host, port -> robot.connect(host, port) }, { robot.disconnect(); connected = false }, { robot.stop() }, { v,w -> robot.sendDrive(v,w) }, { x,y -> route = AStarPlanner.plan(grid, AStarPlanner.Cell(grid.originX, grid.originY), AStarPlanner.Cell(x,y)); mapMessage = if (route.isEmpty()) "No route: cells must be observed as free first" else "Route preview: ${route.size} cells" }, { runCatching { MapStore.save(this, grid); mapMessage = "Map saved locally" }.onFailure { mapMessage = "Save failed: ${it.message}" } }, { mapMessage = if (MapStore.load(this, grid)) "Map loaded" else "No compatible saved map"; route = emptyList() }, { grid.clear(); route = emptyList(); mapMessage = "Map cleared" }) } }
    }
    override fun onState(connected: Boolean, message: String) { runOnUiThread { this.connected = connected; connection = message } }
    override fun onTelemetry(json: String) { runOnUiThread { telemetry = json.take(160) } }
    override fun onDestroy() { if (::robot.isInitialized) robot.disconnect(); super.onDestroy() }
}

@Composable private fun NavScreen(connection: String, connected: Boolean, telemetry: String, mapMessage: String, grid: OccupancyGrid, route: List<AStarPlanner.Cell>, onConnect: (String,Int)->Unit, onDisconnect: ()->Unit, onStop: ()->Unit, onDrive: (Float,Float)->Unit, onMapTap: (Int,Int)->Unit, onSave: ()->Unit, onLoad: ()->Unit, onClear: ()->Unit) {
    val context = LocalContext.current
    var host by remember { mutableStateOf("192.168.4.1") }; var port by remember { mutableStateOf("81") }
    Column(Modifier.fillMaxSize().background(Bg).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("NAV", color = Mint, style = MaterialTheme.typography.titleLarge); Spacer(Modifier.width(8.dp)); Text("/ FIELD MAPPER", color = Color.LightGray); Spacer(Modifier.weight(1f)); Text(if (connected) "ONLINE" else "OFFLINE", color = if (connected) Mint else Color.Gray) }
        Row(Modifier.fillMaxWidth().height(190.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f).fillMaxHeight().background(Panel, RoundedCornerShape(12.dp))) { CameraPreview(Modifier.fillMaxSize()) }
            Box(Modifier.weight(1f).fillMaxHeight().background(Panel, RoundedCornerShape(12.dp)).padding(8.dp)) { OccupancyMap(grid, route, Modifier.fillMaxSize(), onMapTap) }
        }
        Button(onClick = { context.startActivity(Intent(context, me.ashutosh.navapp.scan.HouseScanActivity::class.java)) }, modifier = Modifier.fillMaxWidth()) { Text("WALK & SCAN MY HOUSE") }
        Text("Handheld ARCore depth scan · no robot connection needed", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        Text("MAP TOOLS", color = Mint, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSave) { Text("Save") }
            OutlinedButton(onClick = onLoad) { Text("Load") }
            OutlinedButton(onClick = onClear) { Text("Clear") }
        }
        Text(mapMessage, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
        Text("Tap a known-free destination cell to preview an A* route. Unknown cells are blocked.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        Text("ROBOT LINK", color = Mint, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(host, { host = it }, Modifier.weight(1f), label = { Text("ESP32 IP") }, singleLine = true)
            OutlinedTextField(port, { port = it }, Modifier.width(90.dp), label = { Text("Port") }, singleLine = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onConnect(host.trim(), port.toIntOrNull() ?: 81) }, enabled = !connected) { Text("Connect") }
            OutlinedButton(onClick = onDisconnect, enabled = connected) { Text("Disconnect") }
        }
        Text(connection, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
        Text("TELEMETRY", color = Mint, style = MaterialTheme.typography.labelLarge)
        Text(telemetry, color = Color.LightGray, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        Text("MANUAL DRIVE · LOW SPEED", color = Mint, style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            DriveButton("↶", { onDrive(0f, 0.45f) })
            DriveButton("↑", { onDrive(0.12f, 0f) })
            DriveButton("↷", { onDrive(0f, -0.45f) })
            DriveButton("↓", { onDrive(-0.10f, 0f) })
            Button(onClick = onStop, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C3434))) { Text("STOP") }
        }
        Text("Live SLAM is not connected yet. This build does not generate a map from the camera; do not use it for autonomous motion.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun DriveButton(label: String, action: ()->Unit) { OutlinedButton(onClick = action, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) { Text(label) } }

@Composable private fun OccupancyMap(grid: OccupancyGrid, route: List<AStarPlanner.Cell>, modifier: Modifier = Modifier, onTap: (Int,Int)->Unit) {
    val cells = grid.snapshot()
    Canvas(modifier.background(Color(0xFF0C1214)).pointerInput(grid.width, grid.height) { detectTapGestures { p -> val x = (p.x / size.width * grid.width).toInt().coerceIn(0, grid.width-1); val y = (p.y / size.height * grid.height).toInt().coerceIn(0, grid.height-1); onTap(x,y) } }) {
        val cw = size.width / grid.width; val ch = size.height / grid.height
        for (y in 0 until grid.height step 2) for (x in 0 until grid.width step 2) {
            val v = cells[y * grid.width + x]
            if (kotlin.math.abs(v) > 0.08f) drawRect(if (v > 0) Color(0xFFE16B64) else Color(0xFF425D59), androidx.compose.ui.geometry.Offset(x*cw,y*ch), androidx.compose.ui.geometry.Size(cw*2,ch*2))
        }
        if (route.isNotEmpty()) {
            val path = androidx.compose.ui.graphics.Path()
            route.forEachIndexed { i, c -> val px = (c.x + 0.5f) * cw; val py = (c.y + 0.5f) * ch; if (i == 0) path.moveTo(px, py) else path.lineTo(px, py) }
            drawPath(path, Color(0xFF64B5F6), style = Stroke(2.dp.toPx()))
        }
        val cx = size.width * grid.originX / grid.width; val cy = size.height * grid.originY / grid.height
        drawCircle(Mint, 5.dp.toPx(), androidx.compose.ui.geometry.Offset(cx,cy))
        drawLine(Mint, androidx.compose.ui.geometry.Offset(cx,cy), androidx.compose.ui.geometry.Offset(cx,cy-20.dp.toPx()), 2.dp.toPx())
        drawRect(Color.DarkGray, style = Stroke(1.dp.toPx()))
    }
}
