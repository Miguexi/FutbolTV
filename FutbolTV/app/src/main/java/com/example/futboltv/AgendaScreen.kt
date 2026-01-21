package com.example.futboltv

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList

import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

@Composable
fun AgendaScreen(context: Context, categories: List<Category>) {
    val scope = rememberCoroutineScope()
    var events by remember { mutableStateOf<List<AgendaEvent>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    val listState = rememberLazyListState()
    var focusedIndex by remember { mutableStateOf(0) }

    // Usamos una lista de FocusRequester para manejar el foco de cada tarjeta
    val focusRequesters = remember { SnapshotStateList<FocusRequester>() }

    // Estados por tarjeta (mapa de índice a estado)
    val expandedMap = remember { mutableStateMapOf<Int, Boolean>() }
    val focusedStreamMap = remember { mutableStateMapOf<Int, Int>() }

    // ----------------------------
    // Funciones auxiliares (omito para brevedad)
    // ----------------------------
    val channelAliases: List<Pair<String, List<String>>> = listOf(
        "f1" to listOf("DAZN F1 1080", "DAZN F1 720"),
        "dazn laliga 2" to listOf("DAZN LaLiga 2 1080 MultiAudio", "DAZN LaLiga 2 720 MultiAudio"),
        "dazn laliga" to listOf("DAZN LaLiga 1080 MultiAudio", "DAZN LaLiga 720"),
        "m+ laliga 4" to listOf("m. laliga 4 1080", "m. laliga 4 720"),
        "m+ laliga 3" to listOf("m. laliga 3 1080", "m. laliga 3 720"),
        "m+ laliga 2" to listOf("m. laliga 2 1080", "m. laliga 2 720"),
        "m+ laliga (" to listOf("m. laliga 1080p", "m. laliga 720"),
        "laliga tv hypermotion" to listOf("m. laliga hypermotion", "m. laliga hypermotion 720"),
        "m+ vamos" to listOf("vamos 1080", "vamos 720"),
        "dazn 4" to listOf("dazn 4"),
        "dazn 3" to listOf("dazn 3"),
        "dazn 2" to listOf("dazn 2"),
        "dazn 1" to listOf("dazn 1"),
        "dazn" to listOf("dazn 1", "dazn 2", "dazn 3"),
        "m+ liga de campeones" to listOf("campeones 1080", "campeones 720"),
        "Movistar Plus" to listOf("MovistarPlus 1080")
    )
    val blacklistCompetitions = listOf("ehf")
    val blacklistChannels = listOf("laligaplus", "laliga+")

    fun normalizeName(str: String): String =
        str.lowercase().replace(Regex("[^a-z0-9+ ]"), " ").replace(Regex(" +"), " ").trim()

    fun matchStreams(eventChannels: List<String>): List<Stream> {
        val foundStreams = mutableListOf<Stream>()

        for (eventChannel in eventChannels) {
            val normEvent = normalizeName(eventChannel)
            if (blacklistChannels.any { it in normEvent }) continue

            val matched = channelAliases
                .mapNotNull { (alias, streamNames) ->
                    val normAlias = normalizeName(alias)
                    if (normEvent.contains(normAlias)) {
                        categories.flatMap { it.streams }.filter { s ->
                            val ns = normalizeName(s.name)
                            streamNames.any { ns.contains(normalizeName(it)) }
                        }
                    } else null
                }
                .flatten()
                .distinct()
                .sortedWith(compareByDescending<Stream> { it.name.contains("1080") }) // 1080 antes que 720

            if (matched.isNotEmpty()) foundStreams.addAll(matched)
        }

        return foundStreams
    }


    fun isToday(cal: Calendar): Boolean {
        val t = Calendar.getInstance()
        return t.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
                t.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
    }

    fun parseEventTime(ev: AgendaEvent): Calendar? {
        val raw = ev.time.trim()
        val is12h = raw.contains("AM", true) || raw.contains("PM", true)
        val parser = if (is12h) SimpleDateFormat("hh:mm a", Locale.getDefault())
        else SimpleDateFormat("HH:mm", Locale.getDefault())

        return try {
            val parsed = parser.parse(raw) ?: return null
            val result = ev.date.clone() as Calendar
            val hCal = Calendar.getInstance()
            hCal.time = parsed
            result.set(Calendar.HOUR_OF_DAY, hCal.get(Calendar.HOUR_OF_DAY))
            result.set(Calendar.MINUTE, hCal.get(Calendar.MINUTE))
            result.set(Calendar.SECOND, 0)
            result
        } catch (_: Exception) { null }
    }

    fun isEventInWindow(ev: AgendaEvent): Boolean {
        if (!isToday(ev.date)) return false
        val eCal = parseEventTime(ev) ?: return false
        val now = Calendar.getInstance()
        val past = (now.clone() as Calendar).apply { add(Calendar.HOUR_OF_DAY, -3) }
        val future = (now.clone() as Calendar).apply { add(Calendar.HOUR_OF_DAY, 8) }
        return eCal.timeInMillis in past.timeInMillis..future.timeInMillis
    }


    // ----------------------------
    // Navegación entre tarjetas (LazyColumn)
    // ----------------------------
    val onRemoteKey: (ComposeKeyEvent) -> Boolean = { event ->
        if (event.type != KeyEventType.KeyDown) false
        else when (event.key) {
            Key.DirectionDown -> {
                // Si la tarjeta actual está expandida, DEJAMOS que el cardKeyHandler la maneje.
                if (expandedMap.getOrElse(focusedIndex) { false }) {
                    false // Permitimos que el evento se propague al cardKeyHandler
                } else {
                    val oldIndex = focusedIndex
                    val newIndex = (focusedIndex + 1).coerceAtMost(events.size - 1)
                    if (newIndex != focusedIndex) {
                        expandedMap[oldIndex] = false
                        focusedIndex = newIndex
                        scope.launch {
                            listState.animateScrollToItem(focusedIndex)
                            if (focusedIndex < focusRequesters.size) {
                                focusRequesters[focusedIndex].requestFocus()
                            }
                        }
                        true
                    } else false
                }
            }
            Key.DirectionUp -> {
                // Si la tarjeta actual está expandida, DEJAMOS que el cardKeyHandler la maneje.
                if (expandedMap.getOrElse(focusedIndex) { false }) {
                    false // Permitimos que el evento se propague al cardKeyHandler
                } else {
                    val oldIndex = focusedIndex
                    val newIndex = (focusedIndex - 1).coerceAtLeast(0)
                    if (newIndex != focusedIndex) {
                        expandedMap[oldIndex] = false
                        focusedIndex = newIndex
                        scope.launch {
                            listState.animateScrollToItem(focusedIndex)
                            if (focusedIndex < focusRequesters.size) {
                                focusRequesters[focusedIndex].requestFocus()
                            }
                        }
                        true
                    } else false
                }
            }
            else -> false
        }
    }

    // ----------------------------
    // Cargar eventos y preparar FocusRequesters
    // ----------------------------
    LaunchedEffect(Unit) {
        val fetched: List<AgendaEvent> = try { AgendaRepository.fetchAgenda() } catch(e: Exception) { emptyList() }
        events = fetched.filter { ev ->
            if (!isToday(ev.date)) return@filter false
            if (!isEventInWindow(ev)) return@filter false
            if (blacklistCompetitions.any { normalizeName(ev.competition).contains(it) }) return@filter false
            matchStreams(listOf(ev.channel)).isNotEmpty()
        }.sortedBy { it.time }

        focusRequesters.clear()
        focusRequesters.addAll(List(events.size) { FocusRequester() })

        loading = false
        if (events.isNotEmpty()) {
            focusedIndex = 0
            scope.launch { focusRequesters.firstOrNull()?.requestFocus() }
        }
    }

    // ----------------------------
    // UI
    // ----------------------------
    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Cargando agenda...", color = Color.White)
        }
        return
    }

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()
            .focusable()
            .onPreviewKeyEvent(onRemoteKey) // Maneja el movimiento de tarjetas
    ) {
        itemsIndexed(events) { index, event ->
            val streams = matchStreams(listOf(event.channel))
            if (streams.isEmpty()) return@itemsIndexed

            val isFocused = index == focusedIndex
            val expanded = expandedMap.getOrElse(index) { false }
            val focusedStreamIndex = focusedStreamMap.getOrElse(index) { 0 }

            val cardKeyHandler: (ComposeKeyEvent) -> Boolean = { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) {
                    false
                } else if (!isFocused) {
                    false
                } else {
                    when (keyEvent.key) {
                        Key.DirectionCenter, Key.Enter -> {
                            if (!expanded) {
                                expandedMap.keys.forEach { k -> expandedMap[k] = false }
                                expandedMap[index] = true
                                focusedStreamMap[index] = 0
                                // **CORRECCIÓN 2: Forzar el scroll para ver los streams**
                                scope.launch {
                                    // Scroll al ítem. El scroll ahora incluirá la nueva altura.
                                    listState.animateScrollToItem(index)
                                }
                            } else {
                                // Reproduce stream enfocado
                                streams.getOrNull(focusedStreamMap[index] ?: 0)?.let { s ->
                                    try {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(s.url)).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                        )
                                    } catch (_: Exception) {}
                                }
                            }
                            true // Consume el evento
                        }

                        Key.DirectionDown -> {
                            // **CORRECCIÓN 1: Maneja la navegación interna SOLO aquí**
                            if (expanded) {
                                val current = focusedStreamMap[index] ?: 0
                                if (current < streams.size - 1) {
                                    focusedStreamMap[index] = current + 1
                                }
                                true // Consume siempre si está expandido
                            } else false
                        }

                        Key.DirectionUp -> {
                            // **CORRECCIÓN 1: Maneja la navegación interna SOLO aquí**
                            if (expanded) {
                                val current = focusedStreamMap[index] ?: 0
                                if (current > 0) {
                                    focusedStreamMap[index] = current - 1
                                }
                                true // Consume siempre si está expandido
                            } else false
                        }

                        Key.Back -> {
                            if (expanded) {
                                expandedMap[index] = false
                                true // Consume, cierra la expansión
                            } else false
                        }
                        else -> false
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequesters[index])
                    .background(if (isFocused) Color(0xFF455A64) else Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
                    .focusable()
                    .onPreviewKeyEvent(cardKeyHandler), // Maneja el Enter y la navegación interna
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(event.time, color = Color.Yellow, fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        Image(
                            painter = rememberAsyncImagePainter(event.localLogo),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            contentScale = ContentScale.Crop
                        )
                        val parts = event.match.split(" vs ", ignoreCase = true)
                        Text(parts.getOrNull(0) ?: "", color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("vs", color = Color.Gray)
                        Spacer(Modifier.width(8.dp))
                        Image(
                            painter = rememberAsyncImagePainter(event.visitanteLogo),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            contentScale = ContentScale.Crop
                        )
                        Text(parts.getOrNull(1) ?: "", color = Color.White)
                    }
                    Text(event.competition, color = Color.LightGray)

                    if (expandedMap[index] == true) {
                        Spacer(Modifier.height(8.dp))
                        streams.forEachIndexed { sIndex, s ->
                            val isStreamFocused = isFocused && sIndex == (focusedStreamMap[index] ?: 0)
                            Button(
                                onClick = {
                                    try {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(s.url)).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                        )
                                    } catch (_: Exception) {}
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isStreamFocused) Color(0xFFE57373) else Color(0xFF37474F)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(s.name, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}