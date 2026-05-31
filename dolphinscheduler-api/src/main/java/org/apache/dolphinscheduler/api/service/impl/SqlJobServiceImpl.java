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

package org.apache.dolphinscheduler.api.service.impl;

import org.apache.dolphinscheduler.api.dto.sqljob.DependencyMatchResult;
import org.apache.dolphinscheduler.api.dto.sqljob.DependencyPreviewResult;
import org.apache.dolphinscheduler.api.dto.sqljob.DeployResult;
import org.apache.dolphinscheduler.api.dto.sqljob.SaveSqlRequest;
import org.apache.dolphinscheduler.api.dto.sqljob.SqlJobDeployRequest;
import org.apache.dolphinscheduler.api.dto.sqljob.SqlParseResult;
import org.apache.dolphinscheduler.api.dto.sqljob.TestRunResult;
import org.apache.dolphinscheduler.api.exceptions.ServiceException;
import org.apache.dolphinscheduler.api.service.DagCompilerService;
import org.apache.dolphinscheduler.api.service.DependencyResolverService;
import org.apache.dolphinscheduler.api.service.SqlJobService;
import org.apache.dolphinscheduler.api.service.SqlParserService;
import org.apache.dolphinscheduler.api.service.WorkflowDefinitionService;
import org.apache.dolphinscheduler.common.enums.WorkflowExecutionTypeEnum;
import org.apache.dolphinscheduler.dao.entity.SqlJob;
import org.apache.dolphinscheduler.dao.entity.User;
import org.apache.dolphinscheduler.dao.entity.WorkflowDefinition;
import org.apache.dolphinscheduler.dao.mapper.SqlJobMapper;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

/**
 * Implementation of {@link SqlJobService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlJobServiceImpl implements SqlJobService {

    private final SqlJobMapper sqlJobMapper;
    private final SqlParserService sqlParserService;
    private final DependencyResolverService dependencyResolverService;
    private final DagCompilerService dagCompilerService;
    private final WorkflowDefinitionService workflowDefinitionService;

    @Override
    public SqlJob create(User loginUser, long projectCode, String name) {
        if (StringUtils.isBlank(name)) {
            throw new ServiceException("SQL job name must not be blank");
        }

        SqlJob job = new SqlJob();
        job.setProjectCode(projectCode);
        job.setJobSlug(slugify(name));
        job.setJobName(name);
        // personal branch will be set by git management layer; provide a default placeholder
        job.setPersonalBranch("user/" + loginUser.getUserName() + "/" + slugify(name));
        job.setOwner(loginUser.getUserName());
        job.setUserId(loginUser.getId());
        job.setDatasourceId(null);
        job.setDatasourceType(null);
        job.setSqlContent("");
        job.setWorkflowCode(null);
        job.setCurrentMasterCommit(null);
        Date now = new Date();
        job.setCreateTime(now);
        job.setUpdateTime(now);

        sqlJobMapper.insert(job);
        return job;
    }

    @Override
    public List<SqlJob> list(long projectCode) {
        return sqlJobMapper.selectByProjectCode(projectCode);
    }

    @Override
    public SqlJob save(long id, SaveSqlRequest request) {
        SqlJob job = requireJobById(id);
        job.setSqlContent(request.getSql());
        if (request.getDatasourceId() != null) {
            job.setDatasourceId(request.getDatasourceId());
        }
        if (StringUtils.isNotBlank(request.getDatasourceType())) {
            job.setDatasourceType(request.getDatasourceType());
        }
        job.setUpdateTime(new Date());
        sqlJobMapper.updateById(job);
        return job;
    }

    @Override
    public TestRunResult testRun(long id, int limitRows) {
        // Phase 1 stub: direct execution is complex (requires datasource connection pool access).
        // Return a not-implemented result so the controller can respond gracefully.
        TestRunResult result = new TestRunResult();
        result.setStatus("FAILED");
        result.setError("Test-run execution is not available in Phase 1. "
                + "Please deploy to a scheduled workflow and use run-once trigger.");
        return result;
    }

    @Override
    public SqlParseResult parseSql(long id) {
        SqlJob job = requireJobById(id);
        if (StringUtils.isBlank(job.getSqlContent())) {
            return new SqlParseResult();
        }
        return sqlParserService.parse(job.getSqlContent());
    }

    @Override
    public DependencyPreviewResult dependencyPreview(long projectCode, long id) {
        SqlJob job = requireJobById(id);
        DependencyPreviewResult preview = new DependencyPreviewResult();

        if (StringUtils.isBlank(job.getSqlContent())) {
            return preview;
        }

        SqlParseResult parseResult = sqlParserService.parse(job.getSqlContent());
        preview.setTargetTable(parseResult.getTargetTable());
        preview.setHasMultipleTargets(parseResult.isHasMultipleTargets());

        List<DependencyMatchResult> newDeps =
                dependencyResolverService.resolve(parseResult.getSourceTables(), projectCode);
        preview.setDependencies(newDeps);
        preview.setHasUnresolved(
                newDeps.stream().anyMatch(d -> "UNRESOLVED".equals(d.getStatus())));

        // Old dependencies: derive from currently linked workflow definition.
        // Phase 1 always returns an empty list (workflow task params parsing deferred to Phase 2).
        preview.setOldDependencies(new ArrayList<>());

        return preview;
    }

    @Override
    public DeployResult deploy(User loginUser, long projectCode, long id,
                               SqlJobDeployRequest request) {
        SqlJob job = requireJobById(id);

        if (StringUtils.isBlank(job.getSqlContent())) {
            throw new ServiceException("Cannot deploy: SQL content is empty. Please save SQL first.");
        }

        // 1. Parse SQL
        SqlParseResult parseResult = sqlParserService.parse(job.getSqlContent());

        // 2. Block on multiple target tables
        if (parseResult.isHasMultipleTargets()) {
            throw new ServiceException(
                    "Multiple target tables are not supported in Phase 1. "
                            + "Please use a single INSERT statement.");
        }

        // 3. Detect circular dependencies
        Optional<String> cycleOpt = dependencyResolverService.detectCycles(
                request.getConfirmedDependencies(),
                parseResult.getTargetTable());
        if (cycleOpt.isPresent()) {
            throw new ServiceException(
                    "Circular dependency detected: " + cycleOpt.get());
        }

        // 4. Compile DAG
        int datasourceId = job.getDatasourceId() != null ? job.getDatasourceId() : 0;
        String datasourceType = StringUtils.defaultString(job.getDatasourceType(), "");

        DagCompilerService.CompileResult compiled = dagCompilerService.compile(
                job.getJobName(),
                job.getSqlContent(),
                datasourceId,
                datasourceType,
                request.getConfirmedDependencies());

        // 5. Create or update workflow definition
        String workflowName = "sql_job_" + job.getJobSlug();
        WorkflowDefinition workflowDef;

        if (job.getWorkflowCode() == null) {
            // First deploy: create a new workflow definition
            workflowDef = workflowDefinitionService.createWorkflowDefinition(
                    loginUser,
                    projectCode,
                    workflowName,
                    "Auto-generated by SQL-first job editor (job id=" + id + ")",
                    "[]", // globalParams
                    compiled.getLocations(),
                    0, // timeout
                    compiled.getTaskRelationJson(),
                    compiled.getTaskDefinitionJson(),
                    null, // otherParamsJson
                    WorkflowExecutionTypeEnum.PARALLEL);
        } else {
            // Subsequent deploy: offline → update → (re-online below)
            try {
                workflowDefinitionService.offlineWorkflowDefinition(
                        loginUser, projectCode, job.getWorkflowCode());
            } catch (Exception e) {
                log.warn("Failed to offline workflow {} before update (may already be offline): {}",
                        job.getWorkflowCode(), e.getMessage());
            }
            workflowDef = workflowDefinitionService.updateWorkflowDefinition(
                    loginUser,
                    projectCode,
                    workflowName,
                    job.getWorkflowCode(),
                    "Auto-generated by SQL-first job editor (job id=" + id + ")",
                    "[]",
                    compiled.getLocations(),
                    0,
                    compiled.getTaskRelationJson(),
                    compiled.getTaskDefinitionJson(),
                    WorkflowExecutionTypeEnum.PARALLEL);
        }

        // 6. Bring workflow ONLINE
        workflowDefinitionService.onlineWorkflowDefinition(
                loginUser, projectCode, workflowDef.getCode());

        // 7. Update SqlJob record with the linked workflow code
        job.setWorkflowCode(workflowDef.getCode());
        job.setUpdateTime(new Date());
        sqlJobMapper.updateById(job);

        // 8. Build result
        DeployResult result = new DeployResult();
        result.setWorkflowCode(workflowDef.getCode());
        result.setWorkflowName(workflowName);
        result.setReleaseState("ONLINE");
        result.setInstanceUrl("/projects/" + projectCode + "/workflow/instances");
        return result;
    }

    // -------------------------------------------------------------------------
    // private helpers
    // -------------------------------------------------------------------------

    private SqlJob requireJobById(long id) {
        SqlJob job = sqlJobMapper.selectById(id);
        if (job == null) {
            throw new ServiceException("SQL job not found: id=" + id);
        }
        return job;
    }

    /**
     * Convert a display name to a URL-safe slug (lowercase, hyphens, no special chars).
     */
    private String slugify(String name) {
        if (name == null) {
            return "unnamed";
        }
        return name.trim()
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }
}
