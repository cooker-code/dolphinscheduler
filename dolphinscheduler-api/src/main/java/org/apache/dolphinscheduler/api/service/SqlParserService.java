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

import org.apache.dolphinscheduler.api.dto.sqljob.SqlParseResult;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

/**
 * Statically parses SQL strings using regular expressions.
 *
 * <p>Identifies:
 * <ul>
 *   <li>Target table: {@code INSERT INTO/OVERWRITE [TABLE] <name>}</li>
 *   <li>Source tables: {@code FROM <name>} and {@code JOIN <name>}</li>
 *   <li>CTE aliases defined by {@code WITH <name> AS (…)} — excluded from source tables</li>
 *   <li>Unresolved dynamic variable references: {@code ${varName}}</li>
 * </ul>
 *
 * <p>This is a Phase-1 regex-based implementation.  Complex nested sub-queries may not be
 * fully resolved; such cases are tagged as unresolved rather than producing wrong results.
 * JSQLParser / ANTLR can be introduced in Phase 2 for more precise parsing.
 */
@Service
public class SqlParserService {

    // INSERT INTO/OVERWRITE [TABLE] tableName
    private static final Pattern INSERT_PATTERN = Pattern.compile(
            "\\bINSERT\\s+(?:INTO|OVERWRITE)\\s+(?:TABLE\\s+)?([`'\"]?[\\w.]+[`'\"]?)",
            Pattern.CASE_INSENSITIVE);

    // WITH cte_name AS (
    private static final Pattern CTE_PATTERN = Pattern.compile(
            "\\bWITH\\b[\\s\\S]*?\\b(\\w+)\\s+AS\\s*\\(",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    // FROM tableName (stops at whitespace, comma, or opening paren)
    private static final Pattern FROM_PATTERN = Pattern.compile(
            "\\bFROM\\s+([`'\"]?\\w[\\w.]*[`'\"]?)",
            Pattern.CASE_INSENSITIVE);

    // [LEFT|RIGHT|INNER|FULL|CROSS] JOIN tableName
    private static final Pattern JOIN_PATTERN = Pattern.compile(
            "\\bJOIN\\s+([`'\"]?\\w[\\w.]*[`'\"]?)",
            Pattern.CASE_INSENSITIVE);

    // Dynamic variable placeholder
    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{[^}]+\\}");

    /**
     * Parse the given SQL string and return a {@link SqlParseResult}.
     *
     * @param sql raw SQL string (may contain comments, mixed case)
     * @return parse result
     */
    public SqlParseResult parse(String sql) {
        SqlParseResult result = new SqlParseResult();
        if (StringUtils.isBlank(sql)) {
            return result;
        }

        String normalized = stripComments(sql);

        // 1. Collect CTE alias names (they are not real source tables)
        Set<String> cteNames = new HashSet<>();
        Matcher cteMatcher = CTE_PATTERN.matcher(normalized);
        while (cteMatcher.find()) {
            cteNames.add(normalizeIdentifier(cteMatcher.group(1)));
        }

        // 2. Find INSERT targets
        List<String> targets = new ArrayList<>();
        Matcher insertMatcher = INSERT_PATTERN.matcher(normalized);
        while (insertMatcher.find()) {
            String table = normalizeIdentifier(insertMatcher.group(1));
            if (!targets.contains(table)) {
                targets.add(table);
            }
        }

        if (targets.isEmpty()) {
            result.setTargetTable(null);
            result.setHasMultipleTargets(false);
        } else if (targets.size() == 1) {
            result.setTargetTable(targets.get(0));
            result.setHasMultipleTargets(false);
        } else {
            result.setTargetTable(targets.get(0));
            result.setHasMultipleTargets(true);
        }

        // 3. Find FROM / JOIN source tables
        Set<String> sourceTables = new LinkedHashSet<>();
        Set<String> unresolvedRefs = new LinkedHashSet<>();

        collectSources(normalized, FROM_PATTERN, cteNames, sourceTables, unresolvedRefs);
        collectSources(normalized, JOIN_PATTERN, cteNames, sourceTables, unresolvedRefs);

        // 4. Collect dynamic variable references (these appear before stripping)
        Matcher varMatcher = VAR_PATTERN.matcher(sql);
        while (varMatcher.find()) {
            unresolvedRefs.add(varMatcher.group());
        }

        // Remove the target table itself from source tables
        if (result.getTargetTable() != null) {
            sourceTables.remove(result.getTargetTable());
        }
        // Remove any INSERT target from source tables
        sourceTables.removeAll(targets);

        result.setSourceTables(new ArrayList<>(sourceTables));
        result.setUnresolvedRefs(new ArrayList<>(unresolvedRefs));
        return result;
    }

    // -------------------------------------------------------------------------
    // private helpers
    // -------------------------------------------------------------------------

    private void collectSources(String sql, Pattern pattern,
                                Set<String> cteNames,
                                Set<String> sourceTables,
                                Set<String> unresolvedRefs) {
        Matcher matcher = pattern.matcher(sql);
        while (matcher.find()) {
            String raw = matcher.group(1);
            // Skip sub-query starters that begin with '('
            if (raw.startsWith("(")) {
                continue;
            }
            String table = normalizeIdentifier(raw);
            // Skip CTE aliases
            if (cteNames.contains(table)) {
                continue;
            }
            // Skip pure SQL keywords that sometimes follow FROM/JOIN
            if (isSqlKeyword(table)) {
                continue;
            }
            sourceTables.add(table);
        }
    }

    /**
     * Remove single-line ({@code --}) and multi-line ({@code /* … *\/}) comments.
     */
    private String stripComments(String sql) {
        // multi-line comments first
        String s = sql.replaceAll("/\\*[\\s\\S]*?\\*/", " ");
        // single-line comments
        s = s.replaceAll("--[^\\r\\n]*", " ");
        return s;
    }

    /**
     * Lowercase and strip backtick / single-quote / double-quote quoting.
     */
    private String normalizeIdentifier(String identifier) {
        return identifier.replaceAll("[`'\"]", "").toLowerCase().trim();
    }

    /**
     * Rough guard against picking up SQL keywords (SELECT, WHERE, etc.) that
     * can follow FROM in derived-table or LATERAL patterns.
     */
    private boolean isSqlKeyword(String word) {
        switch (word.toUpperCase()) {
            case "SELECT":
            case "WHERE":
            case "GROUP":
            case "ORDER":
            case "HAVING":
            case "LIMIT":
            case "UNION":
            case "INTERSECT":
            case "EXCEPT":
            case "LATERAL":
            case "DUAL":
                return true;
            default:
                return false;
        }
    }
}
