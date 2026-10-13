package net.yukh.xui.ui.screen.xrayedit

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import net.yukh.xui.data.json.array
import net.yukh.xui.data.json.asObject
import net.yukh.xui.data.json.child
import net.yukh.xui.data.json.parseCsv
import net.yukh.xui.data.json.put
import net.yukh.xui.data.json.putArray
import net.yukh.xui.data.json.putString
import net.yukh.xui.data.json.putStrings
import net.yukh.xui.data.json.string
import net.yukh.xui.data.json.strings
import net.yukh.xui.i18n.tr
import net.yukh.xui.ui.components.ConfirmDialog
import net.yukh.xui.ui.components.Field
import net.yukh.xui.ui.components.LabeledDropdown
import net.yukh.xui.ui.components.PanelFeatureUnsupported
import net.yukh.xui.ui.components.SectionTitle

private val BAL_STRATEGY = listOf("random", "roundRobin", "leastLoad", "leastPing")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalancersScreen(onClose: () -> Unit, vm: BalancersViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(state.savedMessage) { state.savedMessage?.let { snackbar.showSnackbar(it); vm.dismissSavedMessage() } }
    LaunchedEffect(state.error) { if (state.available) state.error?.let { snackbar.showSnackbar(it); vm.dismissError() } }

    val cfg = state.config
    val bal = state.editingBalancer
    if (bal != null) {
        // System Back leaves the editor; without this it closed the whole Balancers screen.
        androidx.activity.compose.BackHandler(onBack = vm::closeBalancer)
        BalancerEditor(
            draft = bal.draft, isNew = bal.isNew, outboundTags = cfg.outboundTags() + state.subscriptionOutboundTags,
            onChange = vm::updateBalDraft, onCancel = vm::closeBalancer,
            onDone = {
                val routing = cfg.child("routing")
                val arr = routing.array("balancers").toMutableList()
                val clean = normalizeBalancer(bal.draft)
                if (bal.isNew) arr.add(clean) else arr[bal.index] = clean
                vm.update(syncObservatories(cfg.put("routing", routing.putArray("balancers", arr))))
                vm.closeBalancer()
                vm.save()
            },
        )
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(tr("Balancers")) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Close")) } },
                actions = {
                    if (state.available) TextButton(onClick = vm::save, enabled = state.dirty && !state.saving) {
                        if (state.saving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) else Text(tr("Save"))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) { Snackbar { Text(it.visuals.message) } } },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                state.unsupported -> PanelFeatureUnsupported(tr("Balancers"))
                !state.available -> SessionGate()
                else -> BalancersBody(state, vm)
            }
        }
    }
}

@Composable
private fun BalancersBody(state: BalancersUiState, vm: BalancersViewModel) {
    val cfg = state.config
    val routing = cfg.child("routing")
    val balancers = routing.array("balancers")
    var pendingBal by remember { mutableStateOf<Int?>(null) }

    fun setBalancers(items: List<JsonElement>) =
        vm.update(syncObservatories(cfg.put("routing", routing.putArray("balancers", items))))

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        balancers.forEachIndexed { i, el ->
            val b = el.asObject()
            Card(modifier = Modifier.fillMaxWidth().clickable { vm.openBalancer(i, b, isNew = false) }) {
                Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(b.string("tag").ifBlank { "(no tag)" }, style = MaterialTheme.typography.titleSmall)
                        Text(b.strings("selector").joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { pendingBal = i }) { Icon(Icons.Outlined.Delete, contentDescription = tr("Delete")) }
                }
            }
        }
        OutlinedButton(onClick = { vm.openBalancer(-1, JsonObject(emptyMap()), isNew = true) }) {
            Icon(Icons.Filled.Add, contentDescription = null); Text("  " + tr("Add balancer"))
        }

        ObservatorySection(
            cfg, vm::updateObservatory,
            saveEnabled = state.observatoryDirty && !state.saving, saving = state.saving && state.observatoryDirty, onSave = vm::save,
        )
    }

    pendingBal?.let { idx ->
        ConfirmDialog(tr("Delete balancer?"), balancers.getOrNull(idx)?.asObject()?.string("tag").orEmpty(), tr("Delete"), destructive = true,
            onConfirm = { pendingBal = null; val a = balancers.toMutableList(); if (idx in a.indices) a.removeAt(idx); setBalancers(a) },
            onDismiss = { pendingBal = null })
    }
}

/** Strategy as the panel writes it: none for random, `settings` only for leastLoad. The
 *  draft keeps them while the type is switched, so going back to leastLoad restores them. */
internal fun normalizeBalancer(b: JsonObject): JsonObject {
    val strategy = b["strategy"] as? JsonObject ?: return b
    val type = strategy.string("type")
    return when {
        type.isBlank() || type == "random" -> b.put("strategy", null)
        type != "leastLoad" -> b.put("strategy", strategy.put("settings", null))
        else -> b
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BalancerEditor(
    draft: JsonObject, isNew: Boolean, outboundTags: List<String>,
    onChange: (JsonObject) -> Unit, onCancel: () -> Unit, onDone: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) tr("New balancer") else tr("Edit balancer")) },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Close")) } },
                actions = { TextButton(onClick = onDone) { Text(tr("Save")) } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(tr("Tag"), draft.string("tag")) { onChange(draft.putString("tag", it)) }
            val strategy = draft.child("strategy").string("type").ifBlank { "random" }
            LabeledDropdown(tr("Strategy"), strategy, BAL_STRATEGY) {
                onChange(draft.put("strategy", draft.child("strategy").putString("type", it)))
            }
            Field(tr("Selector (comma-separated)"), draft.strings("selector").joinToString(", ")) {
                val l = parseCsv(it); onChange(draft.putStrings("selector", l))
            }
            LabeledDropdown(tr("Fallback"), draft.string("fallbackTag"), listOf("") + outboundTags) {
                onChange(if (it.isBlank()) draft.put("fallbackTag", null) else draft.putString("fallbackTag", it))
            }
        }
    }
}
