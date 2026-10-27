package com.example.hideout

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import java.util.UUID
import kotlin.random.Random

data class RoomPlayer(
    val id: String,
    val name: String,
    val team: String
)

data class RoomGameState(
    val status: String = "WAITING",
    val startedAt: Long = 0L,
    val durationSeconds: Int = 600
)

class RoomManager {
    private val root = FirebaseDatabase.getInstance(
        "https://hideout-2705-default-rtdb.firebaseio.com"
    ).reference
    private val database = root.child("rooms")
    // This ID stays the same while this RoomManager instance remains alive.
    private val playerId = UUID.randomUUID().toString()
    private val codeCharacters = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    @Volatile private var serverTimeOffsetMs: Long = 0L

    init {
        root.child(".info/serverTimeOffset").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                serverTimeOffsetMs = snapshot.getValue(Long::class.java) ?: 0L
            }
            override fun onCancelled(error: DatabaseError) = Unit
        })
    }

    fun serverNowMillis(): Long = System.currentTimeMillis() + serverTimeOffsetMs

    fun createRoom(playerName: String, team: String, onResult: (String?, String?) -> Unit) {
        val code = buildString {
            repeat(6) { append(codeCharacters[Random.nextInt(codeCharacters.length)]) }
        }
        val roomRef = database.child(code)
        roomRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                createRoom(playerName, team, onResult)
                return@addOnSuccessListener
            }
            val now = serverNowMillis()
            val player = mapOf("name" to playerName, "team" to team, "joinedAt" to now)
            val room = mapOf(
                "createdAt" to now,
                "hostId" to playerId,
                "players" to mapOf(playerId to player),
                "game" to mapOf("status" to "WAITING", "startedAt" to 0L, "durationSeconds" to 600)
            )
            roomRef.setValue(room)
                .addOnSuccessListener { onResult(code, null) }
                .addOnFailureListener { onResult(null, it.message ?: "Could not create room") }
        }.addOnFailureListener { onResult(null, it.message ?: "Could not reach Firebase") }
    }

    fun joinRoom(codeInput: String, playerName: String, team: String, onResult: (String?, String?) -> Unit) {
        val code = codeInput.trim().uppercase()
        if (!code.matches(Regex("[A-HJ-NP-Z2-9]{6}"))) {
            onResult(null, "Enter a valid 6-character room code")
            return
        }
        val roomRef = database.child(code)
        roomRef.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                onResult(null, "Room not found. Check the code.")
                return@addOnSuccessListener
            }
            val player = mapOf("name" to playerName, "team" to team, "joinedAt" to serverNowMillis())
            roomRef.child("players").child(playerId).setValue(player)
                .addOnSuccessListener { onResult(code, null) }
                .addOnFailureListener { onResult(null, it.message ?: "Could not join room") }
        }.addOnFailureListener { onResult(null, it.message ?: "Could not reach Firebase") }
    }

    fun observePlayers(code: String, onUpdate: (List<RoomPlayer>) -> Unit, onError: (String) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val players = snapshot.children.mapNotNull { child ->
                    val name = child.child("name").getValue(String::class.java) ?: return@mapNotNull null
                    val team = child.child("team").getValue(String::class.java) ?: "RUNNERS"
                    RoomPlayer(child.key ?: "", name, team)
                }
                onUpdate(players)
            }
            override fun onCancelled(error: DatabaseError) { onError(error.message) }
        }
        database.child(code).child("players").addValueEventListener(listener)
        return listener
    }

    fun observeHost(code: String, onUpdate: (Boolean) -> Unit, onError: (String) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onUpdate(snapshot.getValue(String::class.java) == playerId)
            }
            override fun onCancelled(error: DatabaseError) { onError(error.message) }
        }
        database.child(code).child("hostId").addValueEventListener(listener)
        return listener
    }

    fun observeGameState(code: String, onUpdate: (RoomGameState) -> Unit, onError: (String) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onUpdate(
                    RoomGameState(
                        status = snapshot.child("status").getValue(String::class.java) ?: "WAITING",
                        startedAt = snapshot.child("startedAt").getValue(Long::class.java) ?: 0L,
                        durationSeconds = snapshot.child("durationSeconds").getValue(Int::class.java) ?: 600
                    )
                )
            }
            override fun onCancelled(error: DatabaseError) { onError(error.message) }
        }
        database.child(code).child("game").addValueEventListener(listener)
        return listener
    }


    fun startGame(
        code: String,
        durationSeconds: Int = 600,
        onResult: (Boolean, String?) -> Unit
    ) {
        val roomRef = database.child(code)

        roomRef.child("hostId").get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.getValue(String::class.java) != playerId) {
                    onResult(false, "Only the room host can start the game.")
                    return@addOnSuccessListener
                }

                val countdownSeconds = 15
                val startTime = serverNowMillis() + countdownSeconds * 1000L

                val game = mapOf(
                    "status" to "COUNTDOWN",
                    "startedAt" to startTime,
                    "durationSeconds" to durationSeconds
                )

                roomRef.child("game").setValue(game)
                    .addOnSuccessListener {
                        onResult(true, null)
                    }
                    .addOnFailureListener {
                        onResult(false, it.message ?: "Could not start game")
                    }
            }
            .addOnFailureListener {
                onResult(false, it.message ?: "Could not verify room host")
            }
    }

    fun activateGame(code: String) {
        val roomRef = database.child(code)

        roomRef.child("hostId").get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.getValue(String::class.java) != playerId) {
                    return@addOnSuccessListener
                }

                roomRef.child("game").get()
                    .addOnSuccessListener { gameSnapshot ->
                        val status = gameSnapshot.child("status")
                            .getValue(String::class.java) ?: return@addOnSuccessListener

                        val startedAt = gameSnapshot.child("startedAt")
                            .getValue(Long::class.java) ?: return@addOnSuccessListener

                        if (status == "COUNTDOWN" && serverNowMillis() >= startedAt) {
                            roomRef.child("game/status").setValue("RUNNING")
                        }
                    }
            }
    }



    fun resetGame(code: String, onResult: (Boolean, String?) -> Unit) {
        val roomRef = database.child(code)
        roomRef.child("hostId").get().addOnSuccessListener { snapshot ->
            if (snapshot.getValue(String::class.java) != playerId) {
                onResult(false, "Only the room host can reset the game.")
                return@addOnSuccessListener
            }
            val game = mapOf("status" to "WAITING", "startedAt" to 0L, "durationSeconds" to 600)
            roomRef.child("game").setValue(game)
                .addOnSuccessListener { onResult(true, null) }
                .addOnFailureListener { onResult(false, it.message ?: "Could not reset game") }
        }.addOnFailureListener { onResult(false, it.message ?: "Could not verify room host") }
    }

    fun removePlayerListener(code: String, listener: ValueEventListener) {
        database.child(code).child("players").removeEventListener(listener)
    }
    fun removeHostListener(code: String, listener: ValueEventListener) {
        database.child(code).child("hostId").removeEventListener(listener)
    }
    fun removeGameStateListener(code: String, listener: ValueEventListener) {
        database.child(code).child("game").removeEventListener(listener)
    }
}
