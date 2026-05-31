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

package org.apache.dolphinscheduler.api.dto.sqljob;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * Result of a test-run execution.
 */
@Data
public class TestRunResult {

    /**
     * Execution status: "SUCCESS" or "FAILED".
     */
    private String status;

    /**
     * Number of rows returned (or affected).
     */
    private int rowCount;

    /**
     * Column names in the result set.
     */
    private List<String> schema = new ArrayList<>();

    /**
     * Row data (each inner list is one row of column values as strings).
     */
    private List<List<String>> rows = new ArrayList<>();

    /**
     * Wall-clock duration in milliseconds.
     */
    private long durationMs;

    /**
     * Error message if status is FAILED; null otherwise.
     */
    private String error;
}
