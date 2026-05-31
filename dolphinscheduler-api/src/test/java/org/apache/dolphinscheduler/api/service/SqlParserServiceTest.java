/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.dolphinscheduler.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.dolphinscheduler.api.dto.sqljob.SqlParseResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SqlParserService}.
 * No Spring context needed — service has no dependencies.
 */
class SqlParserServiceTest {

    private SqlParserService parser;

    @BeforeEach
    void setUp() {
        parser = new SqlParserService();
    }

    // ── Scenario 1: simple INSERT INTO … FROM ──────────────────────────────

    @Test
    void testSimpleInsertIntoFrom() {
        String sql = "INSERT INTO t1 SELECT * FROM t2";
        SqlParseResult result = parser.parse(sql);

        assertEquals("t1", result.getTargetTable());
        assertTrue(result.getSourceTables().contains("t2"),
                "Expected t2 in sourceTables");
        assertFalse(result.getSourceTables().contains("t1"),
                "Target table should not appear in sourceTables");
        assertFalse(result.isHasMultipleTargets());
    }

    // ── Scenario 2: INSERT OVERWRITE TABLE … JOIN ─────────────────────────

    @Test
    void testInsertOverwriteTableWithJoin() {
        String sql = "INSERT OVERWRITE TABLE t1\n"
                + "SELECT a.*, b.name\n"
                + "FROM t2 a\n"
                + "LEFT JOIN t3 b ON a.id = b.id";
        SqlParseResult result = parser.parse(sql);

        assertEquals("t1", result.getTargetTable());
        assertTrue(result.getSourceTables().contains("t2"), "Expected t2");
        assertTrue(result.getSourceTables().contains("t3"), "Expected t3");
        assertFalse(result.isHasMultipleTargets());
    }

    // ── Scenario 3: CTE exclusion ─────────────────────────────────────────

    @Test
    void testCteAliasExcluded() {
        String sql = "WITH cte AS (\n"
                + "  SELECT * FROM t2\n"
                + ")\n"
                + "INSERT INTO t1\n"
                + "SELECT * FROM cte\n"
                + "JOIN s2 ON cte.id = s2.id";
        SqlParseResult result = parser.parse(sql);

        assertEquals("t1", result.getTargetTable());
        assertFalse(result.getSourceTables().contains("cte"),
                "CTE alias should be excluded from sourceTables");
        assertTrue(result.getSourceTables().contains("t2"), "Expected t2 (real source)");
        assertTrue(result.getSourceTables().contains("s2"), "Expected s2");
    }

    // ── Scenario 4: multiple JOINs ────────────────────────────────────────

    @Test
    void testMultipleJoins() {
        String sql = "INSERT INTO target\n"
                + "SELECT *\n"
                + "FROM t2\n"
                + "LEFT JOIN t3 ON t2.id = t3.id\n"
                + "INNER JOIN t4 ON t2.id = t4.id";
        SqlParseResult result = parser.parse(sql);

        assertEquals("target", result.getTargetTable());
        assertTrue(result.getSourceTables().contains("t2"), "Expected t2");
        assertTrue(result.getSourceTables().contains("t3"), "Expected t3");
        assertTrue(result.getSourceTables().contains("t4"), "Expected t4");
    }

    // ── Scenario 5: dynamic variable reference ────────────────────────────

    @Test
    void testDynamicVariableUnresolved() {
        String sql = "INSERT INTO t1 SELECT * FROM ${tableName}";
        SqlParseResult result = parser.parse(sql);

        assertEquals("t1", result.getTargetTable());
        assertFalse(result.getUnresolvedRefs().isEmpty(),
                "Dynamic variable should appear in unresolvedRefs");
        assertTrue(result.getUnresolvedRefs().stream()
                .anyMatch(r -> r.contains("tableName")),
                "Expected ${tableName} in unresolvedRefs");
    }

    // ── Scenario 6: comment stripping + mixed case ────────────────────────

    @Test
    void testCommentStrippingMixedCase() {
        String sql = "-- this is a comment\n"
                + "insert Into T1\n"
                + "SELECT * FROM T2 /* inline comment */";
        SqlParseResult result = parser.parse(sql);

        assertEquals("t1", result.getTargetTable(), "Should be lowercased");
        assertTrue(result.getSourceTables().contains("t2"),
                "Source table should be lowercased");
    }

    // ── Scenario 7: SELECT-only (no INSERT) ──────────────────────────────

    @Test
    void testSelectOnly() {
        String sql = "SELECT * FROM some_table WHERE id = 1";
        SqlParseResult result = parser.parse(sql);

        assertNull(result.getTargetTable(), "No INSERT means no target table");
        assertFalse(result.isHasMultipleTargets());
    }

    // ── Scenario 8: multiple INSERT targets ──────────────────────────────

    @Test
    void testMultipleInsertTargets() {
        String sql = "INSERT INTO t1 SELECT * FROM src;\n"
                + "INSERT INTO t2 SELECT * FROM src;";
        SqlParseResult result = parser.parse(sql);

        assertTrue(result.isHasMultipleTargets(),
                "Multiple INSERT targets should be flagged");
        assertNotNull(result.getTargetTable(), "First target should be set");
    }

    // ── Scenario 9: null / blank input ────────────────────────────────────

    @Test
    void testNullInput() {
        SqlParseResult result = parser.parse(null);
        assertNull(result.getTargetTable());
        assertTrue(result.getSourceTables().isEmpty());
    }

    @Test
    void testBlankInput() {
        SqlParseResult result = parser.parse("   ");
        assertNull(result.getTargetTable());
        assertTrue(result.getSourceTables().isEmpty());
    }
}
