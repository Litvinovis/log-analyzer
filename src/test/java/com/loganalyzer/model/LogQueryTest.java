package com.loganalyzer.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LogQueryTest {

    @Test
    void shouldCreateLogQuery() {
        Instant from = Instant.now().minusSeconds(3600);
        Instant to = Instant.now();
        List<String> apps = List.of("app1", "app2");

        LogQuery query = new LogQuery(apps, from, to, List.of("ERROR"), "timeout");

        assertEquals(apps, query.apps());
        assertEquals(from, query.from());
        assertEquals(to, query.to());
        assertEquals(List.of("ERROR"), query.levels());
        assertEquals("timeout", query.contains());
    }

    @Test
    void shouldAllowNullContains() {
        LogQuery query = new LogQuery(List.of("app"), null, null, null, null);
        assertNull(query.contains());
    }
}
