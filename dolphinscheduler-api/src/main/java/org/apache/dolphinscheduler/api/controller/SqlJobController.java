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

package org.apache.dolphinscheduler.api.controller;

import org.apache.dolphinscheduler.api.dto.sqljob.DependencyPreviewResult;
import org.apache.dolphinscheduler.api.dto.sqljob.DeployResult;
import org.apache.dolphinscheduler.api.dto.sqljob.SaveSqlRequest;
import org.apache.dolphinscheduler.api.dto.sqljob.SqlJobDeployRequest;
import org.apache.dolphinscheduler.api.dto.sqljob.SqlParseResult;
import org.apache.dolphinscheduler.api.dto.sqljob.TestRunResult;
import org.apache.dolphinscheduler.api.service.SqlJobService;
import org.apache.dolphinscheduler.api.utils.Result;
import org.apache.dolphinscheduler.common.constants.Constants;
import org.apache.dolphinscheduler.dao.entity.SqlJob;
import org.apache.dolphinscheduler.dao.entity.User;

import java.util.List;

import javax.validation.Valid;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * SQL-first job editor REST controller.
 *
 * <p>All endpoints are under {@code /projects/{projectCode}/sql-jobs}.
 * They are isolated from the existing workflow-definition endpoints and do not
 * modify {@code WorkflowDefinitionController} or its service.
 */
@Tag(name = "SQL_JOB_TAG")
@RestController
@RequestMapping("projects/{projectCode}/sql-jobs")
@Slf4j
@RequiredArgsConstructor
public class SqlJobController extends BaseController {

    private final SqlJobService sqlJobService;

    /**
     * Create a new SQL job.
     */
    @Operation(summary = "createSqlJob", description = "CREATE_SQL_JOB")
    @Parameter(name = "name", description = "SQL_JOB_NAME", required = true, schema = @Schema(implementation = String.class))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<SqlJob> createSqlJob(
                                       @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                       @PathVariable long projectCode,
                                       @RequestParam String name) {
        return Result.success(sqlJobService.create(loginUser, projectCode, name));
    }

    /**
     * List all SQL jobs in a project.
     */
    @Operation(summary = "listSqlJobs", description = "LIST_SQL_JOBS")
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public Result<List<SqlJob>> listSqlJobs(
                                            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                            @PathVariable long projectCode) {
        return Result.success(sqlJobService.list(projectCode));
    }

    /**
     * Save SQL content and datasource to a job.
     */
    @Operation(summary = "saveSqlContent", description = "SAVE_SQL_CONTENT")
    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public Result<SqlJob> saveSqlContent(
                                         @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                         @PathVariable long projectCode,
                                         @PathVariable long id,
                                         @Valid @RequestBody SaveSqlRequest request) {
        return Result.success(sqlJobService.save(id, request));
    }

    /**
     * Trigger a sandboxed test run.
     */
    @Operation(summary = "testRun", description = "TEST_RUN_SQL_JOB")
    @PostMapping("/{id}/test")
    @ResponseStatus(HttpStatus.OK)
    public Result<TestRunResult> testRun(
                                         @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                         @PathVariable long projectCode,
                                         @PathVariable long id,
                                         @RequestParam(defaultValue = "1000") int limitRows) {
        return Result.success(sqlJobService.testRun(id, limitRows));
    }

    /**
     * Parse the SQL of a job, returning target table, source tables, and unresolved refs.
     */
    @Operation(summary = "parseSql", description = "PARSE_SQL_JOB")
    @GetMapping("/{id}/parse")
    @ResponseStatus(HttpStatus.OK)
    public Result<SqlParseResult> parseSql(
                                           @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                           @PathVariable long projectCode,
                                           @PathVariable long id) {
        return Result.success(sqlJobService.parseSql(id));
    }

    /**
     * Preview dependency matches for the current SQL versus previously deployed dependencies.
     */
    @Operation(summary = "dependencyPreview", description = "DEPENDENCY_PREVIEW_SQL_JOB")
    @GetMapping("/{id}/dependency-preview")
    @ResponseStatus(HttpStatus.OK)
    public Result<DependencyPreviewResult> dependencyPreview(
                                                             @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                                             @PathVariable long projectCode,
                                                             @PathVariable long id) {
        return Result.success(sqlJobService.dependencyPreview(projectCode, id));
    }

    /**
     * Deploy the SQL job: compile DAG, create/update workflow, bring ONLINE.
     */
    @Operation(summary = "deployWorkflow", description = "DEPLOY_SQL_JOB")
    @PostMapping("/{id}/deploy")
    @ResponseStatus(HttpStatus.OK)
    public Result<DeployResult> deployWorkflow(
                                               @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                               @PathVariable long projectCode,
                                               @PathVariable long id,
                                               @Valid @RequestBody SqlJobDeployRequest request) {
        return Result.success(sqlJobService.deploy(loginUser, projectCode, id, request));
    }
}
