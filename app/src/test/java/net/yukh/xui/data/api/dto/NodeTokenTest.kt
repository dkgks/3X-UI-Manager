package net.yukh.xui.data.api.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Panel 3.8.0 binds a node's apiToken as *string: a missing key keeps the stored
 * token, a present "" replaces it. The panel never returns the token, and the app's
 * Json encodes defaults, so an edit used to send "apiToken":"" and lose the token.
 */
class NodeTokenTest {
    // Same settings as NetworkModule.provideJson().
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = true
        encodeDefaults = true
    }

    @Test
    fun editOfListedNodeLeavesTheTokenOut() {
        val listed = Node(id = 7, name = "node-a", address = "node.example", port = 443)

        val model = listed.toModel()
        val body = json.encodeToString(NodeModel.serializer(), model)

        assertNull(model.apiToken)
        assertFalse(body, body.contains("apiToken"))
    }

    @Test
    fun enteredTokenIsSent() {
        val body = json.encodeToString(NodeModel.serializer(), NodeModel(name = "node-a", apiToken = "abc"))

        assertTrue(body, body.contains("\"apiToken\":\"abc\""))
    }
}
