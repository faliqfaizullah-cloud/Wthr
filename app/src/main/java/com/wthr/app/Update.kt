package com.wthr.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app updates from GitHub Releases. Every build is signed with the same key and has a higher versionCode,
 * so Android installs it as an UPDATE over the existing app (no uninstall, data kept).
 * The release notes contain "versionCode=N", which is compared with the installed versionCode.
 */
object Updater {
    const val OWNER = "faliqfaizullah-cloud"     // your GitHub user
    const val REPO = "wthr"                      // your repository (must be public)

    sealed class S {
        object Idle : S()
        object Checking : S()
        object UpToDate : S()
        class Available(val name: String, val code: Long, val url: String) : S()
        class Downloading(val pct: Int) : S()
        class Failed(val msg: String) : S()
    }

    var state: S by mutableStateOf<S>(S.Idle)
        private set
    var hint: String by mutableStateOf("")
        private set
    private var last: S.Available? = null

    fun installedCode(c: Context): Long {
        val i = c.packageManager.getPackageInfo(c.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) i.longVersionCode else @Suppress("DEPRECATION") i.versionCode.toLong()
    }
    fun installedName(c: Context): String = c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: ""
    fun dismiss() { state = S.Idle; hint = "" }

    suspend fun check(c: Context, manual: Boolean) {
        if (state is S.Downloading) return
        if (manual) state = S.Checking
        val res: Any? = withContext(Dispatchers.IO) { try { fetchLatest() } catch (e: Exception) { "error" } }
        state = when {
            res is S.Available && res.code > installedCode(c) -> { last = res; res }
            res is S.Available -> if (manual) S.UpToDate else S.Idle
            else -> if (manual) S.Failed("Couldn't check for updates") else S.Idle
        }
    }

    private fun fetchLatest(): S.Available? {
        val con = URL("https://api.github.com/repos/$OWNER/$REPO/releases/latest").openConnection() as HttpURLConnection
        con.connectTimeout = 10000; con.readTimeout = 10000
        con.setRequestProperty("Accept", "application/vnd.github+json")
        val j = try { JSONObject(con.inputStream.bufferedReader().readText()) } finally { con.disconnect() }
        val assets = j.getJSONArray("assets")
        var url: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.getString("name").endsWith(".apk")) { url = a.getString("browser_download_url"); break }
        }
        if (url == null) return null
        val code = Regex("versionCode=(\\d+)").find(j.optString("body", ""))?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        return S.Available(j.optString("name", j.optString("tag_name", "")), code, url)
    }

    /** Download then hand the APK to the system installer (it installs over the current app). */
    suspend fun start(c: Context, a: S.Available) {
        if (Build.VERSION.SDK_INT >= 26 && !c.packageManager.canRequestPackageInstalls()) {
            hint = "First allow \"Install unknown apps\" for Wthr (settings are opening), then come back and tap Update now."
            c.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${c.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        hint = ""
        val f = download(c, a) ?: return
        val uri = FileProvider.getUriForFile(c, "${c.packageName}.fileprovider", f)
        c.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        state = S.Idle
    }

    suspend fun retry(c: Context) { last?.let { start(c, it) } }

    private suspend fun download(c: Context, a: S.Available): File? = withContext(Dispatchers.IO) {
        try {
            val dir = File(c.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val f = File(dir, "Wthr-${a.code}.apk")
            val con = URL(a.url).openConnection() as HttpURLConnection
            con.connectTimeout = 15000; con.readTimeout = 30000
            val total = con.contentLengthLong
            state = S.Downloading(0)
            con.inputStream.use { ins ->
                f.outputStream().use { out ->
                    val buf = ByteArray(16384); var done = 0L; var n: Int
                    while (ins.read(buf).also { n = it } > 0) {
                        out.write(buf, 0, n); done += n
                        if (total > 0) state = S.Downloading((done * 100 / total).toInt())
                    }
                }
            }
            con.disconnect()
            f
        } catch (e: Exception) { state = S.Failed("Download failed"); null }
    }
}

/** Glass banner at the top: update available / downloading / failed. */
@Composable
fun UpdateBanner() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val st = Updater.state
    if (st is Updater.S.Available || st is Updater.S.Downloading || (st is Updater.S.Failed && st.msg == "Download failed")) {
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp).glass(RoundedCornerShape(22.dp), Color.White, 0.82f).padding(14.dp)) {
            when (st) {
                is Updater.S.Available -> {
                    Txt("Update available", 14, Maroon, FontWeight.ExtraBold, ls = 0f)
                    Txt("Wthr ${st.name} is ready. It installs over this app, so nothing is lost and you don't need to uninstall.", 11, Maroon, FontWeight.Medium, Modifier.padding(top = 4.dp), 0f, 1.25f)
                    if (Updater.hint.isNotEmpty()) Txt(Updater.hint, 11, Maroon, FontWeight.ExtraBold, Modifier.padding(top = 6.dp), 0f, 1.25f)
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.glass(RoundedCornerShape(50), Maroon, 0.85f).clickable { Haptics.click(); scope.launch { Updater.start(ctx, st) } }.padding(horizontal = 18.dp, vertical = 8.dp)) { Txt("Update now", 13, Lilac, FontWeight.ExtraBold, ls = 0f) }
                        Box(Modifier.glass(RoundedCornerShape(50), Color.White, 0.5f).clickable { Updater.dismiss() }.padding(horizontal = 18.dp, vertical = 8.dp)) { Txt("Later", 13, Maroon, FontWeight.Bold, ls = 0f) }
                    }
                }
                is Updater.S.Downloading -> {
                    Txt("Downloading update… ${st.pct}%", 14, Maroon, FontWeight.ExtraBold, ls = 0f)
                    Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Maroon.copy(alpha = 0.15f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(st.pct / 100f).background(Maroon))
                    }
                }
                else -> {
                    Txt("Update failed", 14, Maroon, FontWeight.ExtraBold, ls = 0f)
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.glass(RoundedCornerShape(50), Maroon, 0.85f).clickable { scope.launch { Updater.retry(ctx) } }.padding(horizontal = 18.dp, vertical = 8.dp)) { Txt("Try again", 13, Lilac, FontWeight.ExtraBold, ls = 0f) }
                        Box(Modifier.glass(RoundedCornerShape(50), Color.White, 0.5f).clickable { Updater.dismiss() }.padding(horizontal = 18.dp, vertical = 8.dp)) { Txt("Close", 13, Maroon, FontWeight.Bold, ls = 0f) }
                    }
                }
            }
        }
    }
}

/** Small tap-to-check line on the Weather screen. */
@Composable
fun UpdateChip() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val label = when (Updater.state) {
        is Updater.S.Checking -> "Checking for updates…"
        is Updater.S.UpToDate -> "You're up to date ✓"
        is Updater.S.Failed -> "Couldn't check - tap to retry"
        is Updater.S.Available -> "Update available ↓"
        else -> "v${Updater.installedName(ctx)} · check for updates"
    }
    Txt(label, 10, Maroon.copy(alpha = 0.65f), FontWeight.Medium, Modifier.clickable { scope.launch { Updater.check(ctx, true) } }.padding(6.dp), 0f)
}
