package net.yukh.xui.ui.screen.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.yukh.xui.data.api.dto.Client
import net.yukh.xui.data.api.dto.ClientGroup
import net.yukh.xui.data.repo.PanelRepository
import net.yukh.xui.data.repo.isUnsupportedByPanel

/** A snackbar line: [text] is the English key the screen translates, [detail] is
 *  appended as is — a group name, a count or the panel's own error. */
data class GroupsNotice(val text: String, val detail: String = "")

/** The add / remove clients picker for one group. */
data class GroupClientsPicker(
    val group: String,
    /** True: choose clients to put into [group]; false: choose members to take out. */
    val adding: Boolean,
    val loading: Boolean = true,
    val clients: List<Client> = emptyList(),
    val selected: Set<String> = emptySet(),
    val query: String = "",
) {
    val visible: List<Client>
        get() {
            val q = query.trim()
            if (q.isEmpty()) return clients
            return clients.filter {
                it.email.contains(q, ignoreCase = true) ||
                    it.comment.contains(q, ignoreCase = true) ||
                    it.group.contains(q, ignoreCase = true)
            }
        }
}

data class GroupsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    /** The panel answers 404 for the group list — too old for client groups. */
    val unsupported: Boolean = false,
    val groups: List<ClientGroup> = emptyList(),
    val busy: Boolean = false,
    val notice: GroupsNotice? = null,
    val picker: GroupClientsPicker? = null,
    /** English name of a group action this panel is too old for; the screen explains it. */
    val unsupportedAction: String? = null,
)

/**
 * The panel's client groups and what is done to a group as a whole. The panel
 * restarts Xray by itself after the changes that need it, so nothing here asks the
 * user to.
 */
@HiltViewModel
class GroupsViewModel @Inject constructor(
    private val repo: PanelRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(GroupsUiState())
    val state: StateFlow<GroupsUiState> = _state.asStateFlow()

    fun load(force: Boolean = false) {
        if (force) _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            val r = repo.listClientGroups()
            r.onSuccess { list ->
                _state.update { it.copy(loading = false, refreshing = false, unsupported = false, groups = list) }
            }.onFailure { e ->
                val unsupported = r.isUnsupportedByPanel()
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        unsupported = unsupported,
                        notice = if (unsupported) null else GroupsNotice("Couldn't load groups", e.message.orEmpty()),
                    )
                }
            }
        }
    }

    fun create(name: String) = mutate(GroupsNotice("Group created", name)) { repo.createClientGroup(name) }

    fun rename(oldName: String, newName: String) =
        mutate(GroupsNotice("Group renamed", newName)) { repo.renameClientGroup(oldName, newName) }

    fun delete(name: String) =
        mutate(GroupsNotice("Group deleted, its clients kept", name)) { repo.deleteClientGroup(name) }

    fun resetTraffic(name: String) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val r = repo.resetClientGroupTraffic(name)
            r.onSuccess {
                _state.update { it.copy(busy = false, notice = GroupsNotice("Group traffic reset", name)) }
                load()
            }.onFailure { e ->
                _state.update {
                    if (r.isUnsupportedByPanel()) it.copy(busy = false, unsupportedAction = "Resetting a group's traffic")
                    else it.copy(busy = false, notice = GroupsNotice("Action failed", e.message.orEmpty()))
                }
            }
        }
    }

    /** Deletes every client of [name] from the panel. The members come from a fresh
     *  client list, so a client added a moment ago is not left behind. */
    fun deleteClients(name: String) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val all = repo.listClients().getOrElse { e ->
                _state.update { it.copy(busy = false, notice = GroupsNotice("Action failed", e.message.orEmpty())) }
                return@launch
            }
            val members = all.filter { it.group.trim() == name }.map { it.email }
            if (members.isEmpty()) {
                _state.update { it.copy(busy = false, notice = GroupsNotice("This group has no clients yet.")) }
                return@launch
            }
            repo.deleteClientsWithReport(members)
                .onSuccess { report ->
                    val notice = if (report.skipped.isEmpty()) {
                        GroupsNotice("Clients deleted", report.deleted.toString())
                    } else {
                        val reason = report.skipped.first().reason.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()
                        GroupsNotice("Deleted / skipped", "${report.deleted} / ${report.skipped.size}$reason")
                    }
                    _state.update { it.copy(busy = false, notice = notice) }
                    load()
                }
                .onFailure { e ->
                    _state.update { it.copy(busy = false, notice = GroupsNotice("Action failed", e.message.orEmpty())) }
                }
        }
    }

    // ---- Add / remove clients ------------------------------------------------

    fun openPicker(group: String, adding: Boolean) {
        _state.update { it.copy(picker = GroupClientsPicker(group, adding)) }
        viewModelScope.launch {
            repo.listClients()
                .onSuccess { all ->
                    val inGroup = { c: Client -> c.group.trim() == group }
                    val clients = (if (adding) all.filterNot(inGroup) else all.filter(inGroup))
                        .sortedBy { it.email.lowercase() }
                    updatePicker { it.copy(loading = false, clients = clients) }
                }
                .onFailure { e ->
                    _state.update { it.copy(picker = null, notice = GroupsNotice("Action failed", e.message.orEmpty())) }
                }
        }
    }

    fun setPickerQuery(q: String) = updatePicker { it.copy(query = q) }

    fun togglePicked(email: String) = updatePicker { p ->
        p.copy(selected = if (email in p.selected) p.selected - email else p.selected + email)
    }

    fun pickAllVisible() = updatePicker { p -> p.copy(selected = p.selected + p.visible.map { it.email }) }

    fun clearPicked() = updatePicker { it.copy(selected = emptySet()) }

    fun closePicker() = _state.update { it.copy(picker = null) }

    fun applyPicker() {
        val p = _state.value.picker ?: return
        if (p.selected.isEmpty() || _state.value.busy) return
        val emails = p.selected.toList()
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val r = if (p.adding) repo.addClientsToGroup(emails, p.group) else repo.removeClientsFromGroup(emails)
            r.onSuccess {
                val text = if (p.adding) "Clients added to the group" else "Clients removed from the group"
                _state.update { it.copy(busy = false, picker = null, notice = GroupsNotice(text, "${p.group} · ${emails.size}")) }
                load()
            }.onFailure { e ->
                _state.update { it.copy(busy = false, notice = GroupsNotice("Action failed", e.message.orEmpty())) }
            }
        }
    }

    private fun updatePicker(transform: (GroupClientsPicker) -> GroupClientsPicker) =
        _state.update { s -> s.picker?.let { s.copy(picker = transform(it)) } ?: s }

    private fun mutate(success: GroupsNotice, block: suspend () -> Result<Unit>) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            block()
                .onSuccess { _state.update { it.copy(busy = false, notice = success) }; load() }
                .onFailure { e ->
                    _state.update { it.copy(busy = false, notice = GroupsNotice("Action failed", e.message.orEmpty())) }
                }
        }
    }

    fun dismissNotice() = _state.update { it.copy(notice = null) }
    fun dismissUnsupportedAction() = _state.update { it.copy(unsupportedAction = null) }
}
