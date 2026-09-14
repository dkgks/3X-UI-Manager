package net.yukh.xui.data.api.dto

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards the /clients/groups parse across panel versions. Panel 3.3.0 lists a
 * group as {name, clientCount, trafficUsed} only; 3.3.1 added up/down. The Groups
 * screen shows ↑/↓ only when the panel reported them, so a 3.3.0 row must leave
 * them null instead of reading as a real zero.
 */
class ClientGroupParseTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = true
    }

    @Test
    fun parsesGroupsFromOldAndNewPanels() {
        val payload = """[
          {"name":"team-a","clientCount":3,"trafficUsed":3072,"up":1024,"down":2048},
          {"name":"empty","clientCount":0,"trafficUsed":0,"up":0,"down":0},
          {"name":"legacy","clientCount":2,"trafficUsed":512}
        ]"""

        val list = json.decodeFromString(ListSerializer(ClientGroup.serializer()), payload)

        assertEquals(3, list.size)
        assertEquals(1024L, list[0].up)
        assertEquals(0L, list[1].down)
        assertEquals(512L, list[2].trafficUsed)
        assertNull(list[2].up)
        assertNull(list[2].down)
    }
}
