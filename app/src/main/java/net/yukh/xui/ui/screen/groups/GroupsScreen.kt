package net.yukh.xui.ui.screen.groups

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.yukh.xui.data.api.dto.ClientGroup
import net.yukh.xui.i18n.tr
import net.yukh.xui.ui.components.PanelFeatureUnsupported
import net.yukh.xui.ui.format.formatBytes

/** Create a group ([renaming] == null) or rename the group called [renaming]. */
private data class NameDialog(val renaming: String?)

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
 * its clients, or delete the clients themselves.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(onClose: () -> Unit, vm: GroupsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var nameDialog by remember { mutableStateOf<NameDialog?>(null) }
    var confirm by remember { mutableStateOf<GroupConfirm?>(null) }

    // The view model outlives this overlay, so every opening re-reads the panel.
    LaunchedEffect(Unit) { vm.load() }

    val notice = state.notice?.let { n -> if (n.detail.isEmpty()) tr(n.text) else "${tr(n.text)}: ${n.detail}" }
    LaunchedEffect(notice) {
        notice?.let {
            snackbar.showSnackbar(it)
            vm.dismissNotice()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(tr("Client groups")) },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Close"))
                        }
                    },
                    actions = {
                        if (state.busy) {
                            CircularProgressIndicator(Modifier.padding(end = 16.dp).size(20.dp), strokeWidth = 2.dp)
                        }
                    },
                )
            },
            floatingActionButton = {
                if (!state.loading && !state.unsupported) {
                    ExtendedFloatingActionButton(
                        onClick = { nameDialog = NameDialog(renaming = null) },
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text(tr("Create group")) },
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { vm.load(force = true) },
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                when {
                    state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

                    state.unsupported -> PanelFeatureUnsupported(tr("Client groups"))

                    state.groups.isEmpty() -> Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(tr("No groups yet."), style = MaterialTheme.typography.titleMedium)
                        Text(
                            tr("Create a group and add clients to it — or type a group name right in the client editor."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        // The bottom inset keeps the last card clear of the floating button.
                        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.groups, key = { it.name }) { group ->
                            GroupCard(
                                group = group,
                                busy = state.busy,
                                onAddClients = { vm.openPicker(group.name, adding = true) },
                                onRemoveClients = { vm.openPicker(group.name, adding = false) },
                                onResetTraffic = { confirm = GroupConfirm.ResetTraffic(group) },
                                onRename = { nameDialog = NameDialog(renaming = group.name) },
                                onDelete = { confirm = GroupConfirm.DeleteGroup(group) },
                                onDeleteClients = { confirm = GroupConfirm.DeleteClients(group) },
                            )
                        }
                    }
                }
            }
        }

        state.picker?.let { picker ->
            BackHandler(onBack = vm::closePicker)
            GroupClientsPickerScreen(
                picker = picker,
                busy = state.busy,
                onQuery = vm::setPickerQuery,
                onToggle = vm::togglePicked,
                onSelectAll = vm::pickAllVisible,
                onClear = vm::clearPicked,
                onApply = vm::applyPicker,
                onClose = vm::closePicker,
            )
        }
    }

    nameDialog?.let { d ->
        GroupNameDialog(
            renaming = d.renaming,
            existing = state.groups.map { it.name },
            onConfirm = { name ->
                nameDialog = null
                if (d.renaming == null) vm.create(name) else vm.rename(d.renaming, name)
            },
            onDismiss = { nameDialog = null },
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
                    Text(
                        "${c.group.name} · ${tr("Clients")}: ${c.group.clientCount}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(body)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    when (c) {
                        is GroupConfirm.ResetTraffic -> vm.resetTraffic(c.group.name)
                        is GroupConfirm.DeleteGroup -> vm.delete(c.group.name)
                        is GroupConfirm.DeleteClients -> vm.deleteClients(c.group.name)
                    }
                }) { Text(action, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(tr("Cancel")) } },
        )
    }

    state.unsupportedAction?.let { feature ->
        AlertDialog(
            onDismissRequest = vm::dismissUnsupportedAction,
            title = { Text(tr(feature)) },
            text = { Text(tr("Your panel version doesn't support this yet. Update the panel to the latest version to use it.")) },
            confirmButton = { TextButton(onClick = vm::dismissUnsupportedAction) { Text(tr("OK")) } },
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
                    IconButton(onClick = { menu = true }, enabled = !busy) {
                        Icon(Icons.Filled.MoreVert, contentDescription = tr("More"))
                    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupClientsPickerScreen(
    picker: GroupClientsPicker,
    busy: Boolean,
    onQuery: (String) -> Unit,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onApply: () -> Unit,
    onClose: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (picker.adding) tr("Add clients to group") else tr("Remove clients from group"),
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
                },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = tr("Close")) }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${tr("selected")}: ${picker.selected.size} / ${picker.clients.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = onApply,
                        enabled = picker.selected.isNotEmpty() && !busy,
                        colors = if (picker.adding) {
                            ButtonDefaults.buttonColors()
                        } else {
                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        },
                    ) { Text(if (picker.adding) tr("Add to group") else tr("Remove from group")) }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
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
                value = picker.query,
                onValueChange = onQuery,
                label = { Text(tr("Search clients")) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onSelectAll, enabled = picker.visible.isNotEmpty()) { Text(tr("Select all")) }
                TextButton(onClick = onClear, enabled = picker.selected.isNotEmpty()) { Text(tr("Clear")) }
            }
            when {
                picker.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

                picker.clients.isEmpty() -> Text(
                    if (picker.adding) tr("No other clients to add.") else tr("This group has no clients yet."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(picker.visible, key = { it.email }) { c ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onToggle(c.email) }.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = c.email in picker.selected, onCheckedChange = { onToggle(c.email) })
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
    }
}
