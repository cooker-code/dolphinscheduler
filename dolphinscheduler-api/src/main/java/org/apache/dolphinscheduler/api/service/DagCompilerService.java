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

import org.apache.dolphinscheduler.api.dto.sqljob.ConfirmedDependency;
import org.apache.dolphinscheduler.common.utils.CodeGenerateUtils;
import org.apache.dolphinscheduler.common.utils.JSONUtils;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Compiles SQL job parameters into DolphinScheduler workflow definition JSON fragments.
 *
 * <p>Produces:
 * <ul>
 *   <li>{@code taskDefinitionJson} — a single SQL task node</li>
 *   <li>{@code taskRelationJson} — single-node relation (no predecessors)</li>
 *   <li>{@code locations} — default coordinate for the single task node</li>
 * </ul>
 *
 * <p>These fragments are passed directly to
 * {@link WorkflowDefinitionService#createWorkflowDefinition} /
 * {@link WorkflowDefinitionService#updateWorkflowDefinition}.
 */
@Slf4j
@Service
public class DagCompilerService {

    /**
     * Immutable compile result holding the three JSON strings required by
     * WorkflowDefinitionService.
     */
    public static class CompileResult {

        private final String taskDefinitionJson;
        private final String taskRelationJson;
        private final String locations;
        private final long taskCode;

        public CompileResult(String taskDefinitionJson, String taskRelationJson,
                             String locations, long taskCode) {
            this.taskDefinitionJson = taskDefinitionJson;
            this.taskRelationJson = taskRelationJson;
            this.locations = locations;
            this.taskCode = taskCode;
        }

        public String getTaskDefinitionJson() {
            return taskDefinitionJson;
        }

        public String getTaskRelationJson() {
            return taskRelationJson;
        }

        public String getLocations() {
            return locations;
        }

        public long getTaskCode() {
            return taskCode;
        }
    }

    /**
     * Compile a single SQL task workflow definition from the given parameters.
     *
     * @param jobName              display name of the SQL job (used to derive the task name)
     * @param sql                  SQL content
     * @param datasourceId         bound datasource id
     * @param datasourceType       datasource type string, e.g. "HIVE"
     * @param confirmedDependencies user-confirmed dependency list (informational in Phase 1)
     * @return compiled JSON fragments
     */
    public CompileResult compile(String jobName,
                                 String sql,
                                 int datasourceId,
                                 String datasourceType,
                                 List<ConfirmedDependency> confirmedDependencies) {
        long taskCode = CodeGenerateUtils.genCode();
        String taskName = "sql_task_" + sanitizeName(jobName);

        // ── task params (SqlParameters shape) ──────────────────────────────
        ObjectNode taskParamsNode = JSONUtils.createObjectNode();
        taskParamsNode.put("type", datasourceType == null ? "" : datasourceType);
        taskParamsNode.put("datasource", datasourceId);
        taskParamsNode.put("sql", sql == null ? "" : sql);
        taskParamsNode.put("sqlType", 1); // 1 = NON_QUERY
        taskParamsNode.put("limit", 1000);
        taskParamsNode.putArray("preStatements");
        taskParamsNode.putArray("postStatements");

        // ── task definition ────────────────────────────────────────────────
        ObjectNode taskDefNode = JSONUtils.createObjectNode();
        taskDefNode.put("code", taskCode);
        taskDefNode.put("name", taskName);
        taskDefNode.put("taskType", "SQL");
        taskDefNode.set("taskParams", taskParamsNode);
        taskDefNode.put("flag", "YES");
        taskDefNode.put("taskPriority", "MEDIUM");
        taskDefNode.put("workerGroup", "default");
        taskDefNode.put("failRetryTimes", 0);
        taskDefNode.put("timeout", 0);
        taskDefNode.put("timeoutFlag", "CLOSE");
        taskDefNode.put("timeoutNotifyStrategy", "WARN");
        taskDefNode.put("taskGroupId", 0);
        taskDefNode.put("taskGroupPriority", 0);
        taskDefNode.put("delayTime", 0);
        taskDefNode.put("cpuQuota", -1);
        taskDefNode.put("memoryMax", -1);
        taskDefNode.put("description", "");
        taskDefNode.put("environmentCode", -1L);

        ArrayNode taskDefArray = JSONUtils.createArrayNode();
        taskDefArray.add(taskDefNode);

        // ── task relation (single node, no predecessor) ───────────────────
        ObjectNode relationNode = JSONUtils.createObjectNode();
        relationNode.put("name", "");
        relationNode.put("preTaskCode", 0);
        relationNode.put("preTaskVersion", 0);
        relationNode.put("postTaskCode", taskCode);
        relationNode.put("postTaskVersion", 1);
        relationNode.put("conditionType", "AND");
        relationNode.set("conditionParams", JSONUtils.createObjectNode());

        ArrayNode relationArray = JSONUtils.createArrayNode();
        relationArray.add(relationNode);

        // ── location (single node at default position) ─────────────────────
        ObjectNode locationNode = JSONUtils.createObjectNode();
        locationNode.put("taskCode", taskCode);
        locationNode.put("x", 100);
        locationNode.put("y", 100);

        ArrayNode locationArray = JSONUtils.createArrayNode();
        locationArray.add(locationNode);

        return new CompileResult(
                JSONUtils.toJsonString(taskDefArray),
                JSONUtils.toJsonString(relationArray),
                JSONUtils.toJsonString(locationArray),
                taskCode);
    }

    private String sanitizeName(String name) {
        if (name == null) {
            return "unnamed";
        }
        return name.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
    }
}
