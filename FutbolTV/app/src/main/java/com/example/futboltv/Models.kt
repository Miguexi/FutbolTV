package com.example.futboltv

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class Category(
    val name: String,
    val streams: List<Stream>,
    val icon: ImageVector,
    val color: Color
)

data class Stream(
    val name: String,
    val url: String
)

data class StreamResponse(
    val categories: List<Category>
)
data class VersionInfo(
    val latestVersionCode: Int,
    val apkUrl: String,
    val changelog: String
)
