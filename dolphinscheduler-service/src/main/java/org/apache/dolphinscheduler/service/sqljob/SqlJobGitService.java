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

import java.util.List;

/**
 * Service interface for Git-backed SQL job version management.
 *
 * <p>All Git operations are executed server-side via JGit; the front-end
 * interacts only through the REST API layer and never accesses the repository
 * directly.
 *
 * <p>Concurrency contract:
 * <ul>
 *   <li>Concurrent {@link #save} calls on the same {@code jobKey} are
 *       serialised by {@code SqlJobLockManager}.</li>
 *   <li>{@link #deploy} holds a global master write-lock for the duration of
 *       the squash-merge to keep master history linear.</li>
 *   <li>Read operations ({@link #commits}, {@link #diff}, {@link #branchStatus})
 *       are lock-free; JGit's read path is thread-safe.</li>
 * </ul>
 */
public interface SqlJobGitService {

    /**
     * Ensure the bare repository exists and has an initial master commit.
     * Safe to call multiple times; subsequent calls are no-ops when the
     * repository is already in a valid state.
     */
    void ensureRepoInitialized();

    /**
     * Write {@code content} to the personal branch for the given job and
     * create a commit.
     *
     * <p>If the personal branch does not yet exist it will be forked from
     * {@code master}. If {@code master} does not yet exist the first commit
     * will initialise it as well.
     *
     * @param jobKey   composite key identifying the job, e.g.
     *                 {@code "<projectCode>/<jobSlug>"}
     * @param content  SQL source text to store
     * @param username commit author (the currently logged-in user)
     * @param message  commit message; callers should append {@code [AI-assisted]}
     *                 when the content was AI-generated
     * @return the full 40-character SHA of the newly created commit
     */
    String save(String jobKey, String content, String username, String message);

    /**
     * List the commit history for a job on the specified branch.
     *
     * @param jobKey branch to read history from (personal branch name)
     * @param branch the branch ref to walk, e.g. {@code user/alice/order-etl}
     * @param limit  maximum number of commits to return (capped at 50 in the
     *               REST layer)
     * @return ordered list of commits, newest first
     */
    List<CommitDto> commits(String jobKey, String branch, int limit);

    /**
     * Return the unified diff between two commits for the given job's SQL file.
     *
     * @param jobKey     composite job key
     * @param baseCommit base commit SHA or branch ref (e.g. {@code master})
     * @param headCommit head commit SHA or branch ref (e.g. the personal branch)
     * @return unified diff string; empty string if content is identical
     */
    String diff(String jobKey, String baseCommit, String headCommit);

    /**
     * Squash-merge the personal branch into {@code master} and write a
     * publish-log entry.
     *
     * <p>When {@code force} is {@code false} and the personal branch is behind
     * master the method throws {@link BehindMasterException} with ahead/behind
     * counters so the caller can surface a rebase suggestion to the user.
     *
     * @param jobKey   composite job key
     * @param force    if {@code true}, proceed even when the personal branch is
     *                 behind master (Phase 1: warns but does not abort)
     * @param username deploying user; written into the merge commit author and
     *                 the publish-log
     * @return the full SHA of the new master commit created by the squash merge
     */
    String deploy(String jobKey, boolean force, String username);

    /**
     * Create a revert commit on {@code master} that undoes the changes
     * introduced between {@code toCommit} and its parent.
     *
     * @param jobKey   composite job key
     * @param toCommit the SHA to revert to (the commit to un-do)
     * @param username user performing the revert
     * @return the full SHA of the new revert commit on master
     */
    String revert(String jobKey, String toCommit, String username);

    /**
     * Compute the ahead/behind relationship between the personal branch and
     * {@code master}.
     *
     * @param jobKey composite job key
     * @param branch personal branch ref name
     * @return ahead/behind status DTO; both counters are 0 when the branches
     *         are at the same commit
     */
    BranchStatusDto branchStatus(String jobKey, String branch);

    /**
     * Read the raw SQL content of a specific commit.
     *
     * <p>Used by the Worker-side RPC path so workers can fetch the pinned SQL
     * without direct repository access.
     *
     * @param jobKey     composite job key
     * @param commitHash full or abbreviated commit SHA
     * @return SQL source text at that commit, never {@code null}
     * @throws IllegalArgumentException if the commit or file does not exist
     */
    String readSqlAtCommit(String jobKey, String commitHash);

    /**
     * Exception thrown by {@link #deploy} when the personal branch is behind
     * master and {@code force} is {@code false}.
     */
    class BehindMasterException extends RuntimeException {

        private final int ahead;
        private final int behind;

        public BehindMasterException(int ahead, int behind) {
            super(String.format(
                    "Personal branch is %d commit(s) behind master (ahead=%d). Rebase first or use force=true.",
                    behind, ahead));
            this.ahead = ahead;
            this.behind = behind;
        }

        public int getAhead() {
            return ahead;
        }

        public int getBehind() {
            return behind;
        }
    }
}
