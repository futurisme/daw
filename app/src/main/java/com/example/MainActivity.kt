package com.example

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sin

object AudioEngine {
    private const val SAMPLE_RATE = 44100
    var globalVolume by mutableFloatStateOf(1.0f)

    fun playTone(midiNote: Int, durationMs: Double, velocity: Int = 100) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val frequency = 440.0 * Math.pow(2.0, (midiNote - 69) / 12.0)
                val numSamples = (durationMs * SAMPLE_RATE / 1000).toInt()
                if (numSamples <= 0) return@launch
                
                val sampleArray = ShortArray(numSamples)
                val amplitude = (32767 * globalVolume * (velocity / 127f)).toInt()
                
                val attackSamples = (0.01 * SAMPLE_RATE).toInt().coerceAtMost(numSamples / 4)
                val releaseSamples = (0.05 * SAMPLE_RATE).toInt().coerceAtMost(numSamples / 4)

                for (i in 0 until numSamples) {
                    var env = 1.0
                    if (i < attackSamples) {
                        env = i.toDouble() / attackSamples
                    } else if (i > numSamples - releaseSamples) {
                        env = (numSamples - i).toDouble() / releaseSamples
                    }
                    val sine = sin(2 * Math.PI * i / (SAMPLE_RATE / frequency))
                    sampleArray[i] = (sine * amplitude * env).toInt().toShort()
                }

                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(sampleArray.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(sampleArray, 0, sampleArray.size)
                audioTrack.play()

                Thread.sleep(durationMs.toLong() + 100)
                audioTrack.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScreen()
            }
        }
    }
}

val PIANO_KEYS = listOf(
    Pair("C6", 84) to true, Pair("B5", 83) to true, Pair("A#5", 82) to false, Pair("A5", 81) to true, Pair("G#5", 80) to false, Pair("G5", 79) to true, Pair("F#5", 78) to false, Pair("F5", 77) to true, Pair("E5", 76) to true, Pair("D#5", 75) to false, Pair("D5", 74) to true, Pair("C#5", 73) to false, Pair("C5", 72) to true,
    Pair("B4", 71) to true, Pair("A#4", 70) to false, Pair("A4", 69) to true, Pair("G#4", 68) to false, Pair("G4", 67) to true, Pair("F#4", 66) to false, Pair("F4", 65) to true, Pair("E4", 64) to true, Pair("D#4", 63) to false, Pair("D4", 62) to true, Pair("C#4", 61) to false, Pair("C4", 60) to true,
    Pair("B3", 59) to true, Pair("A#3", 58) to false, Pair("A3", 57) to true, Pair("G#3", 56) to false, Pair("G3", 55) to true, Pair("F#3", 54) to false, Pair("F3", 53) to true, Pair("E3", 52) to true, Pair("D#3", 51) to false, Pair("D3", 50) to true, Pair("C#3", 49) to false, Pair("C3", 48) to true
)

// 1 pixel = 4.6875 ms (100 pixels = 1 beat @ 128 BPM)
const val MS_PER_PIXEL = 4.6875

class NoteState(
    val id: Int,
    initialX: Float,
    initialY: Float,
    initialWidth: Float,
    val midiNote: Int,
    val velocity: Int = 100
) {
    var rawX by mutableFloatStateOf(initialX)
    var rawY by mutableFloatStateOf(initialY)
    var width by mutableFloatStateOf(initialWidth)
}

@Composable
fun MainAppScreen() {
    val navController = rememberNavController()
    val notes = remember { mutableStateListOf<NoteState>() }
    
    Scaffold(
        bottomBar = { AppBottomNavigation(navController) },
        containerColor = Background1A1C1E
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "keys",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("keys") {
                PianoRollWorkspace(notes = notes)
            }
            composable("mixer") {
                MixerWorkspace()
            }
            composable("tracks") {
                TracksWorkspace()
            }
        }
    }
}

@Composable
fun AppBottomNavigation(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(
        containerColor = Background111318,
        contentColor = PrimaryD0BCFF,
        tonalElevation = 0.dp
    ) {
        NavigationBarItem(
            icon = { Icon(Icons.Filled.Piano, contentDescription = null) },
            label = { Text("Keys") },
            selected = currentRoute == "keys",
            onClick = { navController.navigate("keys") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = PrimaryDark381E72,
                selectedTextColor = PrimaryD0BCFF,
                indicatorColor = PrimaryD0BCFF,
                unselectedIconColor = TextE2E2E6,
                unselectedTextColor = TextE2E2E6
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Filled.GridView, contentDescription = null) },
            label = { Text("Tracks") },
            selected = currentRoute == "tracks",
            onClick = { navController.navigate("tracks") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = PrimaryDark381E72,
                selectedTextColor = PrimaryD0BCFF,
                indicatorColor = PrimaryD0BCFF,
                unselectedIconColor = TextE2E2E6,
                unselectedTextColor = TextE2E2E6
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Filled.GraphicEq, contentDescription = null) },
            label = { Text("Mixer") },
            selected = currentRoute == "mixer",
            onClick = { navController.navigate("mixer") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = PrimaryDark381E72,
                selectedTextColor = PrimaryD0BCFF,
                indicatorColor = PrimaryD0BCFF,
                unselectedIconColor = TextE2E2E6,
                unselectedTextColor = TextE2E2E6
            )
        )
    }
}

@Composable
fun PianoRollWorkspace(notes: MutableList<NoteState>) {
    var snapWidth by remember { mutableFloatStateOf(25f) } // 1/16th note default (100 / 4)
    val horizontalScrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Toolbar minimized
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Background111318)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { /* play notes */ }) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = GreenPulse4ADE80)
                }
                Text("128 BPM", color = TextE2E2E6, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Snap: ", color = TextC4C6D0, fontSize = 12.sp)
                listOf(100f to "1/4", 50f to "1/8", 25f to "1/16").forEach { (width, label) ->
                    Text(
                        text = label,
                        color = if (snapWidth == width) PrimaryD0BCFF else TextE2E2E6,
                        fontSize = 12.sp,
                        fontWeight = if (snapWidth == width) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clickable { snapWidth = width }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Background0F1115)
            ) {
                // Keys sidebar
                Column(
                    modifier = Modifier
                        .width(60.dp)
                        .verticalScroll(verticalScrollState)
                        .background(Background1A1C1E)
                        .border(1.dp, Border44474E)
                ) {
                    PIANO_KEYS.forEachIndexed { index, (keyInfo, isWhite) ->
                        val midiNote = keyInfo.second
                        PianoKeyCompact(
                            noteName = keyInfo.first,
                            isWhite = isWhite,
                            onKeyPressed = {
                                AudioEngine.playTone(midiNote, 200.0)
                            }
                        )
                    }
                }

                // Grid area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        // Apply scrolling
                        .verticalScroll(verticalScrollState)
                        .horizontalScroll(horizontalScrollState)
                ) {
                    val gridWidth = 3000.dp
                    val gridHeight = (PIANO_KEYS.size * 32).dp
                    
                    // Tap to add note
                    Box(modifier = Modifier
                        .size(width = gridWidth, height = gridHeight)
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val x = offset.x
                                val y = offset.y
                                val yIndex = (y / 32.dp.toPx()).toInt().coerceIn(0, PIANO_KEYS.size - 1)
                                val midiNote = PIANO_KEYS[yIndex].first.second
                                val snappedX = (x / snapWidth.dp.toPx()).roundToInt() * snapWidth.dp.toPx()
                                
                                notes.add(
                                    NoteState(
                                        id = notes.size,
                                        initialX = snappedX,
                                        initialY = yIndex * 32f,
                                        initialWidth = snapWidth, // Default 1 unit width
                                        midiNote = midiNote
                                    )
                                )
                                AudioEngine.playTone(midiNote, snapWidth.toDouble() * MS_PER_PIXEL)
                            }
                        }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Draw horizontal lines
                            for (i in 0..PIANO_KEYS.size) {
                                val y = i * 32.dp.toPx()
                                drawLine(
                                    color = Color(0xFF222222),
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 1f
                                )
                            }
                            // Draw vertical lines
                            val numCols = (3000f / snapWidth).toInt()
                            for (i in 0..numCols) {
                                val x = i * snapWidth.dp.toPx()
                                val isBeat = i % (100f / snapWidth).toInt() == 0
                                drawLine(
                                    color = if (isBeat) Color(0xFF333333) else Color(0xFF1A1A1A),
                                    start = Offset(x, 0f),
                                    end = Offset(x, size.height),
                                    strokeWidth = if (isBeat) 2f else 1f
                                )
                            }
                        }

                        // Notes
                        notes.forEach { note ->
                            PlacedNoteCompact(
                                note = note,
                                snapWidth = snapWidth,
                                gridHeight = PIANO_KEYS.size
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PianoKeyCompact(noteName: String, isWhite: Boolean, onKeyPressed: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val bgColor = if (isPressed) PrimaryD0BCFF.copy(alpha = 0.5f) else if (isWhite) Color.White else Color.Black
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(bgColor)
            .border(0.5.dp, if (isWhite) Border44474E else Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onKeyPressed()
                        tryAwaitRelease()
                        isPressed = false
                    }
                )
            }
            .padding(4.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        if (isWhite) {
            Text(
                text = noteName,
                color = if (isPressed) Color.White else Color.Black,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PlacedNoteCompact(
    note: NoteState,
    snapWidth: Float,
    gridHeight: Int
) {
    val density = LocalDensity.current
    
    // Snapped positions for UI render
    val xOffsetPx = (note.rawX / snapWidth).roundToInt() * snapWidth
    val yOffsetPx = (note.rawY / 32f).roundToInt() * 32f

    // Convert pixel to dp for layout
    val xDp = with(density) { xOffsetPx.toDp() }
    val yDp = with(density) { yOffsetPx.toDp() }
    val widthDp = with(density) { note.width.toDp() }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(xDp.roundToPx(), yDp.roundToPx())
            }
            .width(widthDp)
            .height(32.dp)
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(NoteGreen93F5D1)
    ) {
        // Drag Handle for moving the whole note
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(snapWidth) {
                    detectDragGestures(
                        onDragEnd = {
                            // Play sound on drop
                            AudioEngine.playTone(note.midiNote, note.width.toDouble() * MS_PER_PIXEL)
                        }
                    ) { change, dragAmount ->
                        change.consume() // IMPORTANT: Prevents background scroll from intercepting
                        val dragX = dragAmount.x
                        val dragY = dragAmount.y
                        note.rawX = (note.rawX + dragX).coerceIn(0f, 3000.dp.toPx() - note.width)
                        note.rawY = (note.rawY + dragY).coerceIn(0f, (gridHeight - 1) * 32f)
                    }
                }
        )
        
        // Right Handle for resizing
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(20.dp)
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = 0.2f))
                .pointerInput(snapWidth) {
                    detectDragGestures(
                        onDragEnd = {
                            // Snap width to grid on drag end
                            val snappedW = (note.width / snapWidth).roundToInt() * snapWidth
                            note.width = snappedW.coerceAtLeast(snapWidth)
                            AudioEngine.playTone(note.midiNote, note.width.toDouble() * MS_PER_PIXEL)
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        val dragX = dragAmount.x
                        note.width = (note.width + dragX).coerceAtLeast(snapWidth)
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(4.dp)
                    .height(16.dp)
                    .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
            )
        }
    }
}

@Composable
fun MixerWorkspace() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background1A1C1E)
            .padding(16.dp)
    ) {
        Text("Master Volume", color = PrimaryD0BCFF, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.VolumeMute, contentDescription = null, tint = TextC4C6D0)
            Slider(
                value = AudioEngine.globalVolume,
                onValueChange = { AudioEngine.globalVolume = it },
                valueRange = 0f..1f,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                colors = SliderDefaults.colors(
                    thumbColor = PrimaryD0BCFF,
                    activeTrackColor = PrimaryD0BCFF
                )
            )
            Icon(Icons.Filled.VolumeUp, contentDescription = null, tint = TextC4C6D0)
        }
        Text(
            text = "Volume: ${(AudioEngine.globalVolume * 100).toInt()}%",
            color = TextE2E2E6,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
fun TracksWorkspace() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background1A1C1E),
        contentAlignment = Alignment.Center
    ) {
        Text("Tracks view (WIP)", color = TextC4C6D0)
    }
}
