package net.yukh.xui.ui.screen.nodes

import net.yukh.xui.data.api.dto.Node
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The panel's node update writes inboundSyncMode, inboundTags and pinnedCertSha256
 * from the body: leaving them out reset "selected" sync to "all" and broke pin mode.
 */
class NodeSaveCarryTest {
    private val listed = Node(
        id = 3, name = "n", address = "node.example", port = 2053,
        tlsVerifyMode = "pin", pinnedCertSha256 = "ab".repeat(32),
        inboundSyncMode = "selected", inboundTags = listOf("in-443-tcp"), outboundTag = "warp",
    )

    private fun editorFor(n: Node) = NodeEditorState(
        isNew = false, source = n, id = n.id, name = n.name, address = n.address,
        port = n.port.toString(), tlsVerifyMode = n.tlsVerifyMode,
        pinnedCertSha256 = n.pinnedCertSha256, outboundTag = n.outboundTag,
    )

    @Test
    fun editKeepsInboundSyncAndPin() {
        val sent = editorFor(listed).copy(remark = "edited").toNodeModel()

        assertEquals("selected", sent.inboundSyncMode)
        assertEquals(listOf("in-443-tcp"), sent.inboundTags)
        assertEquals("ab".repeat(32), sent.pinnedCertSha256)
        assertEquals("warp", sent.outboundTag)
        assertEquals("edited", sent.remark)
        assertNull(sent.apiToken)
    }

    @Test
    fun pinModeNeedsAPin() {
        val e = editorFor(listed)

        assertTrue(e.canSave)
        assertFalse(e.copy(pinnedCertSha256 = " ").canSave)
    }
}
