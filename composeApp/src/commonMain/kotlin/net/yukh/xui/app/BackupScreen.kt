package net.yukh.xui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.yukh.xui.shared.api.AuthExpiredException
import net.yukh.xui.shared.api.PanelApi

/** Backup / restore of the whole panel database. Engine-agnostic — the panel
 *  returns x-ui.db (SQLite) or x-ui.dump (PostgreSQL) and imports either back.
 *  Uses the iOS share sheet to save and the document picker to restore.
 *  [panel380]: the panel is v3.8.0+, which restarts itself after an import. */
@Composable
fun BackupScreen(api: PanelApi, lang: String, panel380: Boolean = false, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    // After an import on panel v3.8.0+: waiting for the panel's own restart.
    var restarting by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<Pair<String, ByteArray>?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onClose) { Text(tr("Back")) }
            Text(tr("Backup / restore"), style = MaterialTheme.typography.titleMedium)
            Box(Modifier.padding(end = 8.dp))
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                tr("The backup is the panel's whole database — settings, inbounds, clients and the Xray config. SQLite saves as x-ui.db, PostgreSQL as x-ui.dump; either restores back here."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            if (restarting) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        tr("The panel is restarting to apply the restored database…"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Back up"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(tr("Download the database and save it via the share sheet."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = {
                            if (busy) return@Button
                            busy = true; message = null; error = null
                            scope.launch {
                                runCatching { api.getDb() }
                                    .onSuccess { db -> platformExportFile(db.filename, db.bytes); message = tr(lang, "Backup ready — choose where to save") }
                                    .onFailure { message = tr(lang, "Backup failed") }
                                busy = false
                            }
                        },
                        enabled = !busy, modifier = Modifier.fillMaxWidth(),
                    ) { if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(tr("Back up to file")) }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Restore"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(tr("Replace the panel database from a backup file. This overwrites everything and restarts Xray."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(
                        onClick = { if (!busy) platformPickFile { name, bytes -> pendingRestore = name to bytes } },
                        enabled = !busy, modifier = Modifier.fillMaxWidth(),
                    ) { Text(tr("Restore from file")) }
                }
            }
        }
    }

    pendingRestore?.let { (name, bytes) ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(tr("Restore from this backup?")) },
            text = {
                Text(
                    tr("Importing") + " \"$name\" " +
                        tr("overwrites the panel database and restarts Xray. This can't be undone.") + "\n\n" +
                        tr("API tokens come from the backup too: if it doesn't contain this app's token, you'll be asked to reconnect."),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRestore = null
                        busy = true; message = null; error = null
                        scope.launch {
                            val r = runCatching { api.importDb(name, bytes) }.getOrNull()
                            when {
                                r?.success != true -> message = tr(lang, "Restore failed")
                                !panel380 -> message = tr(lang, "Restored — Xray restarted")
                                else -> {
                                    restarting = true
                                    val back = awaitPanelAfterRestart(api)
                                    restarting = false
                                    if (back) {
                                        message = tr(lang, "Restored — the panel restarted and is back")
                                    } else {
                                        error = tr(lang, "Restored, but the panel hasn't answered for a minute since its restart")
                                    }
                                }
                            }
                            busy = false
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(tr("Restore")) }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text(tr("Cancel")) } },
        )
    }
}

/**
 * A v3.8.0+ panel restarts itself about three seconds after a database import. Wait
 * past that, then poll status every two seconds for up to a minute; true once the
 * panel answers. A 401 counts as an answer: the restored database may not hold this
 * app's token, and the main screen then asks to reconnect.
 */
private suspend fun awaitPanelAfterRestart(api: PanelApi): Boolean {
    delay(5_000)
    val deadline = TimeSource.Monotonic.markNow() + 60.seconds
    while (deadline.hasNotPassedNow()) {
        val answered = try {
            api.serverStatus().success
        } catch (e: AuthExpiredException) {
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            false
        }
        if (answered) return true
        delay(2_000)
    }
    return false
}
