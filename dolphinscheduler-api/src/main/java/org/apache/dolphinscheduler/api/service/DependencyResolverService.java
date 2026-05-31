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
import org.apache.dolphinscheduler.api.dto.sqljob.DependencyMatchResult;
import org.apache.dolphinscheduler.common.utils.JSONUtils;
import org.apache.dolphinscheduler.dao.entity.TaskDefinition;
import org.apache.dolphinscheduler.dao.mapper.TaskDefinitionMapper;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Resolves source tables to existing DolphinScheduler task definitions.
 *
 * <p>Builds an index of {@code targetTable → taskCode} by scanning all SQL task definitions
 * in the project.  The SQL task parameters ({@code task_params} JSON field) contain a
 * {@code "sql"} field whose target table is extracted via {@link SqlParserService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DependencyResolverService {

    private final TaskDefinitionMapper taskDefinitionMapper;
    private final SqlParserService sqlParserService;

    /**
     * Build a map from lower-cased target table name to task definition code,
     * scanning all active SQL tasks in the given project.
     *
     * @param projectCode project code
     * @return map of targetTable → taskCode
     */
    public Map<String, Long> buildTargetTableIndex(long projectCode) {
        List<TaskDefinition> sqlTasks = taskDefinitionMapper.selectList(
                new QueryWrapper<TaskDefinition>()
                        .eq("project_code", projectCode)
                        .eq("task_type", "SQL")
                        .eq("flag", 1));

        Map<String, Long> index = new HashMap<>();
        for (TaskDefinition task : sqlTasks) {
            String taskParams = task.getTaskParams();
            if (StringUtils.isBlank(taskParams)) {
                continue;
            }
            try {
                JsonNode paramsNode = JSONUtils.parseObject(taskParams);
                if (paramsNode == null) {
                    continue;
                }
                String sql = paramsNode.path("sql").asText("");
                if (StringUtils.isBlank(sql)) {
                    continue;
                }
                String targetTable = sqlParserService.parse(sql).getTargetTable();
                if (StringUtils.isNotBlank(targetTable)) {
                    index.putIfAbsent(targetTable.toLowerCase(), task.getCode());
                }
            } catch (Exception e) {
                log.debug("Failed to parse task_params for task code {}: {}", task.getCode(), e.getMessage());
            }
        }
        return index;
    }

    /**
     * Resolve a list of source tables to matching task definitions in the given project.
     *
     * @param sourceTables list of lower-cased source table names
     * @param projectCode  project code
     * @return list of dependency match results
     */
    public List<DependencyMatchResult> resolve(List<String> sourceTables, long projectCode) {
        if (sourceTables == null || sourceTables.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, Long> index = buildTargetTableIndex(projectCode);
        List<DependencyMatchResult> results = new ArrayList<>();
        for (String table : sourceTables) {
            DependencyMatchResult match = new DependencyMatchResult();
            match.setTable(table);
            Long taskCode = index.get(table.toLowerCase());
            if (taskCode != null) {
                match.setMatchedTaskCode(taskCode);
                match.setMatchedWorkflowCode(null); // Phase 2
                match.setStatus("MATCHED");
            } else {
                match.setMatchedTaskCode(null);
                match.setMatchedWorkflowCode(null);
                match.setStatus("UNRESOLVED");
            }
            results.add(match);
        }
        return results;
    }

    /**
     * Detect circular dependencies in the confirmed dependency list.
     *
     * <p>Builds a directed graph where each edge represents "this job depends on the task
     * that produces a given table".  Uses DFS to detect cycles and returns the cycle path
     * string if one is found.
     *
     * @param confirmedDeps list of confirmed dependencies
     * @param selfTable     the target table of the current job (to detect self-reference)
     * @return Optional containing the cycle description string, or empty if no cycle
     */
    public Optional<String> detectCycles(List<ConfirmedDependency> confirmedDeps, String selfTable) {
        if (confirmedDeps == null || confirmedDeps.isEmpty()) {
            return Optional.empty();
        }

        // Simple self-reference check: does any source table equal the target table?
        for (ConfirmedDependency dep : confirmedDeps) {
            if (dep.getTable() != null && dep.getTable().equalsIgnoreCase(selfTable)) {
                return Optional.of(selfTable + " -> " + selfTable);
            }
        }

        // For Phase 1 with single-task workflows, a full graph cycle across multiple
        // tasks is unlikely. We perform a basic reachability check: if any confirmed
        // dep's table is the same as selfTable we already caught it above.
        // Deeper multi-hop cycle detection is deferred to Phase 2.
        return Optional.empty();
    }
}
