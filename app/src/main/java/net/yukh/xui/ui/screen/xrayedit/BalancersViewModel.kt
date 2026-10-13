package net.yukh.xui.ui.screen.xrayedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import net.yukh.xui.data.repo.PanelRepository
import net.yukh.xui.data.repo.isUnsupportedByPanel

data class BalEdit(val index: Int, val isNew: Boolean, val draft: JsonObject)

data class BalancersUiState(
    val loading: Boolean = true,
    val available: Boolean = false,
    /** The panel is older than this feature (its endpoint 404s). */
    val unsupported: Boolean = false,
    val config: JsonObject = JsonObject(emptyMap()),
    val testUrl: String = "https://www.google.com/generate_204",
    val dirty: Boolean = false,
    /** Observer probe settings were edited and not saved yet (drives the section's own Save). */
    val observatoryDirty: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val savedMessage: String? = null,
    val editingBalancer: BalEdit? = null,
    /** Tags of outbounds injected from outbound subscriptions — selectable as
     *  balancer fallbacks even though they aren't in the editable config. */
    val subscriptionOutboundTags: List<String> = emptyList(),
) {
    fun withObservatoryEdit(config: JsonObject) = copy(config = config, dirty = true, observatoryDirty = true)

    /** Edits made while [saved] was in flight stay unsaved; everything else is clean now. */
    fun afterSaved(saved: JsonObject): BalancersUiState {
        val clean = config == saved
        return copy(saving = false, dirty = dirty && !clean, observatoryDirty = observatoryDirty && !clean, savedMessage = "Saved — restart Xray to apply")
    }
}

/** Balancers + the observatory they drive; loads and saves the whole Xray template like Routing. */
@HiltViewModel
class BalancersViewModel @Inject constructor(
    private val repo: PanelRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BalancersUiState())
    val state: StateFlow<BalancersUiState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repo.loadXrayConfig()
                .onSuccess { load ->
                    _state.update {
                        it.copy(
                            loading = false, available = true, config = load.config, testUrl = load.testUrl,
                            subscriptionOutboundTags = load.subscriptionOutboundTags,
                            dirty = false, observatoryDirty = false, error = null,
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, available = false, unsupported = e.isUnsupportedByPanel(), error = e.message ?: "Xray config unavailable") } }
        }
    }

    fun update(config: JsonObject) = _state.update { it.copy(config = config, dirty = true) }
    fun updateObservatory(config: JsonObject) = _state.update { it.withObservatoryEdit(config) }

    fun openBalancer(index: Int, draft: JsonObject, isNew: Boolean) = _state.update { it.copy(editingBalancer = BalEdit(index, isNew, draft)) }
    fun updateBalDraft(d: JsonObject) = _state.update { it.copy(editingBalancer = it.editingBalancer?.copy(draft = d)) }
    fun closeBalancer() = _state.update { it.copy(editingBalancer = null) }

    fun save() {
        val st = _state.value
        if (st.saving) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            repo.saveXrayConfig(st.config, st.testUrl)
                .onSuccess { _state.update { it.afterSaved(st.config) } }
                .onFailure { e -> _state.update { it.copy(saving = false, error = e.message ?: "Save failed") } }
        }
    }

    fun dismissSavedMessage() = _state.update { it.copy(savedMessage = null) }
    fun dismissError() = _state.update { it.copy(error = null) }
}
