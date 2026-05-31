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
 * Result of the dependency-preview endpoint.
 * Combines parsed target table, new dependency matches, and diff against old dependencies.
 */
@Data
public class DependencyPreviewResult {

    /**
     * The table written to by the SQL.
     */
    private String targetTable;

    /**
     * New dependency matches based on current SQL.
     */
    private List<DependencyMatchResult> dependencies = new ArrayList<>();

    /**
     * Dependencies from the previously deployed workflow (may be empty before first deploy).
     */
    private List<DependencyMatchResult> oldDependencies = new ArrayList<>();

    /**
     * True when any new dependency could not be resolved.
     */
    private boolean hasUnresolved;

    /**
     * True when the SQL writes to multiple tables (deploy will be blocked).
     */
    private boolean hasMultipleTargets;
}
