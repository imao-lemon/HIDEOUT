package com.example.hideout

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.google.firebase.database.ValueEventListener

private val Ink = Color(0xFF101820)
private val Panel = Color(0xFF1B2732)
private val Lime = Color(0xFFC6F36B)
private val Sky = Color(0xFF7CCBFF)
private val Muted = Color(0xFF9EADB8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HideoutApp() }
    }
}

private enum class Team { HUNTERS, RUNNERS }
private enum class Screen { LOBBY, MAP, CHAT }

@Composable
fun HideoutApp() {
    var screen by remember { mutableStateOf(Screen.LOBBY) }
    var roomCode by remember { mutableStateOf("") }
    var joinCode by remember { mutableStateOf("") }
    var created by remember { mutableStateOf(false) }
    var team by remember { mutableStateOf(Team.RUNNERS) }
    var gameState by remember { mutableStateOf(RoomGameState()) }
    var isHost by remember { mutableStateOf(false) }
    var clockTick by remember { mutableIntStateOf(0) }
    var countdownTick by remember { mutableIntStateOf(0) }
    var pingMessage by remember { mutableStateOf("No pings sent yet") }
    var chatInput by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf("Welcome to HIDEOUT 👋", "Agree on a safe play area before starting.") }

    // Firebase multiplayer state
    val roomManager = remember { RoomManager() }
    var playerName by remember { mutableStateOf("Player") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Create a room or join a friend's room.") }
    var players by remember { mutableStateOf<List<RoomPlayer>>(emptyList()) }
    var playerListener by remember { mutableStateOf<ValueEventListener?>(null) }
    var hostListener by remember { mutableStateOf<ValueEventListener?>(null) }
    var gameListener by remember { mutableStateOf<ValueEventListener?>(null) }

    DisposableEffect(roomCode) {
        if (roomCode.isNotBlank()) {
            playerListener = roomManager.observePlayers(
                code = roomCode,
                onUpdate = { players = it },
                onError = { status = "Database error: $it" }
            )
            hostListener = roomManager.observeHost(
                code = roomCode,
                onUpdate = { isHost = it },
                onError = { status = "Host check failed: $it" }
            )
            gameListener = roomManager.observeGameState(
                code = roomCode,
                onUpdate = { gameState = it },
                onError = { status = "Game state error: $it" }
            )
        } else {
            players = emptyList()
            isHost = false
            gameState = RoomGameState()
        }
        onDispose {
            playerListener?.let { roomManager.removePlayerListener(roomCode, it) }
            hostListener?.let { roomManager.removeHostListener(roomCode, it) }
            gameListener?.let { roomManager.removeGameStateListener(roomCode, it) }
            playerListener = null
            hostListener = null
            gameListener = null
        }
    }

    // Each device derives both countdowns from the same Firebase server time.
    // Tick state is read below so Compose refreshes the displayed seconds.
    LaunchedEffect(roomCode, gameState.status, gameState.startedAt) {
        if (roomCode.isBlank()) return@LaunchedEffect

        when (gameState.status) {
            "COUNTDOWN" -> {
                while (true) {
                    val millisLeft = gameState.startedAt - roomManager.serverNowMillis()
                    countdownTick++
                    if (millisLeft <= 0L) {
                        if (isHost) roomManager.activateGame(roomCode)
                        break
                    }
                    delay(minOf(1000L, millisLeft.coerceAtLeast(1L)))
                }
            }
            "RUNNING" -> {
                val endAt = gameState.startedAt + gameState.durationSeconds * 1000L
                while (true) {
                    val millisLeft = endAt - roomManager.serverNowMillis()
                    clockTick++
                    if (millisLeft <= 0L) break
                    delay(minOf(1000L, millisLeft.coerceAtLeast(1L)))
                }
                clockTick++
            }
        }
    }

    val countdownSeconds = if (gameState.status == "COUNTDOWN" && gameState.startedAt > 0L) {
        val millisLeft = gameState.startedAt - roomManager.serverNowMillis() + countdownTick.toLong() * 0L
        ((millisLeft.coerceAtLeast(0L) + 999L) / 1000L).toInt()
    } else 0

    val remaining = if (gameState.status == "RUNNING" && gameState.startedAt > 0L) {
        val endAt = gameState.startedAt + gameState.durationSeconds * 1000L
        val millisLeft = endAt - roomManager.serverNowMillis() + clockTick.toLong() * 0L
        ((millisLeft.coerceAtLeast(0L) + 999L) / 1000L).toInt()
    } else gameState.durationSeconds
    val running = gameState.status == "RUNNING" && remaining > 0

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Lime,
            secondary = Sky,
            background = Ink,
            surface = Panel,
            onPrimary = Ink,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        Scaffold(
            containerColor = Ink,
            bottomBar = {
                NavigationBar(containerColor = Panel) {
                    NavigationBarItem(
                        selected = screen == Screen.LOBBY,
                        onClick = { screen = Screen.LOBBY },
                        icon = { Text("⌂", fontSize = 20.sp) },
                        label = { Text("Lobby") }
                    )
                    NavigationBarItem(
                        selected = screen == Screen.MAP,
                        onClick = { screen = Screen.MAP },
                        icon = { Text("◎", fontSize = 20.sp) },
                        label = { Text("Map") }
                    )
                    NavigationBarItem(
                        selected = screen == Screen.CHAT,
                        onClick = { screen = Screen.CHAT },
                        icon = { Text("✉", fontSize = 20.sp) },
                        label = { Text("Chat") }
                    )
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("HIDEOUT", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Lime)
                        Text("REAL-WORLD HIDE & SEEK", fontSize = 11.sp, color = Muted, letterSpacing = 2.sp)
                    }
                    Surface(color = Color(0xFF293923), shape = RoundedCornerShape(50)) {
                        Text("BETA 0.1", modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = Lime, fontSize = 11.sp)
                    }
                }
                when (screen) {
                    Screen.LOBBY -> LobbyScreen(
                        created = created,
                        roomCode = roomCode,
                        joinCode = joinCode,
                        onJoinCode = { joinCode = it.uppercase().take(6) },
                        playerName = playerName,
                        onPlayerName = { playerName = it.take(20) },
                        onCreate = {
                            if (playerName.isBlank()) {
                                status = "Enter a player name first."
                            } else {
                                busy = true
                                status = "Creating room…"
                                roomManager.createRoom(playerName.trim(), team.name) { code, error ->
                                    busy = false
                                    if (code != null) {
                                        roomCode = code
                                        created = true
                                        status = "Room created. Share the code with friends."
                                    } else {
                                        status = error ?: "Could not create room."
                                    }
                                }
                            }
                        },
                        onJoin = {
                            if (playerName.isBlank()) {
                                status = "Enter a player name first."
                            } else {
                                busy = true
                                status = "Joining room…"
                                roomManager.joinRoom(joinCode, playerName.trim(), team.name) { code, error ->
                                    busy = false
                                    if (code != null) {
                                        roomCode = code
                                        created = true
                                        status = "Joined room successfully!"
                                    } else {
                                        status = error ?: "Could not join room."
                                    }
                                }
                            }
                        },
                        team = team,
                        onTeam = { team = it },
                        running = running,
                        remaining = remaining,
                        gameState = gameState,
                        countdownSeconds = countdownSeconds,
                        onStart = {
                            if (roomCode.isNotBlank() && isHost && !running) {
                                status = "Starting game…"
                                roomManager.startGame(roomCode) { success, error ->
                                    status = if (success) "Game started for everyone!" else (error ?: "Could not start game")
                                }
                            }
                        },
                        onReset = {
                            if (roomCode.isNotBlank() && isHost) {
                                roomManager.resetGame(roomCode) { success, error ->
                                    status = if (success) "Game timer reset for everyone." else (error ?: "Could not reset timer")
                                }
                            }
                        },
                        onGoMap = { screen = Screen.MAP },
                        players = players,
                        status = status,
                        busy = busy,
                        isHost = isHost
                    )
                    Screen.MAP -> MapScreen(
                        roomCode = roomCode,
                        running = running,
                        remaining = remaining,
                        countdownSeconds = countdownSeconds,
                        gameStatus = gameState.status,
                        pingMessage = pingMessage,
                        onPing = { pingMessage = "Ping requested • demo only • ${if (team == Team.HUNTERS) "runner hint pending" else "your team was notified"}" },
                        onStart = {
                            if (roomCode.isNotBlank() && isHost && !running) {
                                status = "Starting game…"
                                roomManager.startGame(roomCode) { success, error ->
                                    status = if (success) "Game started for everyone!" else (error ?: "Could not start game")
                                }
                            }
                        },
                        isHost = isHost
                    )
                    Screen.CHAT -> ChatScreen(
                        messages = messages,
                        input = chatInput,
                        onInput = { chatInput = it },
                        onSend = {
                            if (chatInput.isNotBlank()) {
                                messages.add("You: ${chatInput.trim()}")
                                chatInput = ""
                            }
                        }
                    )
                }
                Text(
                    "Rooms, host controls and the shared countdown use Firebase. Map and chat are still demos.",
                    color = Muted, fontSize = 11.sp
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun LobbyScreen(
    created: Boolean,
    roomCode: String,
    joinCode: String,
    onJoinCode: (String) -> Unit,
    playerName: String,
    onPlayerName: (String) -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    team: Team,
    onTeam: (Team) -> Unit,
    running: Boolean,
    remaining: Int,
    gameState: RoomGameState,
    countdownSeconds: Int,
    onStart: () -> Unit,
    onReset: () -> Unit,
    onGoMap: () -> Unit,
    players: List<RoomPlayer>,
    status: String,
    busy: Boolean,
    isHost: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardBlock {
            Text("YOUR GAME", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = playerName,
                onValueChange = onPlayerName,
                label = { Text("Player name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            if (created) {
                Text(roomCode, color = Lime, fontSize = 34.sp, fontWeight = FontWeight.Black, letterSpacing = 5.sp)
                Text("Share this code with your friends", color = Muted, fontSize = 13.sp)
            } else {
                Text("Create a room for free", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("One person hosts. Friends join with a 6-character code.", color = Muted, fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onCreate,
                enabled = !busy && playerName.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (busy) "PLEASE WAIT…" else if (created) "CREATE NEW ROOM" else "＋  CREATE GAME")
            }
            Text(status, color = Sky, fontSize = 12.sp)
        }
        CardBlock {
            Text("JOIN A GAME", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = joinCode,
                onValueChange = onJoinCode,
                label = { Text("6-character game code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onJoin,
                enabled = !busy && playerName.isNotBlank() && joinCode.length == 6,
                modifier = Modifier.fillMaxWidth()
            ) { Text("JOIN ROOM") }
        }
        CardBlock {
            Text("PLAYERS IN ROOM (${players.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            if (players.isEmpty()) {
                Text("Create or join a room to see players here.", color = Muted, fontSize = 12.sp)
            } else {
                players.forEach { player ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(player.name, fontWeight = FontWeight.Medium)
                        Text(
                            if (player.team == "HUNTERS") "🕵️ HUNTERS" else "🏃 RUNNERS",
                            color = Lime,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
        CardBlock {
            Text("PICK YOUR TEAM", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TeamButton("🏃 RUNNERS", team == Team.RUNNERS, Modifier.weight(1f)) { onTeam(Team.RUNNERS) }
                TeamButton("🕵️ HUNTERS", team == Team.HUNTERS, Modifier.weight(1f)) { onTeam(Team.HUNTERS) }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            gameState.status == "COUNTDOWN" -> "GET READY"
                            running -> "ROUND IN PROGRESS"
                            gameState.status == "RUNNING" && remaining == 0 -> "ROUND COMPLETE"
                            else -> "10:00 ROUND TIMER"
                        },
                        color = Lime,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        when {
                            gameState.status == "COUNTDOWN" -> "Starting in ${countdownSeconds.coerceAtLeast(0)}…"
                            running || (gameState.status == "RUNNING" && remaining == 0) -> formatTime(remaining)
                            else -> "Start when everyone is ready"
                        },
                        color = Muted,
                        fontSize = 12.sp
                    )
                    Text(if (isHost) "You are the host" else "Only the host can start or reset", color = Sky, fontSize = 11.sp)
                }
                Button(onClick = onStart, enabled = created && isHost && !running && gameState.status != "COUNTDOWN") { Text("START") }
            }
            OutlinedButton(onClick = onReset, enabled = created && isHost, modifier = Modifier.fillMaxWidth()) { Text("RESET TIMER") }
            TextButton(onClick = onGoMap, modifier = Modifier.fillMaxWidth()) { Text("OPEN PLAY ZONE →") }
        }
    }
}

@Composable
private fun MapScreen(
    roomCode: String,
    running: Boolean,
    remaining: Int,
    countdownSeconds: Int,
    gameStatus: String,
    pingMessage: String,
    onPing: () -> Unit,
    onStart: () -> Unit,
    isHost: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardBlock {
            Text("PLAY ZONE", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(if (roomCode.isBlank()) "Create or join a room first" else "ROOM $roomCode", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(260.dp)
                    .background(Color(0xFF243641), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Placeholder map: replace with a real map SDK and live location markers.
                Box(Modifier.size(210.dp).background(Color(0x223CCB88), CircleShape))
                Box(Modifier.size(150.dp).background(Color(0x224CCBFF), CircleShape))
                Box(Modifier.size(90.dp).background(Color(0x22C6F36B), CircleShape))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("◎", fontSize = 46.sp, color = Lime)
                    Text("MAP PREVIEW", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Live GPS will be connected next", color = Muted, fontSize = 11.sp)
                }
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                    color = Ink.copy(alpha = 0.9f), shape = RoundedCornerShape(8.dp)
                ) { Text("ZONE SHRINK DEMO", modifier = Modifier.padding(8.dp), fontSize = 10.sp, color = Lime) }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when (gameStatus) {
                            "COUNTDOWN" -> "GET READY"
                            "RUNNING" -> if (running) "ROUND LIVE" else "ROUND COMPLETE"
                            else -> "ROUND NOT STARTED"
                        },
                        color = if (running || gameStatus == "COUNTDOWN") Lime else Muted,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (gameStatus == "COUNTDOWN") countdownSeconds.coerceAtLeast(0).toString() else formatTime(remaining),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(if (isHost) "Host controls enabled" else "Waiting for host", color = Sky, fontSize = 11.sp)
                }
                Button(onClick = onStart, enabled = roomCode.isNotBlank() && isHost && !running && gameStatus != "COUNTDOWN") { Text("START") }
            }
        }
        CardBlock {
            Text("HUNTER LOCATION PING", fontWeight = FontWeight.Bold)
            Text("Pings will be rate-limited and only share permitted game hints.", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onPing, modifier = Modifier.fillMaxWidth(), enabled = roomCode.isNotBlank()) { Text("REQUEST / SEND DEMO PING") }
            Spacer(Modifier.height(4.dp))
            Text(pingMessage, color = Sky, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ChatScreen(
    messages: List<String>,
    input: String,
    onInput: (String) -> Unit,
    onSend: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("TEAM CHAT", fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text("Local demo chat • real-time delivery comes in the backend phase", color = Muted, fontSize = 12.sp)
        CardBlock(modifier = Modifier.weight(1f)) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                items(messages) { message ->
                    Surface(color = Color(0xFF263541), shape = RoundedCornerShape(12.dp)) {
                        Text(message, modifier = Modifier.fillMaxWidth().padding(12.dp), fontSize = 14.sp)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = onInput,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message your team…") },
                singleLine = true
            )
            Button(onClick = onSend, enabled = input.isNotBlank()) { Text("SEND") }
        }
    }
}

@Composable
private fun CardBlock(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier.fillMaxWidth(), color = Panel, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun TeamButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier.height(48.dp)) { Text(label, fontSize = 12.sp) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier.height(48.dp)) { Text(label, fontSize = 12.sp) }
    }
}

private fun formatTime(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return "%02d:%02d".format(safe / 60, safe % 60)
}
