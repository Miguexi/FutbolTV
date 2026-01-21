// UpdateUtils.kt
package com.example.futboltv

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

object UpdateUtils {

    fun getCurrentVersionCode(context: Context): Int {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        } catch (e: Exception) {
            e.printStackTrace()
            -1
        }
    }

    suspend fun checkForUpdates(context: Context) {
        try {
            val jsonStr = withContext(Dispatchers.IO) {
                URL("https://raw.githubusercontent.com/Miguexi/FutbolTV/refs/heads/main/version.json").readText()
            }

            val json = JSONObject(jsonStr)
            val versionInfo = VersionInfo(
                latestVersionCode = json.getInt("latestVersionCode"),
                apkUrl = json.getString("apkUrl"),
                changelog = json.getString("changelog")
            )

            val currentCode = getCurrentVersionCode(context)
            if (currentCode < versionInfo.latestVersionCode) {
                downloadAndInstallApk(context, versionInfo.apkUrl)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun downloadAndInstallApk(context: Context, apkUrl: String) {
        val request = DownloadManager.Request(Uri.parse(apkUrl))
        request.setTitle("Actualización disponible")
        request.setDescription("Descargando nueva versión...")
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "update.apk")
        request.setAllowedOverMetered(true)
        request.setAllowedOverRoaming(true)

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = manager.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    val uri = manager.getUriForDownloadedFile(downloadId)
                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    context.startActivity(installIntent)
                    context.unregisterReceiver(this)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    }
}
