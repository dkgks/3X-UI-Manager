package net.yukh.xui.data.api.dto

import kotlinx.serialization.Serializable

/**
 * A client group as GET /panel/api/clients/groups lists it (panel v3.3.0+). The
 * panel's list includes groups no client uses yet, which the clients list alone
 * can't reveal. [up]/[down] arrived in panel 3.3.1 and stay null on 3.3.0. The
 * traffic counts from the group's last reset, apart from each client's own counters.
 */
@Serializable
data class ClientGroup(
    val name: String = "",
    val clientCount: Int = 0,
    val trafficUsed: Long = 0,
    val up: Long? = null,
    val down: Long? = null,
)

/** Body for POST /panel/api/clients/groups/create | delete | resetTraffic. */
@Serializable
data class GroupNameRequest(val name: String)

/** Body for POST /panel/api/clients/groups/rename. */
@Serializable
data class GroupRenameRequest(val oldName: String, val newName: String)

/** Body for POST /panel/api/clients/groups/bulkAdd — the panel creates [group] if missing. */
@Serializable
data class GroupAddClientsRequest(val emails: List<String>, val group: String)
