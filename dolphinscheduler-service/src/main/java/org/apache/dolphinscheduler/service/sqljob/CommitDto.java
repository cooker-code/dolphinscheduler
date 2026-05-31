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

import java.util.Date;

import lombok.Data;

/**
 * DTO representing a single Git commit in a SQL job's history.
 */
@Data
public class CommitDto {

    /** Full 40-character commit SHA. */
    private String hash;

    /** Abbreviated 7-character commit SHA for display. */
    private String shortHash;

    /** Commit message (first line). */
    private String message;

    /** Author display name. */
    private String author;

    /** Commit timestamp. */
    private Date commitTime;
}
