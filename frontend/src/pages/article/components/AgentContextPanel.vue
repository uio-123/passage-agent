<template><section class="context-panel"><h2>上下文快照</h2><a-empty v-if="!snapshots.length" description="尚未采集安全上下文摘要"/><a-timeline v-else><a-timeline-item v-for="item in snapshots" :key="item.sequence"><strong>{{ item.stage }}</strong> · {{ item.summary }}<small> Token：{{ item.tokenBefore }} → {{ item.tokenAfter }}</small></a-timeline-item></a-timeline></section></template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getContextSnapshots } from '@/api/agentRun'
import type { ContextSnapshot } from '@/api/agentRun'
const props = defineProps<{ runId: string }>()
const snapshots = ref<ContextSnapshot[]>([])
onMounted(async () => { snapshots.value = (await getContextSnapshots(props.runId)).data.data })
</script>
<style scoped>.context-panel{margin:24px 0;padding:16px;border:1px solid var(--color-border);border-radius:var(--radius-lg)}h2{font-size:16px}small{display:block;color:var(--color-text-muted);margin-top:4px}</style>
