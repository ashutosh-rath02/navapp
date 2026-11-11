package me.ashutosh.navapp

import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import me.ashutosh.navapp.camera.CameraPreview
import me.ashutosh.navapp.mapping.OccupancyGrid
import me.ashutosh.navapp.robot.RobotLink
import me.ashutosh.navapp.navigation.AStarPlanner
import me.ashutosh.navapp.persistence.MapStore

private val Bg = Color(0xFF101719); private val Panel = Color(0xFF1A2528); private val Mint = Color(0xFF45D6A5)

class MainActivity : ComponentActivity(), RobotLink.Listener {
    private val grid = OccupancyGrid(400, 400, 0.05f)
    private lateinit var robot: RobotLink
    private var connection by mutableStateOf("Not connected")
    private var connected by mutableStateOf(false)
    private var telemetry by mutableStateOf("No telemetry")
    private var mapMessage by mutableStateOf("No saved map")
    private var route by mutableStateOf(emptyList<AStarPlanner.Cell>())
    private var mapRevision by mutableIntStateOf(0)
    private var reloadSavedMapOnResume = false
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        robot = RobotLink(this)
        mapMessage = if (MapStore.load(this, grid)) "Saved map loaded" else "No saved map yet"
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.CAMERA)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Mint, background = Bg, surface = Panel)) {
                NavScreen(
                    connection = connection,
                    connected = connected,
                    telemetry = telemetry,
                    mapMessage = mapMessage,
                    grid = grid,
                    route = route,
                    mapRevision = mapRevision,
                    onConnect = { host, port -> robot.connect(host, port) },
                    onDisconnect = { robot.disconnect(); connected = false },
                    onStop = { robot.stop() },
                    onDrive = { v, w -> robot.sendDrive(v, w) },
                    onMapTap = { x, y ->
                        route = AStarPlanner.plan(grid, AStarPlanner.Cell(grid.originX, grid.originY), AStarPlanner.Cell(x, y))
                        mapMessage = if (route.isEmpty()) "No route: cells must be observed as free first" else "Route preview: ${route.size} cells"
                    },
                    onScan = {
                        reloadSavedMapOnResume = true
                        startActivity(Intent(this, me.ashutosh.navapp.scan.HouseScanActivity::class.java))
                    },
                    onSave = {
                        runCatching { MapStore.save(this, grid); mapMessage = "Map saved on this device" }
                            .onFailure { mapMessage = "Save failed: ${it.message}" }
                    },
                    onLoad = {
                        val loaded = MapStore.load(this, grid)
                        mapMessage = if (loaded) "Saved map loaded" else "No compatible saved map found"
                        route = emptyList()
                        if (loaded) mapRevision++
                    },
                    onClear = {
                        grid.clear()
                        route = emptyList()
                        mapRevision++
                        mapMessage = "Map cleared"
                    }
                )
            }
        }
    }
    override fun onResume() {
        super.onResume()
        if (reloadSavedMapOnResume) {
            reloadSavedMapOnResume = false
            val loaded = MapStore.load(this, grid)
            mapMessage = if (loaded) "Scanned map loaded" else "No saved scan found"
            route = emptyList()
            if (loaded) mapRevision++
        }
    }
    override fun onState(connected: Boolean, message: String) { runOnUiThread { this.connected = connected; connection = message } }
    override fun onTelemetry(json: String) { runOnUiThread { telemetry = json.take(160) } }
    override fun onDestroy() { if (::robot.isInitialized) robot.disconnect(); super.onDestroy() }
}

@Composable private fun NavScreen(
    connection: String,
    connected: Boolean,
    telemetry: String,
    mapMessage: String,
    grid: OccupancyGrid,
    route: List<AStarPlanner.Cell>,
    mapRevision: Int,
    onConnect: (String, Int) -> Unit,
    onDisconnect: () -> Unit,
    onStop: () -> Unit,
    onDrive: (Float, Float) -> Unit,
    onMapTap: (Int, Int) -> Unit,
    onScan: () -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onClear: () -> Unit
) {
    var host by remember { mutableStateOf("192.168.4.1") }
    var port by remember { mutableStateOf("81") }
    Column(
        Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("NAVAPP", color = Mint, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("FIELD MAPPING & ROBOT CONTROL", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
            }
            Surface(color = if (connected) Color(0xFF173B32) else Panel, shape = CircleShape) {
                Text(
                    if (connected) "ONLINE" else "OFFLINE",
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = if (connected) Mint else Color.LightGray,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        SectionCard("LIVE CAMERA", "Rear camera preview") {
            Box(Modifier.fillMaxWidth().height(190.dp).background(Color(0xFF0C1214), RoundedCornerShape(12.dp))) {
                CameraPreview(Modifier.fillMaxSize())
            }
        }

        SectionCard("MAP", mapMessage) {
            OccupancyMap(grid, route, mapRevision, Modifier.fillMaxWidth().height(280.dp), onMapTap)
            Text("Tap a known-free cell to preview a route. Unknown cells are blocked.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        }

        Button(onClick = onScan, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp)) {
            Text("WALK & SCAN MY HOUSE", fontWeight = FontWeight.SemiBold)
        }

        SectionCard("MAP TOOLS", "Maps are stored locally on this device") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f)) { Text("Save") }
                OutlinedButton(onClick = onLoad, modifier = Modifier.weight(1f)) { Text("Load") }
                OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) { Text("Clear") }
            }
        }

        SectionCard("ROBOT LINK", connection) {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("ESP32 address") },
                singleLine = true
            )
            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter(Char::isDigit).take(5) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Port") },
                singleLine = true
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onConnect(host.trim(), port.toIntOrNull() ?: 81) },
                    enabled = !connected && host.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text("Connect") }
                OutlinedButton(onClick = onDisconnect, enabled = connected, modifier = Modifier.weight(1f)) { Text("Disconnect") }
            }
            Text("Telemetry", color = Mint, style = MaterialTheme.typography.labelLarge)
            Text(telemetry, color = Color.LightGray, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }

        SectionCard("MANUAL DRIVE", "Low-speed controls · autonomous driving is not enabled") {
            DrivePad(onDrive, onStop)
        }
        Text("Verify the robot is safely supported before testing motor controls.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun SectionCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = Mint, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
            }
            content()
        }
    }
}

@Composable private fun DrivePad(onDrive: (Float, Float) -> Unit, onStop: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.weight(1f))
            DriveButton("↑", Modifier.weight(1f)) { onDrive(0.12f, 0f) }
            Spacer(Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DriveButton("↶", Modifier.weight(1f)) { onDrive(0f, 0.45f) }
            Button(
                onClick = onStop,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C3434))
            ) { Text("STOP", fontWeight = FontWeight.Bold) }
            DriveButton("↷", Modifier.weight(1f)) { onDrive(0f, -0.45f) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.weight(1f))
            DriveButton("↓", Modifier.weight(1f)) { onDrive(-0.10f, 0f) }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable private fun DriveButton(label: String, modifier: Modifier = Modifier, action: () -> Unit) {
    OutlinedButton(onClick = action, modifier = modifier.height(52.dp), shape = RoundedCornerShape(12.dp)) {
        Text(label, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable private fun OccupancyMap(grid: OccupancyGrid, route: List<AStarPlanner.Cell>, mapRevision: Int, modifier: Modifier = Modifier, onTap: (Int,Int)->Unit) {
    val cells = remember(grid, mapRevision) { grid.snapshot() }
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
