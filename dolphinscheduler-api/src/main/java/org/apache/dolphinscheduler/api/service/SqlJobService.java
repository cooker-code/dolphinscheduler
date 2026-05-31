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

import org.apache.dolphinscheduler.api.dto.sqljob.DependencyPreviewResult;
import org.apache.dolphinscheduler.api.dto.sqljob.DeployResult;
import org.apache.dolphinscheduler.api.dto.sqljob.SaveSqlRequest;
import org.apache.dolphinscheduler.api.dto.sqljob.SqlJobDeployRequest;
import org.apache.dolphinscheduler.api.dto.sqljob.SqlParseResult;
import org.apache.dolphinscheduler.api.dto.sqljob.TestRunResult;
import org.apache.dolphinscheduler.dao.entity.SqlJob;
import org.apache.dolphinscheduler.dao.entity.User;

import java.util.List;

/**
 * Service interface for SQL-first job management.
 */
public interface SqlJobService {

    /**
     * Create a new SQL job entry (empty SQL, no workflow yet).
     *
     * @param loginUser   creator
     * @param projectCode project code
     * @param name        display name
     * @return created SqlJob
     */
    SqlJob create(User loginUser, long projectCode, String name);

    /**
     * List all SQL jobs for a project.
     *
     * @param projectCode project code
     * @return list of SqlJob
     */
    List<SqlJob> list(long projectCode);

    /**
     * Save SQL content and datasource info to an existing job.
     *
     * @param id      job id
     * @param request save request containing sql and datasource info
     * @return updated SqlJob
     */
    SqlJob save(long id, SaveSqlRequest request);

    /**
     * Execute the SQL in a sandboxed query mode (LIMIT rows).
     *
     * @param id        job id
     * @param limitRows maximum rows to return
     * @return test run result
     */
    TestRunResult testRun(long id, int limitRows);

    /**
     * Parse the SQL of a job and return structural information.
     *
     * @param id job id
     * @return parse result
     */
    SqlParseResult parseSql(long id);

    /**
     * Preview new dependency matches versus previously deployed dependencies.
     *
     * @param projectCode project code
     * @param id          job id
     * @return dependency preview result
     */
    DependencyPreviewResult dependencyPreview(long projectCode, long id);

    /**
     * Deploy the SQL job: create or update the linked DolphinScheduler workflow
     * and bring it ONLINE.
     *
     * @param loginUser   operator
     * @param projectCode project code
     * @param id          job id
     * @param request     deploy parameters
     * @return deploy result
     */
    DeployResult deploy(User loginUser, long projectCode, long id, SqlJobDeployRequest request);
}
