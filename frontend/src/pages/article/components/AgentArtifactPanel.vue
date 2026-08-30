<template><section class="artifact-panel"><h2>交付物与版本</h2><a-empty v-if="!manifest?.versions.length" description="暂无已登记交付物" /><a-collapse v-else><a-collapse-panel v-for="version in manifest.versions" :key="version.version" :header="`版本 ${version.version} · ${version.changeReason}`"><a-table :data-source="version.artifacts" :pagination="false" row-key="artifactId" size="small"><a-table-column title="类型" data-index="artifactType" /><a-table-column title="Artifact" data-index="artifactId" /><a-table-column title="SHA-256" data-index="sha256" /><a-table-column title="交付状态"><template #default="{ record }"><a-tag :color="record.downloadable ? 'success' : 'default'">{{ record.downloadStatus }}</a-tag></template></a-table-column></a-table></a-collapse-panel></a-collapse></section></template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getAgentArtifacts } from '@/api/agentRun'
import type { ArtifactManifest } from '@/api/agentRun'

const props = defineProps<{ runId: string }>()
const manifest = ref<ArtifactManifest>()

onMounted(async () => {
  manifest.value = (await getAgentArtifacts(props.runId)).data.data
})
</script>
<style scoped>.artifact-panel{margin:24px 0;padding:16px;border:1px solid var(--color-border);border-radius:var(--radius-lg)}h2{font-size:16px}</style>
