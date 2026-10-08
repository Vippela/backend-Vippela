package com.vippela.backend

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.sql.Connection
import javax.sql.DataSource

class HealthControllerTest {
    @Test fun healthRequiresWorkingDatabaseAndNeverExposesConnectionErrors() {
        val source = mock(DataSource::class.java)
        val connection = mock(Connection::class.java)
        `when`(source.connection).thenReturn(connection)
        `when`(connection.isValid(2)).thenReturn(true)
        val controller = HealthController(source)
        assertEquals(200, controller.health().statusCode.value())
        verify(connection).close()
        `when`(source.connection).thenThrow(java.sql.SQLException("private connection information"))
        val failure = controller.health()
        assertEquals(503, failure.statusCode.value())
        assertEquals(mapOf("status" to "unavailable"), failure.body)
    }
}
