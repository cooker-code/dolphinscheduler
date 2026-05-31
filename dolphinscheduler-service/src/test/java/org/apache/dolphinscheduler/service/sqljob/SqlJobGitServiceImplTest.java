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

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link SqlJobGitServiceImpl}.
 *
 * <p>Uses a temporary directory as the Git repository root so tests are
 * completely isolated from any real repository and from each other.
 *
 * <p>Tests cover the full save → diff → deploy → revert lifecycle as well as
 * the concurrent lock and the {@link SqlJobGitService.BehindMasterException}
 * guard on {@code deploy()}.
 */
class SqlJobGitServiceImplTest {

    @TempDir
    File tempDir;

    private SqlJobGitServiceImpl service;
    private static final String JOB_KEY = "project1/order-etl";
    private static final String USER = "alice";

    @BeforeEach
    void setUp() throws Exception {
        SqlJobGitConfig config = new SqlJobGitConfig();
        config.setRepoPath(tempDir.getAbsolutePath());
        config.setAuthorEmailDomain("test.local");
        config.setMaxCommitsPerJob(1000);

        SqlJobLockManager lockManager = new SqlJobLockManager();
        service = new SqlJobGitServiceImpl(config, lockManager);

        // Invoke @PostConstruct manually (Spring not present in unit tests).
        service.init();
    }

    @AfterEach
    void tearDown() throws Exception {
        // Close the JGit repository to release file handles, avoiding temp-dir
        // cleanup failures on Windows.
        Field repoField = SqlJobGitServiceImpl.class.getDeclaredField("repository");
        repoField.setAccessible(true);
        Object repo = repoField.get(service);
        if (repo instanceof org.eclipse.jgit.lib.Repository) {
            ((org.eclipse.jgit.lib.Repository) repo).close();
        }
    }

    // -------------------------------------------------------------------------
    // ensureRepoInitialized
    // -------------------------------------------------------------------------

    @Test
    void ensureRepoInitialized_createsGitDir() {
        File gitDir = new File(tempDir, ".git");
        Assertions.assertTrue(gitDir.exists(), ".git directory should be created after init");
        Assertions.assertTrue(gitDir.isDirectory());
    }

    @Test
    void ensureRepoInitialized_idempotent() {
        // Calling a second time must not throw.
        Assertions.assertDoesNotThrow(() -> service.ensureRepoInitialized());
    }

    // -------------------------------------------------------------------------
    // save
    // -------------------------------------------------------------------------

    @Test
    void save_returnsCommitHash() {
        String hash = service.save(JOB_KEY, "SELECT 1", USER, null);
        Assertions.assertNotNull(hash);
        Assertions.assertEquals(40, hash.length(), "Commit hash should be 40 chars");
    }

    @Test
    void save_writesContentToPersonalBranch() {
        service.save(JOB_KEY, "SELECT 1 FROM orders", USER, "test save");
        String content = service.readSqlAtCommit(JOB_KEY,
                latestCommitHash("user/" + USER + "/" + JOB_KEY));
        Assertions.assertEquals("SELECT 1 FROM orders", content);
    }

    @Test
    void save_multipleTimes_addsCommits() {
        service.save(JOB_KEY, "SELECT 1", USER, "first");
        service.save(JOB_KEY, "SELECT 2", USER, "second");
        service.save(JOB_KEY, "SELECT 3", USER, "third");

        List<CommitDto> history = service.commits(JOB_KEY,
                "user/" + USER + "/" + JOB_KEY, 50);
        // Expect 3 saves + the branch-creation fork from the initial master commit.
        Assertions.assertTrue(history.size() >= 3,
                "Expected at least 3 commits, got " + history.size());
    }

    @Test
    void save_withAiMessage_preservesMarker() {
        String hash = service.save(JOB_KEY, "SELECT * FROM t", USER,
                "Generate ETL query [AI-assisted]");
        List<CommitDto> history = service.commits(JOB_KEY,
                "user/" + USER + "/" + JOB_KEY, 1);
        Assertions.assertFalse(history.isEmpty());
        Assertions.assertTrue(history.get(0).getMessage().contains("[AI-assisted]"));
    }

    // -------------------------------------------------------------------------
    // commits
    // -------------------------------------------------------------------------

    @Test
    void commits_emptyWhenBranchNotFound() {
        List<CommitDto> result = service.commits(JOB_KEY, "user/nobody/no-job", 50);
        Assertions.assertTrue(result.isEmpty());
    }

    @Test
    void commits_respectsLimit() {
        for (int i = 0; i < 10; i++) {
            service.save(JOB_KEY, "SELECT " + i, USER, "commit " + i);
        }
        List<CommitDto> result = service.commits(JOB_KEY,
                "user/" + USER + "/" + JOB_KEY, 5);
        Assertions.assertEquals(5, result.size());
    }

    @Test
    void commits_newestFirst() {
        service.save(JOB_KEY, "SELECT 1", USER, "first");
        service.save(JOB_KEY, "SELECT 2", USER, "second");

        List<CommitDto> result = service.commits(JOB_KEY,
                "user/" + USER + "/" + JOB_KEY, 10);
        Assertions.assertFalse(result.isEmpty());
        // Newest commit should appear first.
        Assertions.assertTrue(result.get(0).getMessage().contains("second") ||
                !result.get(0).getCommitTime().before(result.get(result.size() - 1).getCommitTime()));
    }

    // -------------------------------------------------------------------------
    // diff
    // -------------------------------------------------------------------------

    @Test
    void diff_detectsContentChange() {
        String hash1 = service.save(JOB_KEY, "SELECT 1", USER, "v1");
        String hash2 = service.save(JOB_KEY, "SELECT 2", USER, "v2");

        String diffOutput = service.diff(JOB_KEY, hash1, hash2);
        Assertions.assertFalse(diffOutput.isEmpty(), "Diff should not be empty when content changed");
        Assertions.assertTrue(diffOutput.contains("SELECT 2"),
                "Diff should mention the new content");
    }

    @Test
    void diff_emptyWhenSameCommit() {
        String hash = service.save(JOB_KEY, "SELECT 1", USER, "v1");
        String diffOutput = service.diff(JOB_KEY, hash, hash);
        Assertions.assertTrue(diffOutput.isEmpty(), "Diff against self should be empty");
    }

    @Test
    void diff_unknownRefReturnsEmpty() {
        String diffOutput = service.diff(JOB_KEY, "deadbeef00000000000000000000000000000000",
                "cafebabe00000000000000000000000000000000");
        Assertions.assertTrue(diffOutput.isEmpty());
    }

    // -------------------------------------------------------------------------
    // deploy
    // -------------------------------------------------------------------------

    @Test
    void deploy_returnsMasterCommitHash() {
        service.save(JOB_KEY, "SELECT 1", USER, "initial");
        String deployHash = service.deploy(JOB_KEY, false, USER);
        Assertions.assertNotNull(deployHash);
        Assertions.assertEquals(40, deployHash.length());
    }

    @Test
    void deploy_sqlContentReachableFromMaster() {
        service.save(JOB_KEY, "SELECT 42 -- deployed", USER, "prepare deploy");
        String deployHash = service.deploy(JOB_KEY, false, USER);

        String content = service.readSqlAtCommit(JOB_KEY, deployHash);
        Assertions.assertEquals("SELECT 42 -- deployed", content);
    }

    @Test
    void deploy_behindMaster_withForceFalse_throwsBehindMasterException() throws Exception {
        // 1. Alice saves and deploys first version.
        service.save(JOB_KEY, "SELECT 1", USER, "v1");
        service.deploy(JOB_KEY, false, USER);

        // 2. Simulate master moving ahead: bob deploys a change to the same job.
        // We do this by saving with bob and deploying from bob's branch.
        String bob = "bob";
        service.save(JOB_KEY, "SELECT 99 -- bob", bob, "bob's change");
        service.deploy(JOB_KEY, false, bob);

        // 3. Now save something new on Alice's personal branch (without rebasing).
        // Alice's branch is now behind master (bob's deploy advanced master).
        service.save(JOB_KEY, "SELECT 2 -- alice new", USER, "alice new");

        // force=false should raise BehindMasterException.
        Assertions.assertThrows(SqlJobGitService.BehindMasterException.class,
                () -> service.deploy(JOB_KEY, false, USER));
    }

    @Test
    void deploy_behindMaster_withForceTrue_succeeds() {
        // Use a second job so alice's branch is behind master (due to a different
        // job being deployed), but there is no content conflict on THIS job's file.
        String jobA = "project1/job-a";
        String jobB = "project1/job-b";

        // Both alice and bob save to separate jobs and deploy.
        service.save(jobA, "SELECT 1 -- job-a", USER, "job-a v1");
        service.deploy(jobA, false, USER);

        String bob = "bob";
        service.save(jobB, "SELECT 99 -- job-b", bob, "job-b v1");
        service.deploy(jobB, false, bob);

        // Alice now saves a new version of job-a. Her branch for job-a
        // is NOT behind master on job-a's file, so there is no content conflict.
        // However, she might be "behind" master in history terms.
        // force=true should proceed with the squash merge.
        service.save(jobA, "SELECT 2 -- job-a v2", USER, "job-a v2");
        String hash = service.deploy(jobA, true, USER);
        Assertions.assertNotNull(hash);
        Assertions.assertEquals(40, hash.length());
    }

    // -------------------------------------------------------------------------
    // revert
    // -------------------------------------------------------------------------

    @Test
    void revert_restoresOlderContent() {
        String hash1 = service.save(JOB_KEY, "SELECT 1 -- original", USER, "original");
        service.save(JOB_KEY, "SELECT 2 -- changed", USER, "changed");

        service.revert(JOB_KEY, hash1, USER);

        String latest = latestCommitHash("user/" + USER + "/" + JOB_KEY);
        String content = service.readSqlAtCommit(JOB_KEY, latest);
        Assertions.assertEquals("SELECT 1 -- original", content);
    }

    // -------------------------------------------------------------------------
    // branchStatus
    // -------------------------------------------------------------------------

    @Test
    void branchStatus_aheadAfterSave() {
        service.save(JOB_KEY, "SELECT 1", USER, "v1");

        BranchStatusDto status = service.branchStatus(JOB_KEY,
                "user/" + USER + "/" + JOB_KEY);
        Assertions.assertNotNull(status);
        Assertions.assertTrue(status.getAheadMaster() >= 1,
                "Branch should be at least 1 commit ahead of master after a save");
    }

    @Test
    void branchStatus_evenAfterDeploy() {
        service.save(JOB_KEY, "SELECT 1", USER, "v1");
        service.deploy(JOB_KEY, false, USER);
        service.save(JOB_KEY, "SELECT 2", USER, "v2");

        BranchStatusDto status = service.branchStatus(JOB_KEY,
                "user/" + USER + "/" + JOB_KEY);
        // After one more save the branch should again be ahead of master.
        Assertions.assertTrue(status.getAheadMaster() >= 1);
    }

    @Test
    void branchStatus_unknownBranchReturnsZeroCounts() {
        BranchStatusDto status = service.branchStatus(JOB_KEY, "user/ghost/no-job");
        Assertions.assertEquals(0, status.getAheadMaster());
        Assertions.assertEquals(0, status.getBehindMaster());
    }

    // -------------------------------------------------------------------------
    // readSqlAtCommit
    // -------------------------------------------------------------------------

    @Test
    void readSqlAtCommit_roundTrip() {
        String original = "SELECT id, name FROM customers WHERE active = 1";
        String hash = service.save(JOB_KEY, original, USER, "roundtrip");
        Assertions.assertEquals(original, service.readSqlAtCommit(JOB_KEY, hash));
    }

    @Test
    void readSqlAtCommit_invalidHashThrows() {
        Assertions.assertThrows(Exception.class,
                () -> service.readSqlAtCommit(JOB_KEY,
                        "0000000000000000000000000000000000000000"));
    }

    // -------------------------------------------------------------------------
    // SqlJobLockManager
    // -------------------------------------------------------------------------

    @Test
    void lockManager_sameJobKeyReturnsSameLock() {
        SqlJobLockManager mgr = new SqlJobLockManager();
        Assertions.assertSame(mgr.getLock("job1"), mgr.getLock("job1"));
    }

    @Test
    void lockManager_differentJobKeyReturnsDifferentLock() {
        SqlJobLockManager mgr = new SqlJobLockManager();
        Assertions.assertNotSame(mgr.getLock("job1"), mgr.getLock("job2"));
    }

    @Test
    void lockManager_withLock_runsAction() {
        SqlJobLockManager mgr = new SqlJobLockManager();
        boolean[] ran = {false};
        mgr.withLock("j1", () -> ran[0] = true);
        Assertions.assertTrue(ran[0]);
    }

    @Test
    void lockManager_withLock_releasesOnException() {
        SqlJobLockManager mgr = new SqlJobLockManager();
        try {
            mgr.withLock("j1", () -> {
                throw new RuntimeException("intentional");
            });
        } catch (RuntimeException ignored) {
        }
        // Lock must be released — acquiring it again must not deadlock.
        boolean[] ran = {false};
        mgr.withLock("j1", () -> ran[0] = true);
        Assertions.assertTrue(ran[0]);
    }

    @Test
    void lockManager_masterLockReleasedOnException() {
        SqlJobLockManager mgr = new SqlJobLockManager();
        try {
            mgr.withMasterLock(() -> {
                throw new RuntimeException("intentional master failure");
            });
        } catch (RuntimeException ignored) {
        }
        boolean[] ran = {false};
        mgr.withMasterLock(() -> ran[0] = true);
        Assertions.assertTrue(ran[0]);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the HEAD commit hash of the given branch by reading the first entry
     * from the commit log (most recent commit).
     */
    private String latestCommitHash(String branch) {
        List<CommitDto> history = service.commits(JOB_KEY, branch, 1);
        Assertions.assertFalse(history.isEmpty(), "Branch " + branch + " has no commits");
        return history.get(0).getHash();
    }
}
