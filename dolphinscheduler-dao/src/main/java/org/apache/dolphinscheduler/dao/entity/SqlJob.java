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

package org.apache.dolphinscheduler.dao.entity;

import java.util.Date;

import lombok.Data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * SQL Job metadata entity.
 * Stores per-job Git branch information and the last deployed master commit hash.
 */
@Data
@TableName("t_ds_sql_job")
public class SqlJob {

    /**
     * primary key
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * project code
     */
    @TableField(value = "project_code")
    private Long projectCode;

    /**
     * unique job identifier within a project (slug, alphanumeric + underscore + hyphen)
     */
    @TableField(value = "job_slug")
    private String jobSlug;

    /**
     * display name shown in the UI
     */
    @TableField(value = "job_name")
    private String jobName;

    /**
     * personal Git branch name for this job, e.g. user/alice/order-etl
     */
    @TableField(value = "personal_branch")
    private String personalBranch;

    /**
     * commit hash of the last successful deploy to master; null before first deploy
     */
    @TableField(value = "current_master_commit")
    private String currentMasterCommit;

    /**
     * creator / owner username
     */
    @TableField(value = "owner")
    private String owner;

    /**
     * bound datasource id (optional)
     */
    @TableField(value = "datasource_id")
    private Integer datasourceId;

    @TableField(value = "create_time")
    private Date createTime;

    @TableField(value = "update_time")
    private Date updateTime;
}
