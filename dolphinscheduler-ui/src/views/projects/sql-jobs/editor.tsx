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

import { defineComponent, ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import {
  NButton,
  NSpace,
  NCard,
  NTag,
  NSpin,
  useMessage
} from 'naive-ui'
import MonacoEditor from '@/components/monaco-editor'
import {
  saveSqlJob,
  testRunSqlJob,
  parseSqlJob
} from '@/service/modules/sql-jobs'
import type { SqlParseResult, TestRunResult } from '@/service/modules/sql-jobs/types'
import DeployDrawer from './deploy-drawer'

export default defineComponent({
  name: 'SqlJobEditor',
  setup() {
    const route = useRoute()
    const message = useMessage()

    const projectCode = Number(route.params.projectCode)
    const jobId = Number(route.params.id)

    const sqlContent = ref('')
    const saving = ref(false)
    const testing = ref(false)
    const showDeploy = ref(false)
    const parseResult = ref<SqlParseResult | null>(null)
    const testResult = ref<TestRunResult | null>(null)

    const handleSave = async () => {
      saving.value = true
      try {
        await saveSqlJob(projectCode, jobId, {
          sql: sqlContent.value
        })
        message.success('保存成功')
        // refresh parse result after save
        parseResult.value = await parseSqlJob(projectCode, jobId)
      } catch (e: any) {
        message.error(e?.message || '保存失败')
      } finally {
        saving.value = false
      }
    }

    const handleTest = async () => {
      testing.value = true
      testResult.value = null
      try {
        const result = await testRunSqlJob(projectCode, jobId, 1000)
        testResult.value = result
        if (result.status === 'FAILED') {
          message.error(result.error || '测试运行失败')
        } else {
          message.success(`测试成功，返回 ${result.rowCount} 行，耗时 ${result.durationMs}ms`)
        }
      } catch (e: any) {
        message.error(e?.message || '测试运行请求失败')
      } finally {
        testing.value = false
      }
    }

    const targetTableLabel = computed(() => parseResult.value?.targetTable || '(未解析)')
    const sourceTablesLabel = computed(
      () => parseResult.value?.sourceTables?.join(', ') || '(无)'
    )

    return () => (
      <NSpace vertical style='padding: 20px; height: 100%;'>
        {/* Toolbar */}
        <NSpace justify='space-between' align='center'>
          <span style='font-size: 16px; font-weight: bold;'>SQL 编辑器</span>
          <NSpace>
            <NButton
              onClick={handleSave}
              loading={saving.value}
              type='primary'
            >
              保存
            </NButton>
            <NButton
              onClick={handleTest}
              loading={testing.value}
            >
              测试运行
            </NButton>
            <NButton
              type='warning'
              onClick={() => {
                showDeploy.value = true
              }}
            >
              发布上线
            </NButton>
          </NSpace>
        </NSpace>

        {/* SQL Editor */}
        <MonacoEditor
          value={sqlContent.value}
          onUpdateValue={(v: string) => {
            sqlContent.value = v
          }}
          options={{ language: 'sql', minimap: { enabled: false } }}
          style='height: 400px; border: 1px solid #e0e0e0; border-radius: 4px;'
        />

        {/* Parse result card */}
        {parseResult.value && (
          <NCard title='SQL 解析结果' size='small'>
            <NSpace>
              <span>产出表：</span>
              <NTag type='success'>{targetTableLabel.value}</NTag>
              <span>来源表：</span>
              <span>{sourceTablesLabel.value}</span>
              {parseResult.value.hasMultipleTargets && (
                <NTag type='error'>多产出表 — 不支持发布</NTag>
              )}
            </NSpace>
          </NCard>
        )}

        {/* Test run result */}
        {testResult.value && (
          <NCard
            title={`测试结果 (${testResult.value.status})`}
            size='small'
          >
            {testResult.value.status === 'FAILED' ? (
              <span style='color: red;'>{testResult.value.error}</span>
            ) : (
              <span>
                返回 {testResult.value.rowCount} 行，耗时 {testResult.value.durationMs}ms
              </span>
            )}
          </NCard>
        )}

        {/* Deploy Drawer */}
        <DeployDrawer
          projectCode={projectCode}
          jobId={jobId}
          visible={showDeploy.value}
          onClose={() => {
            showDeploy.value = false
          }}
        />
      </NSpace>
    )
  }
})
