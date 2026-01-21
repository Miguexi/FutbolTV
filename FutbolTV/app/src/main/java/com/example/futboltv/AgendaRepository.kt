package com.example.futboltv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.*

object AgendaRepository {

    suspend fun fetchAgenda(): List<AgendaEvent> = withContext(Dispatchers.IO) {
        val url = "https://www.futbolenlatv.es/deporte"
        val doc = Jsoup.connect(url)
            .timeout(10_000)
            .get()

        val rows = doc.select("table.tablaPrincipal tbody tr")

        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        var currentDate: Calendar = Calendar.getInstance()   // Valor por defecto

        val events = mutableListOf<AgendaEvent>()

        for (tr in rows) {

            // -------------------------------
            // ¿Es cabecera de fecha?
            // -------------------------------
            if (tr.hasClass("cabeceraTabla")) {
                val text = tr.text().trim()  // Ej: "Mañana martes, 02/12/2025"
                val dateRegex = Regex("(\\d{2}/\\d{2}/\\d{4})")
                val match = dateRegex.find(text)

                if (match != null) {
                    val dateString = match.value
                    val parsed = dateFormat.parse(dateString)
                    if (parsed != null) {
                        currentDate = Calendar.getInstance().apply {
                            time = parsed
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                    }
                }
                continue
            }

            // ------------------------------------------
            // Si no tiene hora, no es evento
            // ------------------------------------------
            if (tr.select("td.hora").isEmpty()) continue

            try {
                val time = tr.selectFirst("td.hora")?.text()?.trim() ?: ""
                val competition = tr.selectFirst("td.detalles label")?.text()?.trim() ?: ""
                val local = tr.selectFirst("td.local span")?.text()?.trim() ?: ""
                val visitante = tr.selectFirst("td.visitante span")?.text()?.trim() ?: ""
                val localLogo = tr.selectFirst("td.local img")?.attr("src") ?: ""
                val visitanteLogo = tr.selectFirst("td.visitante img")?.attr("src") ?: ""
                val channels = tr.select("td.canales ul.listaCanales li")
                    .joinToString(", ") { it.text().trim() }

                // Clonamos la fecha real capturada
                val eventDate = currentDate.clone() as Calendar

                events.add(
                    AgendaEvent(
                        time = time,
                        match = "$local vs $visitante",
                        competition = competition,
                        channel = channels,
                        localLogo = localLogo,
                        visitanteLogo = visitanteLogo,
                        date = eventDate
                    )
                )

            } catch (_: Exception) {}
        }

        events
    }
}
