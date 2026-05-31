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

import { defineComponent, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NButton,
  NDataTable,
  NSpace,
  NTag,
  NInput,
  NModal,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import { createSqlJob, listSqlJobs } from '@/service/modules/sql-jobs'
import type { SqlJob } from '@/service/modules/sql-jobs/types'

export default defineComponent({
  name: 'SqlJobList',
  setup() {
    const route = useRoute()
    const router = useRouter()
    const message = useMessage()

    const projectCode = Number(route.params.projectCode)

    const jobs = ref<SqlJob[]>([])
    const loading = ref(false)
    const showCreateModal = ref(false)
    const newJobName = ref('')

    const fetchJobs = async () => {
      loading.value = true
      try {
        const data = await listSqlJobs(projectCode)
        jobs.value = data || []
      } catch (e: any) {
        message.error(e?.message || '获取 SQL 作业列表失败')
      } finally {
        loading.value = false
      }
    }

    const handleCreate = async () => {
      const name = newJobName.value.trim()
      if (!name) {
        message.warning('请输入作业名称')
        return
      }
      try {
        const job = await createSqlJob(projectCode, name)
        showCreateModal.value = false
        newJobName.value = ''
        router.push({
          name: 'sql-job-editor',
          params: { projectCode, id: job.id }
        })
      } catch (e: any) {
        message.error(e?.message || '创建失败')
      }
    }

    const handleEdit = (row: SqlJob) => {
      router.push({
        name: 'sql-job-editor',
        params: { projectCode, id: row.id }
      })
    }

    const columns: DataTableColumns<SqlJob> = [
      { title: '作业名称', key: 'jobName' },
      { title: '标识符', key: 'jobSlug' },
      {
        title: '状态',
        key: 'workflowCode',
        render: (row) =>
          row.workflowCode ? (
            <NTag type='success'>已发布</NTag>
          ) : (
            <NTag type='default'>未发布</NTag>
          )
      },
      { title: '负责人', key: 'owner' },
      { title: '更新时间', key: 'updateTime' },
      {
        title: '操作',
        key: 'actions',
        render: (row) => (
          <NButton size='small' onClick={() => handleEdit(row)}>
            编辑
          </NButton>
        )
      }
    ]

    onMounted(fetchJobs)

    return () => (
      <NSpace vertical style='padding: 20px;'>
        <NSpace justify='space-between'>
          <span style='font-size: 18px; font-weight: bold;'>SQL 作业</span>
          <NButton
            type='primary'
            onClick={() => {
              showCreateModal.value = true
            }}
          >
            新建 SQL 作业
          </NButton>
        </NSpace>
        <NDataTable
          columns={columns}
          data={jobs.value}
          loading={loading.value}
          rowKey={(row: SqlJob) => row.id}
        />
        <NModal
          show={showCreateModal.value}
          onUpdateShow={(v: boolean) => {
            showCreateModal.value = v
          }}
          title='新建 SQL 作业'
          preset='dialog'
          positiveText='创建'
          onPositiveClick={handleCreate}
        >
          <NInput
            value={newJobName.value}
            onUpdateValue={(v: string) => {
              newJobName.value = v
            }}
            placeholder='请输入作业名称，如 dwd_order_daily'
          />
        </NModal>
      </NSpace>
    )
  }
})
