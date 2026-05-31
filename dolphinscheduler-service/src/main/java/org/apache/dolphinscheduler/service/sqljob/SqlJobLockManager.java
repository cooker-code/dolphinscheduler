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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.stereotype.Component;

/**
 * Per-job and global-master write-lock manager for SQL Job Git operations.
 *
 * <p>Provides two lock scopes:
 * <ul>
 *   <li><b>Per-job lock</b> ({@link #getLock(String)} / {@link #withLock(String, Runnable)}):
 *       serialises concurrent {@code save}, {@code deploy}, and {@code revert} calls on the
 *       same job key so JGit does not see concurrent index / ref mutations on the same
 *       branch.</li>
 *   <li><b>Master write-lock</b> ({@link #getMasterLock()} / {@link #withMasterLock(Runnable)}):
 *       held exclusively during the squash-merge phase of {@code deploy} to keep the
 *       {@code master} branch history strictly linear regardless of the number of
 *       concurrent deploy requests across different jobs.</li>
 * </ul>
 *
 * <p>All locks are fair ({@code fair=true}) to prevent starvation when many concurrent
 * callers queue for the same resource.
 */
@Component
public class SqlJobLockManager {

    /** Per-job locks: jobKey → ReentrantLock */
    private final ConcurrentHashMap<String, ReentrantLock> jobLocks = new ConcurrentHashMap<>();

    /** Global master-branch write lock — held only during squash-merge. */
    private final ReentrantLock masterLock = new ReentrantLock(true);

    /**
     * Return the per-job lock for the given key, creating it if absent.
     *
     * @param jobKey composite job identifier, e.g. {@code "<projectCode>/<jobSlug>"}
     * @return the fair {@link ReentrantLock} for this job
     */
    public ReentrantLock getLock(String jobKey) {
        return jobLocks.computeIfAbsent(jobKey, k -> new ReentrantLock(true));
    }

    /**
     * Execute {@code action} while holding the per-job lock for {@code jobKey}.
     *
     * @param jobKey composite job identifier
     * @param action the action to run under the lock
     * @throws RuntimeException rethrows any {@link RuntimeException} thrown by the action
     */
    public void withLock(String jobKey, Runnable action) {
        ReentrantLock lock = getLock(jobKey);
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Return the global master-branch write lock.
     *
     * @return the fair {@link ReentrantLock} guarding {@code master} writes
     */
    public ReentrantLock getMasterLock() {
        return masterLock;
    }

    /**
     * Execute {@code action} while holding the global master write-lock.
     *
     * <p>This is used by the deploy path to guarantee that squash-merges on
     * different jobs do not interleave, which would corrupt the master reflog.
     *
     * @param action the action to run under the master lock
     * @throws RuntimeException rethrows any {@link RuntimeException} thrown by the action
     */
    public void withMasterLock(Runnable action) {
        masterLock.lock();
        try {
            action.run();
        } finally {
            masterLock.unlock();
        }
    }
}
