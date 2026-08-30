<template>
  <section v-if="detail" class="agent-observability">
    <h2>Agent 运行轨迹 <a-tag>{{ detail.runs[0]?.status || '未记录' }}</a-tag></h2>
    <div class="dag"><span v-for="node in detail.nodes" :key="`${node.nodeId}-${node.stateVersion}`">{{ node.nodeId }} · {{ node.status }}</span><em v-if="!detail.nodes.length">尚无已记录节点</em></div>
    <a-timeline v-if="events.length"><a-timeline-item v-for="event in events" :key="event.sequence">#{{ event.sequence }} {{ event.eventType }}<small v-if="event.nodeId"> · {{ event.nodeId }}</small></a-timeline-item></a-timeline>
  </section>
</template>
<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { agentRunEventUrl, getAgentRunDetail, getAgentRunEvents, type AgentEvent, type AgentRunDetail } from '@/api/agentRun'
const props = defineProps<{ runId: string }>(); const detail = ref<AgentRunDetail>(); const events = ref<AgentEvent[]>([]); let source: EventSource | undefined
const add = (event: AgentEvent) => { if (event.sequence > (events.value[events.value.length - 1]?.sequence ?? 0)) events.value.push(event) }
onMounted(async () => { detail.value = (await getAgentRunDetail(props.runId)).data.data; const replay = await getAgentRunEvents(props.runId); replay.data.data.events.forEach(add); source = new EventSource(agentRunEventUrl(props.runId), { withCredentials: true }); source.addEventListener('agent-event', event => add(JSON.parse((event as MessageEvent).data))) })
onBeforeUnmount(() => source?.close())
</script>
<style scoped>.agent-observability{margin:24px 0;padding:16px;border:1px solid var(--color-border);border-radius:var(--radius-lg)}h2{font-size:16px}.dag{display:flex;gap:8px;flex-wrap:wrap;margin:12px 0}.dag span{background:var(--color-background-secondary);padding:6px 10px;border-radius:6px;font-size:12px}small{color:var(--color-text-muted)}</style>
