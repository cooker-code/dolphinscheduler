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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.annotation.PostConstruct;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.springframework.stereotype.Service;

/**
 * JGit-backed implementation of {@link SqlJobGitService}.
 *
 * <h3>Repository layout</h3>
 * <pre>
 * ${sql-job.git.repo-path}/.git/   ← standard (non-bare) repository
 *   HEAD → refs/heads/master
 *   refs/heads/master              ← protected; only written via deploy()
 *   refs/heads/user/&lt;name&gt;/&lt;slug&gt; ← per-user personal branches
 * working tree:
 *   &lt;projectCode&gt;/&lt;jobSlug&gt;.sql   ← SQL source files
 * </pre>
 *
 * <h3>Concurrency</h3>
 * <ul>
 *   <li>Every write method acquires the per-job lock from {@link SqlJobLockManager}
 *       before touching the working tree or refs.</li>
 *   <li>{@link #deploy} additionally acquires the global master write-lock so that
 *       squash-merges across different jobs are serialised.</li>
 *   <li>Read methods ({@link #commits}, {@link #diff}, {@link #branchStatus},
 *       {@link #readSqlAtCommit}) are lock-free; JGit's read path is thread-safe.</li>
 * </ul>
 *
 * <h3>Java 8 compatibility</h3>
 * Uses {@link OutputStreamWriter} for file writes instead of
 * {@code Files.writeString} (Java 11+) and {@code javax.annotation.PostConstruct}
 * for post-construction initialisation.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SqlJobGitServiceImpl implements SqlJobGitService {

    private static final String MASTER_BRANCH = "master";
    private static final String REFS_HEADS_PREFIX = "refs/heads/";

    private final SqlJobGitConfig config;
    private final SqlJobLockManager lockManager;

    /**
     * Lazily initialised repository instance.
     * Initialised by {@link #ensureRepoInitialized()} on the first {@link PostConstruct} call.
     */
    private Repository repository;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Called automatically by Spring after dependency injection.
     * Delegates to {@link #ensureRepoInitialized()} so the repository is ready
     * before the first API request arrives.
     */
    @PostConstruct
    public void init() {
        ensureRepoInitialized();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Creates the repository directory if absent, calls {@code git init}, and
     * creates an empty initial commit on {@code master} so that personal branches
     * can be forked from it immediately.
     */
    @Override
    public void ensureRepoInitialized() {
        File repoRoot = new File(resolveRepoPath());
        File gitDir = new File(repoRoot, ".git");

        if (!repoRoot.exists()) {
            if (!repoRoot.mkdirs()) {
                throw new IllegalStateException("Cannot create SQL job repo directory: " + repoRoot);
            }
        }

        try {
            if (!gitDir.exists()) {
                // First time: init a normal (non-bare) repository with a working tree.
                repository = FileRepositoryBuilder.create(gitDir);
                repository.create(false);

                // Create an empty initial commit so master exists and personal
                // branches can be forked from it.
                try (Git git = new Git(repository)) {
                    git.commit()
                            .setMessage("Initial commit")
                            .setAllowEmpty(true)
                            .setAuthor("dolphinscheduler", "ds@dolphinscheduler.apache.org")
                            .call();
                }
                log.info("Initialised SQL job Git repository at {}", repoRoot.getAbsolutePath());
            } else {
                // Repository already exists — open it.
                repository = new FileRepositoryBuilder()
                        .setGitDir(gitDir)
                        .setWorkTree(repoRoot)
                        .build();
                log.debug("Opened existing SQL job Git repository at {}", repoRoot.getAbsolutePath());
            }
        } catch (IOException | GitAPIException e) {
            throw new IllegalStateException("Failed to initialise SQL job Git repository", e);
        }
    }

    // -------------------------------------------------------------------------
    // Write operations
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Steps:
     * <ol>
     *   <li>Acquires the per-job lock.</li>
     *   <li>Ensures the personal branch exists (forked from master if new).</li>
     *   <li>Checks out the personal branch.</li>
     *   <li>Writes the SQL content to the working tree file.</li>
     *   <li>Stages and commits the change.</li>
     * </ol>
     */
    @Override
    public String save(String jobKey, String content, String username, String message) {
        final String[] resultHolder = new String[1];
        lockManager.withLock(jobKey, () -> {
            try (Git git = new Git(repository)) {
                String branch = personalBranch(jobKey, username);
                ensureBranch(git, branch);

                git.checkout().setName(branch).call();

                writeSqlFile(jobKey, content);

                git.add().addFilepattern(sqlFilePath(jobKey)).call();

                String commitMsg = (message != null && !message.isEmpty())
                        ? message
                        : "Save by " + username + " at " + Instant.now();

                RevCommit commit = git.commit()
                        .setMessage(commitMsg)
                        .setAuthor(username, username + "@" + config.getAuthorEmailDomain())
                        .call();

                resultHolder[0] = commit.getName();
                log.debug("Saved job {} on branch {} as commit {}", jobKey, branch, commit.getName());
            } catch (Exception e) {
                throw new RuntimeException("save() failed for job " + jobKey, e);
            }
        });
        return resultHolder[0];
    }

    /**
     * {@inheritDoc}
     *
     * <p>Steps (under the per-job lock, then the global master lock):
     * <ol>
     *   <li>Checks ahead/behind status; throws {@link BehindMasterException} if
     *       behind and {@code force} is {@code false}.</li>
     *   <li>Acquires the global master write-lock.</li>
     *   <li>Reads the SQL content from the personal branch HEAD.</li>
     *   <li>Checks out master, writes the SQL file, and commits.</li>
     * </ol>
     *
     * <p><b>Why no squash merge?</b> Each personal branch owns <em>only</em> the
     * SQL file for its own job; master accumulates files from all jobs. A classic
     * squash merge would conflict whenever master contains files that the personal
     * branch never touched. Instead we read the single file we care about from the
     * personal branch HEAD and apply it directly to master, which is semantically
     * equivalent and avoids any tree-level conflict.
     */
    @Override
    public String deploy(String jobKey, boolean force, String username) {
        final String[] resultHolder = new String[1];
        lockManager.withLock(jobKey, () -> {
            String branch = personalBranch(jobKey, username);

            // Ahead/behind check before acquiring the master lock.
            BranchStatusDto status = branchStatus(jobKey, branch);
            if (status.getBehindMaster() > 0 && !force) {
                throw new BehindMasterException(status.getAheadMaster(), status.getBehindMaster());
            }

            lockManager.withMasterLock(() -> {
                try (Git git = new Git(repository)) {
                    Ref personalRef = repository.findRef(REFS_HEADS_PREFIX + branch);
                    if (personalRef == null) {
                        throw new IllegalStateException("Personal branch not found: " + branch);
                    }

                    // Read the SQL content from the tip of the personal branch.
                    String sqlContent = readSqlAtCommit(jobKey, personalRef.getObjectId().getName());

                    // Checkout master and apply the file change as a new commit.
                    git.checkout().setName(MASTER_BRANCH).call();

                    writeSqlFile(jobKey, sqlContent);
                    git.add().addFilepattern(sqlFilePath(jobKey)).call();

                    String commitMsg = buildDeployCommitMessage(jobKey, username, branch,
                            personalRef.getObjectId().getName());

                    RevCommit deployCommit = git.commit()
                            .setMessage(commitMsg)
                            .setAuthor(username, username + "@" + config.getAuthorEmailDomain())
                            .call();

                    resultHolder[0] = deployCommit.getName();
                    log.info("Deployed job {} by {} on master as commit {}", jobKey, username,
                            deployCommit.getName());
                } catch (BehindMasterException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException("deploy() failed for job " + jobKey, e);
                }
            });
        });
        return resultHolder[0];
    }

    /**
     * {@inheritDoc}
     *
     * <p>Reads the SQL content from the target commit and writes it as a new
     * commit on the personal branch, recording the revert in the commit message.
     */
    @Override
    public String revert(String jobKey, String toCommit, String username) {
        final String[] resultHolder = new String[1];
        lockManager.withLock(jobKey, () -> {
            try (Git git = new Git(repository)) {
                String branch = personalBranch(jobKey, username);
                ensureBranch(git, branch);

                git.checkout().setName(branch).call();

                // Read content from the target commit and overwrite the working tree.
                String content = readSqlAtCommit(jobKey, toCommit);
                writeSqlFile(jobKey, content);

                git.add().addFilepattern(sqlFilePath(jobKey)).call();

                String abbrev = toCommit.length() >= 7 ? toCommit.substring(0, 7) : toCommit;
                RevCommit commit = git.commit()
                        .setMessage("Revert " + jobKey + " to " + abbrev + " by " + username)
                        .setAuthor(username, username + "@" + config.getAuthorEmailDomain())
                        .call();

                resultHolder[0] = commit.getName();
                log.info("Reverted job {} to {} on branch {} as commit {}", jobKey, abbrev, branch,
                        commit.getName());
            } catch (Exception e) {
                throw new RuntimeException("revert() failed for job " + jobKey, e);
            }
        });
        return resultHolder[0];
    }

    // -------------------------------------------------------------------------
    // Read operations (lock-free)
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CommitDto> commits(String jobKey, String branch, int limit) {
        List<CommitDto> result = new ArrayList<>();
        try {
            Ref branchRef = repository.findRef(REFS_HEADS_PREFIX + branch);
            if (branchRef == null) {
                return result;
            }

            try (RevWalk walk = new RevWalk(repository)) {
                walk.markStart(walk.parseCommit(branchRef.getObjectId()));
                int count = 0;
                for (RevCommit commit : walk) {
                    if (count++ >= limit) {
                        break;
                    }
                    CommitDto dto = new CommitDto();
                    dto.setHash(commit.getName());
                    dto.setShortHash(commit.getName().substring(0, Math.min(7, commit.getName().length())));
                    dto.setMessage(commit.getShortMessage());
                    dto.setAuthor(commit.getAuthorIdent().getName());
                    dto.setCommitTime(new Date((long) commit.getCommitTime() * 1000L));
                    result.add(dto);
                }
            }
        } catch (IOException e) {
            log.warn("commits() failed for branch {}: {}", branch, e.getMessage());
        }
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Resolves both ref arguments as either full commit SHAs or branch names,
     * then produces a unified diff limited to the job's SQL file.
     */
    @Override
    public String diff(String jobKey, String baseCommit, String headCommit) {
        try (
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                DiffFormatter formatter = new DiffFormatter(out)) {

            formatter.setRepository(repository);
            formatter.setContext(3);

            ObjectId baseId;
            ObjectId headId;
            try {
                baseId = repository.resolve(baseCommit);
                headId = repository.resolve(headCommit);
            } catch (IOException e) {
                log.warn("diff() could not resolve refs: base={} head={}: {}", baseCommit, headCommit,
                        e.getMessage());
                return "";
            }

            if (baseId == null || headId == null) {
                log.warn("diff() could not resolve refs: base={} head={}", baseCommit, headCommit);
                return "";
            }

            // If either object does not exist in the repository (e.g. a garbage SHA),
            // prepareTreeParser will throw; catch and return empty diff gracefully.
            AbstractTreeIterator baseTree;
            AbstractTreeIterator headTree;
            try {
                baseTree = prepareTreeParser(baseId);
                headTree = prepareTreeParser(headId);
            } catch (IOException e) {
                log.warn("diff() object not found for job {}: {}", jobKey, e.getMessage());
                return "";
            }

            String targetPath = sqlFilePath(jobKey);
            List<DiffEntry> entries = formatter.scan(baseTree, headTree);
            for (DiffEntry entry : entries) {
                if (targetPath.equals(entry.getNewPath()) || targetPath.equals(entry.getOldPath())) {
                    formatter.format(entry);
                }
            }
            formatter.flush();
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            throw new RuntimeException("diff() failed for job " + jobKey, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BranchStatusDto branchStatus(String jobKey, String branch) {
        BranchStatusDto dto = new BranchStatusDto();
        dto.setPersonalBranch(branch);

        try {
            ObjectId masterHead = repository.resolve(REFS_HEADS_PREFIX + MASTER_BRANCH);
            dto.setMasterHead(masterHead != null ? masterHead.getName() : null);

            // Attempt to use JGit's BranchTrackingStatus for configured remote tracking.
            // In a local repo without a configured upstream, this returns null — we then
            // fall back to a manual merge-base calculation.
            BranchTrackingStatus trackingStatus = BranchTrackingStatus.of(repository, branch);
            if (trackingStatus != null) {
                dto.setAheadMaster(trackingStatus.getAheadCount());
                dto.setBehindMaster(trackingStatus.getBehindCount());
                return dto;
            }

            // Manual fallback: compare branch HEAD against master via merge-base.
            ObjectId branchHead = repository.resolve(REFS_HEADS_PREFIX + branch);
            if (masterHead == null || branchHead == null) {
                return dto;
            }

            try (RevWalk walk = new RevWalk(repository)) {
                walk.setRevFilter(RevFilter.MERGE_BASE);
                walk.markStart(walk.parseCommit(masterHead));
                walk.markStart(walk.parseCommit(branchHead));
                RevCommit mergeBase = walk.next();

                if (mergeBase != null) {
                    dto.setAheadMaster(countCommitsBetween(branchHead, mergeBase.getId()));
                    dto.setBehindMaster(countCommitsBetween(masterHead, mergeBase.getId()));
                } else {
                    // No common ancestor — treat master as fully ahead.
                    dto.setBehindMaster(countCommitsBetween(masterHead, null));
                }
            }
        } catch (IOException e) {
            log.warn("branchStatus() failed for branch {}: {}", branch, e.getMessage());
        }
        return dto;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String readSqlAtCommit(String jobKey, String commitHash) {
        try {
            ObjectId commitId = repository.resolve(commitHash);
            if (commitId == null) {
                throw new IllegalArgumentException("Cannot resolve commit: " + commitHash);
            }

            try (RevWalk walk = new RevWalk(repository)) {
                RevCommit commit = walk.parseCommit(commitId);
                RevTree tree = commit.getTree();

                try (TreeWalk treeWalk = TreeWalk.forPath(repository, sqlFilePath(jobKey), tree)) {
                    if (treeWalk == null) {
                        // File did not exist at this commit.
                        return "";
                    }
                    ObjectId blobId = treeWalk.getObjectId(0);
                    ObjectLoader loader = repository.open(blobId);
                    return new String(loader.getBytes(), StandardCharsets.UTF_8);
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            throw new RuntimeException("readSqlAtCommit() failed for job " + jobKey + " at " + commitHash, e);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Resolve configured repo path, expanding {@code ${user.home}} if present.
     */
    private String resolveRepoPath() {
        return config.getRepoPath().replace("${user.home}", System.getProperty("user.home"));
    }

    /**
     * Derive the personal branch name for a given job and user.
     * Format: {@code user/<username>/<jobKey>} with path separators preserved in jobKey.
     */
    private String personalBranch(String jobKey, String username) {
        return "user/" + username + "/" + jobKey;
    }

    /**
     * Derive the SQL file path within the working tree for a given jobKey.
     * jobKey is expected to be of the form {@code "<projectCode>/<jobSlug>"}.
     */
    private String sqlFilePath(String jobKey) {
        return jobKey + ".sql";
    }

    /**
     * Ensure the named branch exists in the repository. If it does not exist it
     * is created as a fork from the current HEAD of master.
     */
    private void ensureBranch(Git git, String branch) throws GitAPIException, IOException {
        if (repository.findRef(REFS_HEADS_PREFIX + branch) == null) {
            git.branchCreate().setName(branch).call();
            log.debug("Created personal branch {}", branch);
        }
    }

    /**
     * Write {@code content} to the SQL file for the given jobKey in the working tree.
     * Creates parent directories as needed. Java 8 compatible.
     */
    private void writeSqlFile(String jobKey, String content) throws IOException {
        File sqlFile = new File(resolveRepoPath(), sqlFilePath(jobKey));
        File parent = sqlFile.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create directory: " + parent);
        }
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(sqlFile), StandardCharsets.UTF_8)) {
            writer.write(content);
        }
    }

    /**
     * Prepare a tree iterator for the given commit object ID, used by the diff formatter.
     */
    private AbstractTreeIterator prepareTreeParser(ObjectId objectId) throws IOException {
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(objectId);
            RevTree tree = walk.parseTree(commit.getTree().getId());

            CanonicalTreeParser parser = new CanonicalTreeParser();
            try (ObjectReader reader = repository.newObjectReader()) {
                parser.reset(reader, tree.getId());
            }
            return parser;
        }
    }

    /**
     * Count commits reachable from {@code from} but not from {@code stopAt}.
     * If {@code stopAt} is {@code null}, counts all commits reachable from {@code from}.
     */
    private int countCommitsBetween(ObjectId from, ObjectId stopAt) throws IOException {
        int count = 0;
        try (RevWalk walk = new RevWalk(repository)) {
            walk.markStart(walk.parseCommit(from));
            if (stopAt != null) {
                walk.markUninteresting(walk.parseCommit(stopAt));
            }
            for (RevCommit ignored : walk) {
                count++;
            }
        }
        return count;
    }

    /**
     * Build a structured deploy commit message that references the source branch and HEAD.
     */
    private String buildDeployCommitMessage(String jobKey, String username, String branch,
                                            String sourceHead) {
        return "Deploy " + jobKey + " by " + username + " at " + Instant.now()
                + "\n\nSquashed-from: " + branch + "@" + sourceHead;
    }
}
