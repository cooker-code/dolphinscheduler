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

package org.apache.dolphinscheduler.service.sqljob;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the SQL Job Git management feature.
 *
 * <p>Bound from {@code sql-job.git.*} in {@code application.yaml}.
 *
 * <p>Example configuration:
 * <pre>
 * sql-job:
 *   git:
 *     enabled: false
 *     repo-path: /data/dolphinscheduler/sql-jobs.git
 *     author-email-domain: dolphinscheduler.local
 *     max-commits-per-job: 1000
 * </pre>
 *
 * <p>Note: when running multiple API nodes, writes must be routed to a single node
 * (sticky session or leader election). This is a Phase 1 constraint.
 */
@Data
@Component
@ConfigurationProperties(prefix = "sql-job.git")
public class SqlJobGitConfig {

    /**
     * Feature flag. Set to {@code true} to enable Git-backed SQL job management.
     * Defaults to {@code false} so existing workflows are not affected.
     */
    private boolean enabled = false;

    /**
     * Filesystem path of the bare Git repository used to store SQL job sources.
     * The directory will be auto-initialised on first use if it does not exist.
     */
    private String repoPath = "${user.home}/.ds-sql-jobs/repo";

    /**
     * Email domain appended to commit author names, e.g.
     * author {@code alice} becomes {@code alice@dolphinscheduler.local}.
     */
    private String authorEmailDomain = "dolphinscheduler.local";

    /**
     * Soft upper bound on the number of commits retained per job.
     * Enforcement (shallow grafting) is deferred to Phase 2.
     */
    private int maxCommitsPerJob = 1000;
}
