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

export interface SqlJob {
  id: number
  projectCode: number
  jobSlug: string
  jobName: string
  personalBranch: string
  owner: string
  datasourceId: number | null
  datasourceType: string | null
  sqlContent: string | null
  workflowCode: number | null
  createTime: string
  updateTime: string
}

export interface SaveSqlRequest {
  sql: string
  datasourceId?: number
  datasourceType?: string
}

export interface ConfirmedDependency {
  table: string
  taskCode: number | null
}

export interface DeployRequest {
  cron: string
  mergeStrategy?: string
  confirmedDependencies?: ConfirmedDependency[]
}

export interface SqlParseResult {
  targetTable: string | null
  sourceTables: string[]
  unresolvedRefs: string[]
  hasMultipleTargets: boolean
}

export interface DependencyMatchResult {
  table: string
  matchedTaskCode: number | null
  matchedWorkflowCode: number | null
  status: 'MATCHED' | 'UNRESOLVED'
}

export interface DependencyPreviewResult {
  targetTable: string | null
  dependencies: DependencyMatchResult[]
  oldDependencies: DependencyMatchResult[]
  hasUnresolved: boolean
  hasMultipleTargets: boolean
}

export interface TestRunResult {
  status: 'SUCCESS' | 'FAILED'
  rowCount: number
  schema: string[]
  rows: string[][]
  durationMs: number
  error: string | null
}

export interface DeployResult {
  workflowCode: number
  workflowName: string
  releaseState: string
  instanceUrl: string
}
