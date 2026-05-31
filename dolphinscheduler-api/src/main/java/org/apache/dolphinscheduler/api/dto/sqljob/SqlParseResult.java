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

package org.apache.dolphinscheduler.api.dto.sqljob;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * Result of parsing a SQL string.
 * Identifies target table (INSERT INTO/OVERWRITE), source tables (FROM/JOIN),
 * and any unresolved dynamic variable references.
 */
@Data
public class SqlParseResult {

    /**
     * The table written to by the SQL; null for SELECT-only statements.
     */
    private String targetTable;

    /**
     * Tables read by the SQL (FROM/JOIN), excluding CTE aliases and the target table.
     */
    private List<String> sourceTables = new ArrayList<>();

    /**
     * Table name expressions that could not be statically resolved
     * (e.g. {@code ${tableName}} placeholders).
     */
    private List<String> unresolvedRefs = new ArrayList<>();

    /**
     * True when the SQL contains multiple INSERT statements targeting different tables.
     * Phase 1 blocks deploy in this case.
     */
    private boolean hasMultipleTargets;
}
