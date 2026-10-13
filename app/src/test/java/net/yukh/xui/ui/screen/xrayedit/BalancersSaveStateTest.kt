package net.yukh.xui.ui.screen.xrayedit

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The observatory's own Save must switch off once its edits reach the panel, and not before. */
class BalancersSaveStateTest {

    private val base: JsonObject = Json.parseToJsonElement(
        """{"routing":{"balancers":[{"tag":"b","selector":["a"],"strategy":{"type":"leastPing"}}]},"observatory":{"subjectSelector":["a"]}}""",
    ).jsonObject

    @Test
    fun `saving the observatory edit clears both the section and the screen Save`() {
        val edited = BalancersUiState(available = true, config = base, saving = true)
            .withObservatoryEdit(base.patchObservatory("probeInterval", JsonPrimitive("30s")))
        val saved = edited.afterSaved(edited.config)
        assertFalse(saved.observatoryDirty)
        assertFalse(saved.dirty)
        assertFalse(saved.saving)
    }

    @Test
    fun `an observatory edit made while the save was in flight stays unsaved`() {
        val sent = BalancersUiState(available = true, config = base).withObservatoryEdit(base.patchObservatory("probeInterval", JsonPrimitive("30s")))
        val typedMore = sent.withObservatoryEdit(sent.config.patchObservatory("probeInterval", JsonPrimitive("45s")))
        val saved = typedMore.afterSaved(sent.config)
        assertTrue(saved.observatoryDirty)
        assertTrue(saved.dirty)
    }
}
