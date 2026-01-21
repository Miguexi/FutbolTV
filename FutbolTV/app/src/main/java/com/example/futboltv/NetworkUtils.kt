package com.example.futboltv

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import android.graphics.Color.parseColor

object NetworkUtils {

    suspend fun fetchStreamsJson(url: String): StreamResponse? = withContext(Dispatchers.IO) {
        try {
            val jsonStr = URL(url).readText()
            val jsonObject = JSONObject(jsonStr)

            val categoriesJson = jsonObject.getJSONArray("categories")
            val categories = mutableListOf<Category>()

            for (i in 0 until categoriesJson.length()) {
                val catJson = categoriesJson.getJSONObject(i)
                val name = catJson.getString("name")
                val color = Color(parseColor(catJson.getString("color")))
                val icon = getIconByName(catJson.getString("icon"))

                val streamsJson = catJson.getJSONArray("streams")
                val streams = mutableListOf<Stream>()
                for (j in 0 until streamsJson.length()) {
                    val streamJson = streamsJson.getJSONObject(j)
                    val streamName = streamJson.getString("name")
                    val streamUrl = streamJson.getString("url")
                    streams.add(Stream(streamName, streamUrl))
                }

                categories.add(Category(name, streams, icon, color))
            }

            StreamResponse(categories)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getIconByName(name: String) = when (name.lowercase()) {
        "sports_soccer" -> Icons.Filled.SportsSoccer
        "tv" -> Icons.Filled.Tv
        "sports_motorsports" -> Icons.Filled.SportsMotorsports
        "sports_basketball" -> Icons.Filled.SportsBasketball
        "emoji_events" -> Icons.Filled.EmojiEvents
        else -> Icons.Filled.Tv
    }
}
