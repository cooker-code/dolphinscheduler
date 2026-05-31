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

import { axios } from '@/service/service'
import type {
  SaveSqlRequest,
  DeployRequest,
  SqlJob,
  SqlParseResult,
  DependencyPreviewResult,
  TestRunResult,
  DeployResult
} from './types'

export function createSqlJob(
  projectCode: number,
  name: string
): Promise<SqlJob> {
  return axios({
    url: `/projects/${projectCode}/sql-jobs`,
    method: 'post',
    params: { name }
  })
}

export function listSqlJobs(projectCode: number): Promise<SqlJob[]> {
  return axios({
    url: `/projects/${projectCode}/sql-jobs`,
    method: 'get'
  })
}

export function saveSqlJob(
  projectCode: number,
  id: number,
  data: SaveSqlRequest
): Promise<SqlJob> {
  return axios({
    url: `/projects/${projectCode}/sql-jobs/${id}`,
    method: 'put',
    data
  })
}

export function testRunSqlJob(
  projectCode: number,
  id: number,
  limitRows = 1000
): Promise<TestRunResult> {
  return axios({
    url: `/projects/${projectCode}/sql-jobs/${id}/test`,
    method: 'post',
    params: { limitRows }
  })
}

export function parseSqlJob(
  projectCode: number,
  id: number
): Promise<SqlParseResult> {
  return axios({
    url: `/projects/${projectCode}/sql-jobs/${id}/parse`,
    method: 'get'
  })
}

export function getDependencyPreview(
  projectCode: number,
  id: number
): Promise<DependencyPreviewResult> {
  return axios({
    url: `/projects/${projectCode}/sql-jobs/${id}/dependency-preview`,
    method: 'get'
  })
}

export function deploySqlJob(
  projectCode: number,
  id: number,
  data: DeployRequest
): Promise<DeployResult> {
  return axios({
    url: `/projects/${projectCode}/sql-jobs/${id}/deploy`,
    method: 'post',
    data
  })
}
