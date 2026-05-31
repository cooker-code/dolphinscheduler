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

/**
 * DTO describing the ahead/behind relationship of a personal branch relative to master.
 */
@Data
public class BranchStatusDto {

    /** Number of commits the personal branch is ahead of master. */
    private int aheadMaster;

    /** Number of commits master is ahead of the personal branch (i.e., commits the personal branch is missing). */
    private int behindMaster;

    /** Full personal branch ref name, e.g. {@code user/alice/order-etl}. */
    private String personalBranch;

    /** Current HEAD commit hash of master. */
    private String masterHead;
}
