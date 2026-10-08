package com.vippela.backend

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import javax.sql.DataSource

@RestController
class HealthController(private val database: DataSource) {
    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, String>> {
        val ready = runCatching { database.connection.use { it.isValid(2) } }.getOrDefault(false)
        return ResponseEntity.status(if (ready) 200 else 503)
            .body(mapOf("status" to if (ready) "ok" else "unavailable"))
    }
}
