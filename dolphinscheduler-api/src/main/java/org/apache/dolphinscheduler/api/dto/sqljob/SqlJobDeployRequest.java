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

import javax.validation.constraints.NotBlank;

import lombok.Data;

/**
 * Request body for deploying a SQL job as a DolphinScheduler workflow.
 */
@Data
public class SqlJobDeployRequest {

    /**
     * Quartz cron expression, e.g. {@code "0 30 1 * * ? *"}.
     */
    @NotBlank(message = "cron must not be blank")
    private String cron;

    /**
     * Dependency merge strategy: "OVERWRITE" (default) replaces all previous dependencies;
     * "APPEND" keeps existing dependencies and adds new ones.
     */
    private String mergeStrategy = "OVERWRITE";

    /**
     * User-confirmed dependency list from the deploy drawer.
     */
    private List<ConfirmedDependency> confirmedDependencies = new ArrayList<>();
}
