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

import { defineComponent, ref, watch, PropType } from 'vue'
import { useRouter } from 'vue-router'
import {
  NDrawer,
  NDrawerContent,
  NButton,
  NDataTable,
  NInput,
  NRadioGroup,
  NRadio,
  NTag,
  NSpace,
  NSpin,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import {
  getDependencyPreview,
  deploySqlJob
} from '@/service/modules/sql-jobs'
import type {
  DependencyPreviewResult,
  DependencyMatchResult
} from '@/service/modules/sql-jobs/types'

export default defineComponent({
  name: 'DeployDrawer',
  props: {
    projectCode: {
      type: Number as PropType<number>,
      required: true
    },
    jobId: {
      type: Number as PropType<number>,
      required: true
    },
    visible: {
      type: Boolean,
      default: false
    },
    onClose: {
      type: Function as PropType<() => void>,
      default: () => {}
    }
  },
  setup(props) {
    const router = useRouter()
    const message = useMessage()

    const loading = ref(false)
    const deploying = ref(false)
    const preview = ref<DependencyPreviewResult | null>(null)
    const cron = ref('0 30 1 * * ? *')
    const mergeStrategy = ref<'OVERWRITE' | 'APPEND'>('OVERWRITE')

    const fetchPreview = async () => {
      loading.value = true
      try {
        preview.value = await getDependencyPreview(
          props.projectCode,
          props.jobId
        )
      } catch (e: any) {
        message.error(e?.message || '获取依赖预览失败')
      } finally {
        loading.value = false
      }
    }

    watch(
      () => props.visible,
      (visible) => {
        if (visible) {
          fetchPreview()
        }
      }
    )

    const handleDeploy = async () => {
      if (!cron.value.trim()) {
        message.warning('请输入 Cron 表达式')
        return
      }
      deploying.value = true
      try {
        const confirmedDeps =
          preview.value?.dependencies.map((d) => ({
            table: d.table,
            taskCode: d.matchedTaskCode
          })) || []

        const result = await deploySqlJob(props.projectCode, props.jobId, {
          cron: cron.value,
          mergeStrategy: mergeStrategy.value,
          confirmedDependencies: confirmedDeps
        })

        message.success(`发布成功：${result.workflowName}`)
        props.onClose()
        router.push(result.instanceUrl)
      } catch (e: any) {
        message.error(e?.message || '发布失败')
      } finally {
        deploying.value = false
      }
    }

    const depColumns: DataTableColumns<DependencyMatchResult> = [
      { title: '来源表', key: 'table' },
      {
        title: '状态',
        key: 'status',
        render: (row) => (
          <NTag type={row.status === 'MATCHED' ? 'success' : 'warning'}>
            {row.status === 'MATCHED' ? '已匹配' : '未知来源'}
          </NTag>
        )
      },
      {
        title: '关联任务 Code',
        key: 'matchedTaskCode',
        render: (row) =>
          row.matchedTaskCode ? String(row.matchedTaskCode) : '-'
      }
    ]

    return () => (
      <NDrawer
        show={props.visible}
        width={520}
        onUpdateShow={(show: boolean) => {
          if (!show) props.onClose()
        }}
      >
        <NDrawerContent title='发布上线' closable>
          {loading.value ? (
            <NSpin />
          ) : (
            <NSpace vertical>
              {/* Target table */}
              <div>
                <span>产出表：</span>
                {preview.value?.targetTable ? (
                  <NTag type='success'>{preview.value.targetTable}</NTag>
                ) : (
                  <NTag type='default'>(未检测到)</NTag>
                )}
                {preview.value?.hasMultipleTargets && (
                  <NTag type='error' style='margin-left: 8px;'>
                    多产出表 — 发布将被阻断
                  </NTag>
                )}
              </div>

              {/* Dependency table */}
              <div>
                <p style='margin-bottom: 8px; font-weight: bold;'>依赖表</p>
                {preview.value?.hasUnresolved && (
                  <NTag type='warning' style='margin-bottom: 8px;'>
                    存在未知来源表，请手动确认
                  </NTag>
                )}
                <NDataTable
                  columns={depColumns}
                  data={preview.value?.dependencies || []}
                  size='small'
                />
              </div>

              {/* Merge strategy */}
              <div>
                <p style='margin-bottom: 8px; font-weight: bold;'>依赖合并策略</p>
                <NRadioGroup
                  value={mergeStrategy.value}
                  onUpdateValue={(v: 'OVERWRITE' | 'APPEND') => {
                    mergeStrategy.value = v
                  }}
                >
                  <NSpace>
                    <NRadio value='OVERWRITE'>覆盖 (替换旧依赖)</NRadio>
                    <NRadio value='APPEND'>追加 (保留旧依赖)</NRadio>
                  </NSpace>
                </NRadioGroup>
              </div>

              {/* Cron */}
              <div>
                <p style='margin-bottom: 8px; font-weight: bold;'>调度周期 (Cron)</p>
                <NInput
                  value={cron.value}
                  onUpdateValue={(v: string) => {
                    cron.value = v
                  }}
                  placeholder='0 30 1 * * ? *'
                />
              </div>

              {/* Deploy button */}
              <NButton
                type='primary'
                loading={deploying.value}
                onClick={handleDeploy}
                style='width: 100%;'
              >
                确认发布
              </NButton>
            </NSpace>
          )}
        </NDrawerContent>
      </NDrawer>
    )
  }
})
