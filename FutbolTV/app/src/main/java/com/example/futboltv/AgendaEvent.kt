package com.example.futboltv
import java.util.Calendar
data class AgendaEvent(
    val time: String,
    val match: String,
    val competition: String,
    val channel: String,
    val localLogo: String,
    val visitanteLogo: String,
    val date: Calendar   // AÑADIDO
)


