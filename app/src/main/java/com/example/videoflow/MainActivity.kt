package com.example.videoflow

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.URLUtil
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.net.URI

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VideoFlowApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoFlowApp() {
    val context = LocalContext.current
    var url by remember { mutableStateOf("") }
    var filename by remember { mutableStateOf("") }
    var permissionConfirmed by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("শুধু সরাসরি ডাউনলোডযোগ্য ও অনুমোদিত ভিডিও লিংক দিন।") }

    MaterialTheme(
        colorScheme = if (android.os.Build.VERSION.SDK_INT >= 31)
            dynamicColorScheme(context) else lightColorScheme()
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("VideoFlow", fontWeight = FontWeight.Bold) }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding)
                    .verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ElevatedCard {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("ভিডিও ডাউনলোড", style = MaterialTheme.typography.headlineSmall)
                        Text("সরাসরি MP4, MKV বা অন্যান্য মিডিয়া ফাইল URL ব্যবহার করুন।")
                        OutlinedTextField(
                            value = url, onValueChange = { url = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("ভিডিও URL") },
                            leadingIcon = { Icon(Icons.Default.Link, null) },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = filename, onValueChange = { filename = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("ফাইলের নাম (ঐচ্ছিক)") },
                            singleLine = true
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = permissionConfirmed, onCheckedChange = { permissionConfirmed = it })
                            Text("ভিডিওটি ডাউনলোড করার অনুমতি আমার আছে।")
                        }
                        Button(
                            onClick = {
                                message = startDownload(context, url.trim(), filename.trim(), permissionConfirmed)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = url.isNotBlank()
                        ) {
                            Icon(Icons.Default.Download, null)
                            Spacer(Modifier.width(8.dp))
                            Text("ডাউনলোড শুরু")
                        }
                    }
                }
                ElevatedCard {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("স্ট্যাটাস", style = MaterialTheme.typography.titleMedium)
                        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    "নোট: এই প্রাথমিক সংস্করণ সরাসরি HTTP/HTTPS ফাইল URL ডাউনলোড করে। " +
                    "YouTube/Facebook/Instagram/TikTok-এর পেজ URL বা DRM-সুরক্ষিত কনটেন্ট extract বা bypass করে না।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun dynamicColorScheme(context: Context): ColorScheme =
    if (android.os.Build.VERSION.SDK_INT >= 31)
        dynamicLightColorScheme(context) else lightColorScheme()

private fun startDownload(
    context: Context,
    rawUrl: String,
    requestedName: String,
    permissionConfirmed: Boolean
): String {
    if (!permissionConfirmed) return "ডাউনলোডের অনুমতি নিশ্চিত করুন।"
    val uri = try { Uri.parse(rawUrl) } catch (_: Exception) { return "URL সঠিক নয়।" }
    if (uri.scheme != "https" && uri.scheme != "http") return "শুধু HTTP/HTTPS URL গ্রহণযোগ্য।"
    if (uri.host.isNullOrBlank()) return "URL-এ বৈধ host পাওয়া যায়নি।"

    val guessed = URLUtil.guessFileName(rawUrl, null, null)
    val safeName = (requestedName.ifBlank { guessed })
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .take(120)
        .ifBlank { "video_download" }

    return try {
        val request = DownloadManager.Request(uri)
            .setTitle(safeName)
            .setDescription("VideoFlow download")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, safeName)
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
        "ডাউনলোড কিউতে যোগ হয়েছে: $safeName"
    } catch (e: Exception) {
        "ডাউনলোড শুরু করা যায়নি: ${e.message ?: "অজানা সমস্যা"}"
    }
}
