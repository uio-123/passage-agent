import request from '@/request'
import { API_BASE_URL } from '@/config/env'

export type AgentEvent = { sequence: number; eventType: string; nodeId?: string; occurredAt: string; payload: Record<string, string> }
export type AgentRunDetail = { runs: Array<{ runId: string; parentRunId?: string; status: string; currentNode?: string }>; checkpoints: Array<{ nodeId: string; stateVersion: number; status: string }>; nodes: Array<{ nodeId: string; stateVersion: number; status: string }> }

export const getAgentRunDetail = (runId: string) => request<{ data: AgentRunDetail }>(`/agent-runs/${runId}/detail`)
export const getAgentRunEvents = (runId: string, afterSequence = 0) => request<{ data: { events: AgentEvent[] } }>(`/agent-runs/${runId}/events`, { params: { afterSequence } })
export type ArtifactManifest = { versions: Array<{ version: number; parentVersion?: number; changeReason: string; createdAt: string; artifacts: Array<{ artifactId: string; artifactType: string; location: string; sha256: string; downloadable: boolean; downloadStatus: string }> }> }
export const getAgentArtifacts = (runId: string) => request<{ data: ArtifactManifest }>(`/agent-runs/${runId}/artifacts`)
export type ContextSnapshot = { sequence: number; stage: string; summary: string; tokenBefore: number; tokenAfter: number; createdAt: string }
export const getContextSnapshots = (runId: string) => request<{ data: ContextSnapshot[] }>(`/agent-runs/${runId}/context-snapshots`)
export const agentRunEventUrl = (runId: string) => `${API_BASE_URL}/agent-runs/${encodeURIComponent(runId)}/events/stream`
