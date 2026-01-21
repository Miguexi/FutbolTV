@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.futboltv

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URL

class MainActivity : ComponentActivity() {

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DarkThemeApp {
                var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
                var isLoading by remember { mutableStateOf(true) }

                LaunchedEffect(Unit) {
                    val data = NetworkUtils.fetchStreamsJson(
                        "https://raw.githubusercontent.com/Miguexi/FutbolTV/main/streams.json"
                    )
                    categories = data?.categories
                        ?.map { cat -> cat.copy(streams = cat.streams.filter { it.url.isNotBlank() }) }
                        ?: emptyList()
                    isLoading = false
                }

                var screen by remember { mutableStateOf("menu") }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    when (screen) {
                        "menu" -> MenuScreen(
                            onCanalesClick = { screen = "canales" },
                            onAgendaClick = { screen = "agenda" }
                        )
                        "canales" -> MainScreen(categories)
                        "agenda" -> AgendaScreen(context = this, categories = categories) // Asumiendo que AgendaScreen está definida
                    }
                }
            }
        }
    }

    // --------------------- Menu ---------------------

    // COMPONENTE AUXILIAR PARA LOS BOTONES DEL MENÚ (Implementa el foco visual)
    @Composable
    fun MenuButton(text: String, onClick: () -> Unit, baseColor: Color) {
        val interactionSource = remember { MutableInteractionSource() }
        val isFocused by interactionSource.collectIsFocusedAsState()

        // 1. Color: Cambia a un color más brillante o más oscuro al enfocarse
        val focusedColor by animateColorAsState(
            targetValue = if (isFocused) baseColor.copy(alpha = 1.0f) else baseColor.copy(alpha = 0.8f),
            animationSpec = tween(durationMillis = 150)
        )

        // 2. Sombra/Elevación: Aumenta la profundidad para resaltar
        val elevation by animateFloatAsState(
            targetValue = if (isFocused) 12.dp.value else 4.dp.value,
            animationSpec = tween(durationMillis = 150)
        )

        Button(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(70.dp)
                .shadow(elevation.dp, RoundedCornerShape(12.dp))
                .padding(vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = focusedColor
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(text, fontSize = 24.sp, color = Color.White)
        }
    }

    @Composable
    fun MenuScreen(
        onCanalesClick: () -> Unit,
        onAgendaClick: () -> Unit
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("⚽ FútbolTV - Menú ⚽", fontSize = 24.sp, color = Color.White) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    modifier = Modifier.background(
                        Brush.horizontalGradient(listOf(Color(0xFF64B5F6), Color(0xFF81C784)))
                    )
                )
            },
            containerColor = Color(0xFF1E1E1E)
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 40.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Usa el componente auxiliar MenuButton
                MenuButton(
                    text = "Canales",
                    onClick = onCanalesClick,
                    baseColor = Color(0xFF3794dd) // Azul
                )

                Spacer(modifier = Modifier.height(20.dp))

                MenuButton(
                    text = "Agenda",
                    onClick = onAgendaClick,
                    baseColor = Color(0xFF5ea363) // Verde
                )
            }
        }
    }

    // --------------------- Canales ---------------------
    @Composable
    fun MainScreen(categories: List<Category>) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("⚽ FútbolTV ⚽", fontSize = 24.sp, color = Color.White) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    modifier = Modifier.background(
                        Brush.horizontalGradient(listOf(Color(0xFF64B5F6), Color(0xFF81C784)))
                    )
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(categories) { category ->
                    CategoryDropdown(category)
                }
            }
        }
    }

    @Composable
    fun CategoryDropdown(category: Category) {
        var expanded by remember { mutableStateOf(false) }
        val interactionSource = remember { MutableInteractionSource() }
        val isFocused by interactionSource.collectIsFocusedAsState() // Detectar foco

        val rotation by animateFloatAsState(
            targetValue = if (expanded) 180f else 0f,
            animationSpec = tween(durationMillis = 400)
        )

        // 1. Color: Cambia el color de la Card al recibir el foco
        val focusedContainerColor by animateColorAsState(
            targetValue = if (isFocused) category.color.copy(alpha = 0.85f) else category.color,
            animationSpec = tween(durationMillis = 150)
        )
        // 2. Sombra: Aumenta la sombra al recibir el foco
        val elevation by animateFloatAsState(
            targetValue = if (isFocused) 10.dp.value else 4.dp.value,
            animationSpec = tween(durationMillis = 150)
        )


        Card(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(tween(300))
                // Aplicamos sombra animada y la capturamos con clickable/interactionSource
                .shadow(elevation.dp, RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { expanded = !expanded }
                ),
            shape = RoundedCornerShape(12.dp),
            // Usamos el color animado
            colors = CardDefaults.cardColors(containerColor = focusedContainerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = category.name, color = Color.White, fontSize = 20.sp)
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.graphicsLayer { rotationZ = rotation },
                        tint = Color.White
                    )
                }

                if (expanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, start = 16.dp, end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        category.streams.forEach { stream ->
                            StreamButton(stream, category.color)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun StreamButton(stream: Stream, categoryColor: Color) {
        val interactionSource = remember { MutableInteractionSource() }
        val isFocused by interactionSource.collectIsFocusedAsState()

        // Color: Usamos un color base fijo con alpha para el contraste
        val buttonColor by animateColorAsState(
            targetValue = if (isFocused) categoryColor.copy(alpha = 0.9f) else categoryColor.copy(alpha = 0.4f),
            animationSpec = tween(300)
        )

        // Sombra/Elevación
        val elevation by animateFloatAsState(
            targetValue = if (isFocused) 6.dp.value else 2.dp.value,
            animationSpec = tween(durationMillis = 150)
        )

        Button(
            onClick = { openAceStream(stream.url) },
            interactionSource = interactionSource,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .shadow(elevation.dp, RoundedCornerShape(8.dp)),
            colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
        ) {
            Text(text = stream.name, color = if (isFocused) Color.White else Color.White.copy(alpha = 0.8f))
        }
    }


    // --------------------- Utils ---------------------
    private fun matchStream(channelName: String, streams: List<Stream>): Stream? {
        return streams.firstOrNull { it.name.contains(channelName, ignoreCase = true) }
    }

    private fun openAceStream(url: String) {
        if (url.isEmpty()) {
            Toast.makeText(this, "Stream no disponible", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "No se pudo abrir el stream", Toast.LENGTH_SHORT).show()
        }
    }

    // --------------------- Tema ---------------------
    @Composable
    fun DarkThemeApp(content: @Composable () -> Unit) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Color(0xFF64B5F6),
                secondary = Color(0xFF81C784),
                background = Color(0xFF121212),
                surface = Color(0xFF1E1E1E),
                onPrimary = Color.White,
                onSecondary = Color.White,
                onBackground = Color.White,
                onSurface = Color.White
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFF1E1E1E), Color(0xFF121212)))
                    )
            ) {
                content()
            }
        }
    }
}