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

import lombok.Data;

/**
 * Mapping result between a source table and an existing DolphinScheduler task definition.
 */
@Data
public class DependencyMatchResult {

    /**
     * The source table name being resolved.
     */
    private String table;

    /**
     * Task definition code that produces this table; null if unresolved.
     */
    private Long matchedTaskCode;

    /**
     * Workflow definition code that contains the matched task; null if unresolved.
     * (Populated in Phase 2; may be null in Phase 1.)
     */
    private Long matchedWorkflowCode;

    /**
     * Resolution status: "MATCHED" or "UNRESOLVED".
     */
    private String status;
}
