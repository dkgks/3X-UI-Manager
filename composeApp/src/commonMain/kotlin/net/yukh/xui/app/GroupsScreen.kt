package net.yukh.xui.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.yukh.xui.shared.api.PanelApi
import net.yukh.xui.shared.api.PanelFeatureUnsupportedException
import net.yukh.xui.shared.dto.ApiAck
import net.yukh.xui.shared.dto.Client
import net.yukh.xui.shared.dto.ClientGroup

/** Create a group ([renaming] == null) or rename the group called [renaming]. */
private data class GroupNameEdit(val renaming: String?)

/** The add / remove clients picker: [adding] puts clients into [group], otherwise
 *  members are taken out of it. */
private data class GroupPicker(val group: String, val adding: Boolean)

private sealed interface GroupConfirm {
    val group: ClientGroup

    data class ResetTraffic(override val group: ClientGroup) : GroupConfirm
    data class DeleteGroup(override val group: ClientGroup) : GroupConfirm
    data class DeleteClients(override val group: ClientGroup) : GroupConfirm
}

/**
 * Client groups: the panel's list (empty groups included) with each group's client
 * count and traffic, and the actions on a group as a whole — create, rename, add or
 * remove clients, reset the group's traffic counter, delete the group while keeping
 * its clients, or delete the clients themselves. Mirrors the Android screen.
 *
 * The panel restarts Xray on its own after the changes that need it, so unlike the
 * outbound subscriptions screen this one offers no restart.
 */
@Composable
fun GroupsScreen(api: PanelApi, lang: String, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var unsupported by remember { mutableStateOf(false) }
    var groups by remember { mutableStateOf<List<ClientGroup>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var nameEdit by remember { mutableStateOf<GroupNameEdit?>(null) }
    var confirm by remember { mutableStateOf<GroupConfirm?>(null) }
    var unsupportedAction by remember { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf<GroupPicker?>(null) }
    // Null while the picker's client list is still loading.
    var pickerClients by remember { mutableStateOf<List<Client>?>(null) }
    var pickerSelected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pickerQuery by remember { mutableStateOf("") }

    fun failed(detail: String) {
        error = "${tr(lang, "Action failed")}: $detail"
    }

    suspend fun load() {
        try {
            val r = api.clientGroups()
            if (r.success) {
                groups = r.obj.orEmpty()
                unsupported = false
            } else {
                error = "${tr(lang, "Couldn't load groups")}: ${r.msg}"
            }
        } catch (e: PanelFeatureUnsupportedException) {
            unsupported = true
        } catch (e: Throwable) {
            error = "${tr(lang, "Couldn't load groups")}: ${e.message.orEmpty()}"
        }
        loading = false
    }
    LaunchedEffect(Unit) { load() }

    /** Runs one panel call. A refusal shows the panel's own msg, so a failure is never silent. */
    fun mutate(done: String, block: suspend () -> ApiAck) {
        if (busy) return
        busy = true
        error = null
        message = null
        scope.launch {
            try {
                val r = block()
                if (r.success) {
                    message = done
                    load()
                } else {
                    failed(r.msg)
                }
            } catch (e: Throwable) {
                failed(e.message.orEmpty())
            }
            busy = false
        }
    }

    fun resetTraffic(name: String) {
        if (busy) return
        busy = true
        error = null
        message = null
        scope.launch {
            try {
                val r = api.resetClientGroupTraffic(name)
                if (r.success) {
                    message = "${tr(lang, "Group traffic reset")}: $name"
                    load()
                } else {
                    failed(r.msg)
                }
            } catch (e: PanelFeatureUnsupportedException) {
                unsupportedAction = "Resetting a group's traffic"
            } catch (e: Throwable) {
                failed(e.message.orEmpty())
            }
            busy = false
        }
    }

    fun deleteClients(name: String) {
        if (busy) return
        busy = true
        error = null
        message = null
        scope.launch {
            try {
                // A fresh list, so a client added a moment ago is not left behind.
                val list = api.clients()
                val members = list.obj.orEmpty().filter { it.group.trim() == name }.map { it.email }
                when {
                    !list.success -> failed(list.msg)
                    members.isEmpty() -> message = tr(lang, "This group has no clients yet.")
                    else -> {
                        val r = api.bulkDelClientsWithReport(members)
                        val report = r.obj
                        if (!r.success || report == null) {
                            failed(r.msg)
                        } else {
                            message = if (report.skipped.isEmpty()) {
                                "${tr(lang, "Clients deleted")}: ${report.deleted}"
                            } else {
                                val reason = report.skipped.first().reason.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()
                                "${tr(lang, "Deleted / skipped")}: ${report.deleted} / ${report.skipped.size}$reason"
                            }
                            load()
                        }
                    }
                }
            } catch (e: Throwable) {
                failed(e.message.orEmpty())
            }
            busy = false
        }
    }

    fun openPicker(group: String, adding: Boolean) {
        picker = GroupPicker(group, adding)
        pickerClients = null
        pickerSelected = emptySet()
        pickerQuery = ""
        error = null
        scope.launch {
            try {
                val r = api.clients()
                if (r.success) {
                    val inGroup = { c: Client -> c.group.trim() == group }
                    val all = r.obj.orEmpty()
                    pickerClients = (if (adding) all.filterNot(inGroup) else all.filter(inGroup))
                        .sortedBy { it.email.lowercase() }
                } else {
                    picker = null
                    failed(r.msg)
                }
            } catch (e: Throwable) {
                picker = null
                failed(e.message.orEmpty())
            }
        }
    }

    fun applyPicker() {
        val p = picker ?: return
        val emails = pickerSelected.toList()
        if (emails.isEmpty() || busy) return
        busy = true
        error = null
        message = null
        scope.launch {
            try {
                val r = if (p.adding) api.addClientsToGroup(emails, p.group) else api.removeClientsFromGroup(emails)
                if (r.success) {
                    val text = if (p.adding) "Clients added to the group" else "Clients removed from the group"
                    message = "${tr(lang, text)}: ${p.group} · ${emails.size}"
                    picker = null
                    load()
                } else {
                    failed(r.msg)
                }
            } catch (e: Throwable) {
                failed(e.message.orEmpty())
            }
            busy = false
        }
    }

    val openPickerFor = picker
    if (openPickerFor != null) {
        val q = pickerQuery.trim()
        val visible = pickerClients.orEmpty().filter {
            q.isEmpty() || it.email.contains(q, ignoreCase = true) ||
                it.comment.contains(q, ignoreCase = true) || it.group.contains(q, ignoreCase = true)
        }
        GroupPickerScreen(
            picker = openPickerFor,
            clients = pickerClients,
            visible = visible,
            selected = pickerSelected,
            query = pickerQuery,
            busy = busy,
            error = error,
            onQuery = { pickerQuery = it },
            onToggle = { email -> pickerSelected = if (email in pickerSelected) pickerSelected - email else pickerSelected + email },
            onSelectAll = { pickerSelected = pickerSelected + visible.map { it.email } },
            onClear = { pickerSelected = emptySet() },
            onApply = { applyPicker() },
            onClose = { picker = null; error = null },
        )
    } else {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onClose) { Text(tr("Back")) }
                    Text(tr("Client groups"), style = MaterialTheme.typography.titleMedium)
                    if (busy) {
                        CircularProgressIndicator(Modifier.padding(horizontal = 16.dp).size(20.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(onClick = { scope.launch { load() } }, enabled = !loading) { Text(tr("Refresh")) }
                    }
                }
                message?.let {
                    Text(
                        it,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                error?.let {
                    Text(
                        it,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                when {
                    loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

                    unsupported -> PanelFeatureUnsupported(tr("Client groups"))

                    else -> LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (groups.isEmpty()) {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(tr("No groups yet."), style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        tr("Create a group and add clients to it — or type a group name right in the client editor."),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        items(groups, key = { it.name }) { group ->
                            GroupCard(
                                group = group,
                                busy = busy,
                                onAddClients = { openPicker(group.name, adding = true) },
                                onRemoveClients = { openPicker(group.name, adding = false) },
                                onResetTraffic = { confirm = GroupConfirm.ResetTraffic(group) },
                                onRename = { nameEdit = GroupNameEdit(renaming = group.name) },
                                onDelete = { confirm = GroupConfirm.DeleteGroup(group) },
                                onDeleteClients = { confirm = GroupConfirm.DeleteClients(group) },
                            )
                        }
                        item {
                            OutlinedButton(
                                onClick = { nameEdit = GroupNameEdit(renaming = null) },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(tr("Create group")) }
                        }
                    }
                }
            }
        }
    }

    nameEdit?.let { edit ->
        GroupNameDialog(
            renaming = edit.renaming,
            existing = groups.map { it.name },
            onConfirm = { name ->
                nameEdit = null
                val renaming = edit.renaming
                if (renaming == null) {
                    mutate("${tr(lang, "Group created")}: $name") { api.createClientGroup(name) }
                } else {
                    mutate("${tr(lang, "Group renamed")}: $name") { api.renameClientGroup(renaming, name) }
                }
            },
            onDismiss = { nameEdit = null },
        )
    }

    confirm?.let { c ->
        val title: String
        val body: String
        val action: String
        when (c) {
            is GroupConfirm.ResetTraffic -> {
                title = tr("Reset the group's traffic?")
                body = tr("Only the group's counter starts from zero. Each client keeps its own traffic and limits.")
                action = tr("Reset traffic")
            }
            is GroupConfirm.DeleteGroup -> {
                title = tr("Delete the group?")
                body = tr("The group disappears and its clients are left without a group. The clients themselves stay on the panel.")
                action = tr("Delete group")
            }
            is GroupConfirm.DeleteClients -> {
                title = tr("Delete all clients of the group?")
                body = tr("Every client of the group is deleted from the panel together with its traffic. This cannot be undone.")
                action = tr("Delete clients")
            }
        }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${c.group.name} · ${tr("Clients")}: ${c.group.clientCount}", style = MaterialTheme.typography.titleSmall)
                    Text(body)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    when (c) {
                        is GroupConfirm.ResetTraffic -> resetTraffic(c.group.name)
                        is GroupConfirm.DeleteGroup -> mutate("${tr(lang, "Group deleted, its clients kept")}: ${c.group.name}") {
                            api.deleteClientGroup(c.group.name)
                        }
                        is GroupConfirm.DeleteClients -> deleteClients(c.group.name)
                    }
                }) { Text(action, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(tr("Cancel")) } },
        )
    }

    unsupportedAction?.let { feature ->
        AlertDialog(
            onDismissRequest = { unsupportedAction = null },
            title = { Text(tr(feature)) },
            text = { Text(tr("Your panel version doesn't support this yet. Update the panel to the latest version to use it.")) },
            confirmButton = { TextButton(onClick = { unsupportedAction = null }) { Text(tr("OK")) } },
        )
    }
}

@Composable
private fun GroupCard(
    group: ClientGroup,
    busy: Boolean,
    onAddClients: () -> Unit,
    onRemoveClients: () -> Unit,
    onResetTraffic: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDeleteClients: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val hasClients = group.clientCount > 0
    val danger = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(start = 12.dp, top = 4.dp, end = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    group.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    TextButton(onClick = { menu = true }, enabled = !busy) { Text("⋮") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(tr("Add clients…")) }, onClick = { menu = false; onAddClients() })
                        DropdownMenuItem(
                            text = { Text(tr("Remove clients…")) },
                            enabled = hasClients,
                            onClick = { menu = false; onRemoveClients() },
                        )
                        DropdownMenuItem(
                            text = { Text(tr("Reset traffic")) },
                            enabled = hasClients,
                            onClick = { menu = false; onResetTraffic() },
                        )
                        DropdownMenuItem(text = { Text(tr("Rename")) }, onClick = { menu = false; onRename() })
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(tr("Delete group")) },
                            colors = danger,
                            onClick = { menu = false; onDelete() },
                        )
                        DropdownMenuItem(
                            text = { Text(tr("Delete the group's clients")) },
                            colors = danger,
                            enabled = hasClients,
                            onClick = { menu = false; onDeleteClients() },
                        )
                    }
                }
            }
            Text(
                "${tr("Clients")}: ${group.clientCount}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val total = "${tr("Total")}: ${group.trafficUsed.formatBytes()}"
            val up = group.up
            val down = group.down
            // Panel 3.3.0 reports only the total, without the upload / download split.
            Text(
                if (up != null && down != null) "↑ ${up.formatBytes()}   ↓ ${down.formatBytes()}   ·   $total" else total,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun GroupNameDialog(
    renaming: String?,
    existing: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(renaming.orEmpty()) }
    val trimmed = name.trim()
    // The panel keys groups by name, so a case-only clash would split one group in two.
    val taken = existing.any { it.equals(trimmed, ignoreCase = true) && it != renaming }
    val unchanged = renaming != null && trimmed == renaming
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (renaming == null) tr("Create group") else tr("Rename group")) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(tr("Group name")) },
                singleLine = true,
                isError = taken,
                supportingText = if (taken) {
                    { Text(tr("A group with this name already exists.")) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmed) },
                enabled = trimmed.isNotEmpty() && !taken && !unchanged,
            ) { Text(if (renaming == null) tr("Create") else tr("Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}

@Composable
private fun GroupPickerScreen(
    picker: GroupPicker,
    clients: List<Client>?,
    visible: List<Client>,
    selected: Set<String>,
    query: String,
    busy: Boolean,
    error: String?,
    onQuery: (String) -> Unit,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onApply: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onClose) { Text(tr("Cancel")) }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (picker.adding) tr("Add clients to group") else tr("Remove clients from group"),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        picker.group,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                if (picker.adding) {
                    tr("Clients already in this group are not listed. Their inbounds stay as they are — only the group changes.")
                } else {
                    tr("The clients stay on the panel — they only leave the group.")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                label = { Text(tr("Search clients")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onSelectAll, enabled = visible.isNotEmpty()) { Text(tr("Select all")) }
                TextButton(onClick = onClear, enabled = selected.isNotEmpty()) { Text(tr("Clear")) }
            }
            error?.let {
                Text(
                    it,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    clients == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

                    clients.isEmpty() -> Text(
                        if (picker.adding) tr("No other clients to add.") else tr("This group has no clients yet."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )

                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(visible, key = { it.email }) { c ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onToggle(c.email) }.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = c.email in selected, onCheckedChange = { onToggle(c.email) })
                                Column(Modifier.weight(1f)) {
                                    Text(c.email, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val details = listOfNotNull(
                                        c.comment.takeIf { it.isNotBlank() },
                                        c.group.trim().takeIf { picker.adding && it.isNotEmpty() }?.let { "${tr("Group")}: $it" },
                                        if (!c.enable) tr("disabled") else null,
                                    ).joinToString("  ·  ")
                                    if (details.isNotEmpty()) {
                                        Text(
                                            details,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${tr("selected")}: ${selected.size} / ${clients.orEmpty().size}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onApply,
                    enabled = selected.isNotEmpty() && !busy,
                    colors = if (picker.adding) {
                        ButtonDefaults.buttonColors()
                    } else {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    },
                ) { Text(if (picker.adding) tr("Add to group") else tr("Remove from group")) }
            }
        }
    }
}
