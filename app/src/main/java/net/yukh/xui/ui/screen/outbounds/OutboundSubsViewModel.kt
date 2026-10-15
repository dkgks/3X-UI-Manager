package net.yukh.xui.ui.screen.outbounds

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
import net.yukh.xui.data.api.dto.OutboundSubscription
import net.yukh.xui.data.json.string
import net.yukh.xui.data.repo.PanelRepository
import net.yukh.xui.data.repo.isUnsupportedByPanel

/** One line of a subscription preview: the tag the panel would assign and the
 *  protocol behind it. */
data class PreviewedOutbound(val tag: String, val protocol: String)

data class OutboundSubsUiState(
    val loading: Boolean = true,
    /** The panel is older than the outbound-subscription feature (404). */
    val unsupported: Boolean = false,
    val items: List<OutboundSubscription> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
    /** Outbounds a refresh yielded, shown after [message] as " (N)". */
    val messageCount: Int? = null,
    val error: String? = null,
    /** Non-null while the add/edit sheet is open. */
    val editor: OutboundSubscription? = null,
    val previewing: Boolean = false,
    val preview: List<PreviewedOutbound>? = null,
) {
    /** Only a plain edit waits for a manual restart; other changes reach Xray by themselves. */
    val offersRestart: Boolean get() = message == SUB_SAVED_RESTART
}

/** An edit saves the row only; the panel flags no reload, so Xray sees it on its next restart. */
const val SUB_SAVED_RESTART = "Saved — restart Xray to apply"

/** Refresh/move/delete flag Xray for the panel's 30-second reload job (live where it can). */
const val SUB_SAVED_APPLIES_SOON = "Saved — the panel applies it within 30 s"
const val SUB_CHANGED_APPLIES_SOON = "Done — the panel applies it within 30 s"
const val SUB_REFRESHED_APPLIES_SOON = "Refreshed — the panel applies it within 30 s"

/**
 * The panel's "Subscriptions" panel on the Outbounds page: remote lists of
 * outbounds the panel fetches on a timer and merges into the Xray config.
 *
 * Refresh, reorder and delete make the panel flag Xray for its 30-second reload
 * job; a plain edit does not, so only then the screen offers a restart.
 */
@HiltViewModel
class OutboundSubsViewModel @Inject constructor(
    private val repo: PanelRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OutboundSubsUiState())
    val state: StateFlow<OutboundSubsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val r = repo.listOutboundSubs()
            r.onSuccess { list ->
                _state.update { it.copy(loading = false, unsupported = false, items = list.sortedBy { s -> s.priority }) }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        loading = false,
                        unsupported = r.isUnsupportedByPanel(),
                        error = if (r.isUnsupportedByPanel()) null else e.message,
                    )
                }
            }
        }
    }

    // ---- Editor ----------------------------------------------------------

    fun openNew() = _state.update { it.copy(editor = OutboundSubscription(), preview = null) }

    fun openEdit(sub: OutboundSubscription) = _state.update { it.copy(editor = sub, preview = null) }

    fun closeEditor() = _state.update { it.copy(editor = null, preview = null, previewing = false) }

    fun editDraft(transform: (OutboundSubscription) -> OutboundSubscription) =
        _state.update { s -> s.copy(editor = s.editor?.let(transform)) }

    /** Fetch and parse the URL without saving, so the user sees what it yields. */
    fun preview() {
        val draft = _state.value.editor ?: return
        if (draft.url.isBlank() || _state.value.previewing) return
        _state.update { it.copy(previewing = true, preview = null, error = null) }
        viewModelScope.launch {
            repo.previewOutboundSub(draft.url, draft.allowPrivate, draft.allowInsecure)
                .onSuccess { list ->
                    _state.update { it.copy(previewing = false, preview = list.map(::toPreview)) }
                }
                .onFailure { e -> _state.update { it.copy(previewing = false, error = e.message) } }
        }
    }

    fun save() {
        val draft = _state.value.editor ?: return
        if (draft.url.isBlank() || _state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            if (draft.id != 0) {
                repo.updateOutboundSub(draft)
                    .onSuccess {
                        _state.update { it.copy(busy = false, editor = null, preview = null, message = SUB_SAVED_RESTART) }
                        load()
                    }
                    .onFailure { e -> _state.update { it.copy(busy = false, error = e.message) } }
                return@launch
            }
            repo.createOutboundSub(draft)
                .onSuccess { created ->
                    // Like the panel's own form: fetch it at once, which also flags Xray for reload.
                    val fetched = created?.id?.takeIf { it > 0 }?.let { repo.refreshOutboundSub(it) }
                    _state.update {
                        it.copy(
                            busy = false, editor = null, preview = null,
                            message = if (fetched?.isSuccess == true) SUB_SAVED_APPLIES_SOON else null,
                            messageCount = fetched?.getOrNull()?.size,
                            error = fetched?.exceptionOrNull()?.message,
                        )
                    }
                    load()
                }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message) } }
        }
    }

    // ---- Row actions -----------------------------------------------------

    fun setEnabled(sub: OutboundSubscription, enabled: Boolean) = mutate(SUB_SAVED_RESTART) {
        repo.updateOutboundSub(sub.copy(enabled = enabled))
    }

    fun refresh(id: Int) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            repo.refreshOutboundSub(id)
                .onSuccess { obs ->
                    _state.update { it.copy(busy = false, message = SUB_REFRESHED_APPLIES_SOON, messageCount = obs.size) }
                    load()
                }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message) } }
        }
    }

    fun refreshAll() {
        val ids = _state.value.items.filter { it.enabled }.map { it.id }
        if (ids.isEmpty() || _state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            var failed = 0
            ids.forEach { id -> repo.refreshOutboundSub(id).onFailure { failed++ } }
            _state.update {
                it.copy(busy = false, message = if (failed == 0) SUB_REFRESHED_APPLIES_SOON else "$failed failed")
            }
            load()
        }
    }

    fun move(id: Int, up: Boolean) = mutate(SUB_CHANGED_APPLIES_SOON) { repo.moveOutboundSub(id, up) }

    fun delete(id: Int) = mutate(SUB_CHANGED_APPLIES_SOON) { repo.deleteOutboundSub(id) }

    fun restartXray() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            repo.restartXray()
                .onSuccess { _state.update { it.copy(busy = false, message = "Xray is restarting…") } }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message) } }
        }
    }

    private fun mutate(doneMessage: String, block: suspend () -> Result<Unit>) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            block()
                .onSuccess { _state.update { it.copy(busy = false, message = doneMessage) }; load() }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message) } }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null, messageCount = null) }
    fun dismissError() = _state.update { it.copy(error = null) }
}

private fun toPreview(o: JsonObject) = PreviewedOutbound(
    tag = o.string("tag"),
    protocol = o.string("protocol"),
)
