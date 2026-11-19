package com.example.videoflow

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VideoFlowApp() }
    }
}

private enum class SourceType { YOUTUBE, FACEBOOK, DIRECT }
private enum class DownloadKind(val label: String) { VIDEO("ভিডিও"), AUDIO("অডিও / MP3"), SUBTITLE("সাবটাইটেল") }
private data class VideoInfo(val source: SourceType, val title: String, val creator: String = "", val thumbnail: String? = null, val embedHtml: String? = null)
private data class DownloadRow(val id: Long, val title: String, val status: String, val progress: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoFlowApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var darkTheme by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var url by remember { mutableStateOf("") }
    var filename by remember { mutableStateOf("") }
    var permissionConfirmed by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<VideoInfo?>(null) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("অনুমোদিত Direct Media URL পেস্ট করুন।") }
    var kind by remember { mutableStateOf(DownloadKind.VIDEO) }
    var quality by remember { mutableStateOf("Original") }
    var rows by remember { mutableStateOf(emptyList<DownloadRow>()) }

    MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("VideoFlow", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = { darkTheme = !darkTheme }) {
                            Icon(if (darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode, contentDescription = "Theme switch")
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.Download, null) }, label = { Text("ডাউনলোড") })
                    NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.Public, null) }, label = { Text("ব্রাউজার") })
                    NavigationBarItem(selected = tab == 2, onClick = { tab = 2; rows = readDownloads(context) }, icon = { Icon(Icons.Default.History, null) }, label = { Text("ডাউনলোডস") })
                }
            }
        ) { padding ->
            when (tab) {
                0 -> DownloaderScreen(
                    modifier = Modifier.padding(padding), url = url, onUrlChange = { value ->
                        url = value; info = null
                        val host = Uri.parse(value).host?.lowercase().orEmpty()
                        if (host.contains("youtube.com") || host == "youtu.be" || host.contains("facebook.com") || host == "fb.watch") {
                            loading = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { fetchPreview(value.trim()) }
                                info = result; loading = false
                                message = if (result == null) "Preview পাওয়া যায়নি। Public লিংক দিয়ে চেষ্টা করুন।" else "Metadata Preview পাওয়া গেছে।"
                            }
                        }
                    }, filename = filename, onFilenameChange = { filename = it },
                    permissionConfirmed = permissionConfirmed, onPermissionChange = { permissionConfirmed = it },
                    info = info, loading = loading, message = message, kind = kind, onKindChange = { kind = it },
                    quality = quality, onQualityChange = { quality = it },
                    onDownload = {
                        scope.launch {
                            message = withContext(Dispatchers.IO) { enqueueVerifiedDownload(context, url.trim(), filename.trim(), permissionConfirmed, kind) }
                        }
                    }
                )
                1 -> SimpleBrowserScreen(Modifier.padding(padding))
                else -> DownloadsScreen(Modifier.padding(padding), rows, onRefresh = { rows = readDownloads(context) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloaderScreen(
    modifier: Modifier, url: String, onUrlChange: (String) -> Unit,
    filename: String, onFilenameChange: (String) -> Unit,
    permissionConfirmed: Boolean, onPermissionChange: (Boolean) -> Unit,
    info: VideoInfo?, loading: Boolean, message: String,
    kind: DownloadKind, onKindChange: (DownloadKind) -> Unit,
    quality: String, onQualityChange: (String) -> Unit, onDownload: () -> Unit
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ElevatedCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Smart Media Downloader", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(value = url, onValueChange = onUrlChange, modifier = Modifier.fillMaxWidth(), label = { Text("Facebook / YouTube / Direct URL") }, leadingIcon = { Icon(Icons.Default.Link, null) }, singleLine = true)
                OutlinedTextField(value = filename, onValueChange = onFilenameChange, modifier = Modifier.fillMaxWidth(), label = { Text("ফাইলের নাম (ঐচ্ছিক)") }, singleLine = true)
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                info?.let {
                    HorizontalDivider()
                    Text(it.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (it.creator.isNotBlank()) Text(it.creator, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (it.source == SourceType.FACEBOOK) {
                        it.embedHtml?.let { html ->
                            AndroidView(factory = { ctx -> WebView(ctx).apply { settings.javaScriptEnabled = true; settings.domStorageEnabled = true; webViewClient = WebViewClient() } }, update = { it.loadDataWithBaseURL("https://www.facebook.com/", html, "text/html", "UTF-8", null) }, modifier = Modifier.fillMaxWidth().height(220.dp))
                        }
                        Text("Facebook-এর embed preview; এটি ডাউনলোড স্ট্রিম নয়।", style = MaterialTheme.typography.bodySmall)
                    } else if (it.source == SourceType.YOUTUBE) {
                        Text("YouTube metadata পাওয়া গেছে। এটি ভিডিও স্ট্রিম দেয় না।", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text("ডাউনলোডের ধরন", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DownloadKind.values().forEach { option ->
                        FilterChip(selected = kind == option, onClick = { onKindChange(option) }, label = { Text(option.label) })
                    }
                }
                var qualityExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = qualityExpanded, onExpandedChange = { qualityExpanded = !qualityExpanded }) {
                    OutlinedTextField(value = quality, onValueChange = {}, readOnly = true, label = { Text("Quality") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = qualityExpanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = qualityExpanded, onDismissRequest = { qualityExpanded = false }) {
                        DropdownMenuItem(text = { Text("Original (Direct URL-এর আসল Quality)") }, onClick = { onQualityChange("Original"); qualityExpanded = false })
                    }
                }
                Text("360p–1080p অপশন কেবল তখনই আসবে, যখন অনুমোদিত সোর্স একাধিক পৃথক Quality URL সরবরাহ করবে।", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = permissionConfirmed, onCheckedChange = onPermissionChange)
                    Text("এই ফাইলটি ডাউনলোড করার অনুমতি আমার আছে।")
                }
                Button(onClick = onDownload, modifier = Modifier.fillMaxWidth(), enabled = url.isNotBlank() && permissionConfirmed && info?.source != SourceType.YOUTUBE && info?.source != SourceType.FACEBOOK) {
                    Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text("ডাউনলোড শুরু")
                }
                Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Text("MP3-এর জন্য সরাসরি audio/mpeg URL এবং Subtitle-এর জন্য সরাসরি SRT/VTT URL দিন।", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SimpleBrowserScreen(modifier: Modifier) {
    var address by remember { mutableStateOf("https://www.google.com") }
    var pageUrl by remember { mutableStateOf("https://www.google.com") }
    Column(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = address, onValueChange = { address = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("ওয়েব ঠিকানা বা সার্চ") })
            IconButton(onClick = { pageUrl = normalizeAddress(address) }) { Icon(Icons.Default.ArrowForward, "Go") }
        }
        AndroidView(factory = { ctx -> WebView(ctx).apply { settings.javaScriptEnabled = true; settings.domStorageEnabled = true; webViewClient = WebViewClient(); loadUrl(pageUrl) } }, update = { if (it.url != pageUrl) it.loadUrl(pageUrl) }, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun DownloadsScreen(modifier: Modifier, rows: List<DownloadRow>, onRefresh: () -> Unit) {
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ডাউনলোড ইতিহাস", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Refresh") }
        }
        if (rows.isEmpty()) Text("এখনো কোনো ডাউনলোড পাওয়া যায়নি।")
        rows.forEach { row ->
            ElevatedCard {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(row.title, fontWeight = FontWeight.Medium)
                    Text(row.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (row.progress in 0..99) LinearProgressIndicator(progress = { row.progress / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("${row.progress}%", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Text("এই প্রাথমিক 2.0 build-এ DownloadManager-এর History দেখা যায়। Pause/Resume-এর জন্য পরবর্তী ধাপে Range-supporting download engine যুক্ত হবে।", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun normalizeAddress(value: String): String {
    val trimmed = value.trim()
    if (trimmed.startsWith("https://") || trimmed.startsWith("http://")) return trimmed
    if (trimmed.contains('.') && !trimmed.contains(' ')) return "https://$trimmed"
    return "https://www.google.com/search?q=" + Uri.encode(trimmed)
}

private fun fetchPreview(raw: String): VideoInfo? {
    val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
    val host = uri.host?.lowercase() ?: return null
    return try {
        when {
            host == "youtu.be" || host.endsWith("youtube.com") -> {
                val encoded = URLEncoder.encode(raw, "UTF-8")
                val json = getJson("https://www.youtube.com/oembed?url=$encoded&format=json")
                VideoInfo(SourceType.YOUTUBE, json.optString("title", "YouTube Video"), json.optString("author_name"), json.optString("thumbnail_url").takeIf { it.startsWith("https://") })
            }
            host == "fb.watch" || host.endsWith("facebook.com") || host.endsWith("fb.com") -> {
                val encoded = URLEncoder.encode(raw, "UTF-8")
                val json = getJson("https://graph.facebook.com/v27.0/oembed_video?url=$encoded&omitscript=true")
                VideoInfo(SourceType.FACEBOOK, "Facebook Video", json.optString("provider_name", "Facebook"), embedHtml = json.optString("html").takeIf { it.isNotBlank() })
            }
            else -> VideoInfo(SourceType.DIRECT, "Direct media URL")
        }
    } catch (_: Exception) { null }
}

private fun getJson(address: String): JSONObject {
    val connection = (URL(address).openConnection() as HttpURLConnection).apply { connectTimeout = 12000; readTimeout = 12000; setRequestProperty("User-Agent", "VideoFlow/2.0 Android") }
    try {
        if (connection.responseCode !in 200..299) throw IllegalStateException("HTTP ${connection.responseCode}")
        return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
    } finally { connection.disconnect() }
}

private fun enqueueVerifiedDownload(context: Context, rawUrl: String, requestedName: String, permissionConfirmed: Boolean, kind: DownloadKind): String {
    if (!permissionConfirmed) return "ডাউনলোডের অনুমতি নিশ্চিত করুন।"
    val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return "URL সঠিক নয়।"
    if ((uri.scheme != "https" && uri.scheme != "http") || uri.host.isNullOrBlank()) return "শুধু HTTP/HTTPS URL গ্রহণযোগ্য।"
    val host = uri.host!!.lowercase()
    if (host == "youtu.be" || host.endsWith("youtube.com") || host.endsWith("facebook.com") || host == "fb.watch") return "Platform page URL সরাসরি ডাউনলোড করা যাবে না।"
    val check = (URL(rawUrl).openConnection() as HttpURLConnection).apply { requestMethod = "HEAD"; connectTimeout = 10000; readTimeout = 10000; instanceFollowRedirects = true; setRequestProperty("User-Agent", "VideoFlow/2.0 Android") }
    val mime: String
    try {
        val code = check.responseCode
        if (code !in 200..299) return "সার্ভার ফাইল যাচাই করতে দেয়নি (HTTP $code)।"
        mime = check.contentType?.substringBefore(';')?.lowercase() ?: return "Content-Type পাওয়া যায়নি; ডাউনলোড বন্ধ করা হয়েছে।"
    } catch (e: Exception) { return "ফাইল যাচাই করা যায়নি: ${e.message ?: "Network error"}" } finally { check.disconnect() }
    val valid = when (kind) {
        DownloadKind.VIDEO -> mime.startsWith("video/") || mime == "application/octet-stream"
        DownloadKind.AUDIO -> mime.startsWith("audio/") || mime == "application/octet-stream"
        DownloadKind.SUBTITLE -> mime in listOf("text/vtt", "application/x-subrip", "text/plain", "application/octet-stream")
    }
    if (!valid || mime.contains("text/html")) return "এই URL নির্বাচিত ধরন (${kind.label})-এর ফাইল নয় (Content-Type: $mime)।"
    val ext = when {
        kind == DownloadKind.AUDIO && mime == "audio/mpeg" -> ".mp3"
        kind == DownloadKind.SUBTITLE && mime == "text/vtt" -> ".vtt"
        kind == DownloadKind.SUBTITLE && mime == "application/x-subrip" -> ".srt"
        mime == "video/mp4" -> ".mp4"
        mime == "video/webm" -> ".webm"
        mime == "audio/mpeg" -> ".mp3"
        mime == "audio/mp4" -> ".m4a"
        else -> ".bin"
    }
    val base = requestedName.ifBlank { "VideoFlow_${System.currentTimeMillis()}" }.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(100)
    val safeName = if (base.contains('.')) base else base + ext
    return try {
        val request = DownloadManager.Request(uri).setTitle(safeName).setDescription("VideoFlow ${kind.label}").setMimeType(mime).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setAllowedOverMetered(true).setAllowedOverRoaming(false).setDestinationInExternalPublicDir(if (kind == DownloadKind.SUBTITLE) Environment.DIRECTORY_DOWNLOADS else Environment.DIRECTORY_MOVIES, safeName)
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
        "ডাউনলোড কিউতে যোগ হয়েছে: $safeName"
    } catch (e: Exception) { "ডাউনলোড শুরু করা যায়নি: ${e.message ?: "অজানা সমস্যা"}" }
}

private fun readDownloads(context: Context): List<DownloadRow> {
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val cursor = manager.query(DownloadManager.Query()) ?: return emptyList()
    val result = mutableListOf<DownloadRow>()
    cursor.use {
        while (it.moveToNext()) {
            val id = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
            val title = it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)) ?: "Download $id"
            val statusCode = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val progress = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else 0
            val status = when (statusCode) {
                DownloadManager.STATUS_PENDING -> "Pending"
                DownloadManager.STATUS_RUNNING -> "Downloading"
                DownloadManager.STATUS_PAUSED -> "Paused"
                DownloadManager.STATUS_SUCCESSFUL -> "Completed"
                DownloadManager.STATUS_FAILED -> "Failed"
                else -> "Unknown"
            }
            result.add(DownloadRow(id, title, status, if (statusCode == DownloadManager.STATUS_SUCCESSFUL) 100 else progress))
        }
    }
    return result
}
