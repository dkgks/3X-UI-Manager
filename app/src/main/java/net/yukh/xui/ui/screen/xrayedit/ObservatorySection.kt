package net.yukh.xui.ui.screen.xrayedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.yukh.xui.data.json.bool
import net.yukh.xui.data.json.child
import net.yukh.xui.data.json.int
import net.yukh.xui.data.json.string
import net.yukh.xui.data.json.strings
import net.yukh.xui.i18n.tr
import net.yukh.xui.ui.components.Field
import net.yukh.xui.ui.components.LabeledDropdown
import net.yukh.xui.ui.components.SectionTitle
import net.yukh.xui.ui.components.SwitchRow

private val HTTP_METHODS = listOf("HEAD", "GET")

/** The panel's Observatory tab: shows the one active observer of [cfg]; observers themselves
 *  are created and dropped by [syncObservatories] when balancers change. */
@Composable
internal fun ObservatorySection(cfg: JsonObject, onChange: (JsonObject) -> Unit) {
    val observatory = cfg.observatoryView()
    val burst = cfg.burstObservatoryView()
    SectionTitle(tr("Observatory"))
    if (observatory == null && burst == null) {
        Help(tr("No connection observer is active. One is added automatically when you create a Least Ping or Least Load balancer — or a Random / Round-robin balancer with a fallback — so observer-backed balancers can check outbound health before choosing a target."))
        return
    }
    Notice(
        tr("Observers are managed automatically from your balancers. Tune how they probe below — the watched outbounds follow your balancer selectors."),
        MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer,
    )
    if (observatory != null && burst != null) {
        Notice(
            tr("This config contains both Observatory and Burst Observatory. Xray uses one global observer, so this mixed legacy state is not supported; saving balancers will normalize it to one observer."),
            MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer,
        )
    }
    if (burst != null && (observatory == null || cfg.requiresBurstObservatory())) {
        BurstFields(burst) { key, value -> onChange(cfg.patchPingConfig(key, value)) }
    } else if (observatory != null) {
        ObservatoryFields(observatory) { key, value -> onChange(cfg.patchObservatory(key, value)) }
    }
}

@Composable
private fun ObservatoryFields(o: JsonObject, patch: (String, JsonPrimitive) -> Unit) {
    Selectors(o.strings("subjectSelector"))
    Field(tr("Probe URL"), o.string("probeURL")) { patch("probeURL", JsonPrimitive(it)) }
    Help(tr("URL fetched to measure each outbound. Should return HTTP 204."))
    Field(tr("Probe Interval"), o.string("probeInterval")) { patch("probeInterval", JsonPrimitive(it)) }
    Help(tr("How often to probe each outbound, e.g. 30s, 1m, 2h45m."))
    SwitchRow(tr("Concurrent Probing"), o.bool("enableConcurrency")) { patch("enableConcurrency", JsonPrimitive(it)) }
    Help(tr("Probe all watched outbounds at once instead of one-by-one. Faster, but more visible on the network."))
}

@Composable
private fun BurstFields(burst: JsonObject, patch: (String, JsonPrimitive) -> Unit) {
    val ping = burst.child("pingConfig")
    Selectors(burst.strings("subjectSelector"))
    Field(tr("Probe Destination"), ping.string("destination")) { patch("destination", JsonPrimitive(it)) }
    Help(tr("URL fetched to measure each outbound. Should return HTTP 204."))
    Field(tr("Connectivity Check"), ping.string("connectivity")) { patch("connectivity", JsonPrimitive(it)) }
    Help(tr("Optional local-network check URL, tried only after the destination fails. Leave empty to skip."))
    Field(tr("Probe Interval"), ping.string("interval")) { patch("interval", JsonPrimitive(it)) }
    Help(tr("Average time between probes per outbound, e.g. 1m. Minimum 10s."))
    Field(tr("Probe Timeout"), ping.string("timeout")) { patch("timeout", JsonPrimitive(it)) }
    Help(tr("How long to wait for a probe before it counts as failed, e.g. 5s."))
    // Like the panel's InputNumber: a cleared or < 1 entry is not stored.
    val sampling = ping.int("sampling") ?: 2
    var samplingText by remember(sampling) { mutableStateOf(sampling.toString()) }
    Field(tr("Sampling Count"), samplingText, numeric = true) { text ->
        samplingText = text.filter(Char::isDigit)
        samplingText.toIntOrNull()?.takeIf { it >= 1 }?.let { patch("sampling", JsonPrimitive(it)) }
    }
    Help(tr("Number of recent probe results kept to score each outbound."))
    LabeledDropdown(tr("HTTP Method"), ping.string("httpMethod"), HTTP_METHODS) { patch("httpMethod", JsonPrimitive(it)) }
    Help(tr("HTTP method used for probes."))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Selectors(tags: List<String>) {
    Column {
        Text(tr("Watched Outbounds"), style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (tags.ifEmpty { listOf("—") }).forEach { SuggestionChip(onClick = {}, label = { Text(it) }) }
        }
        Help(tr("Outbound tags this observer probes. Managed automatically from your balancers."))
    }
}

@Composable
private fun Notice(text: String, container: Color, content: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = container), modifier = Modifier.fillMaxWidth()) {
        Text(text, modifier = Modifier.padding(12.dp), color = content, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Help(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
