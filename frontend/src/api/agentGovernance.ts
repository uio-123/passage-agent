import request from '@/request'
export type Governance = { sampleCount:number; successRate:number; avgDurationMs:number; p50DurationMs:number; p95DurationMs:number; agents:Array<{agentName:string;sampleCount:number;failureRate:number;avgDurationMs:number;slow:boolean;highFailure:boolean}>; token:{available:boolean;reason:string}; cost:{available:boolean;reason:string} }
export const getAgentGovernance=()=>request<{data:Governance}>('/statistics/agent-governance')
