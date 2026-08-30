package com.passage.agent.agent.config;

import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.passage.agent.agent.policy.HostResolver;
import com.passage.agent.agent.policy.ToolAuditSink;
import com.passage.agent.agent.policy.ToolPolicy;
import com.passage.agent.agent.policy.ToolPolicyGateway;
import com.passage.agent.agent.policy.UrlSafetyValidator;
import com.passage.agent.agent.research.GatewayResearchUseCase;
import com.passage.agent.agent.research.ResearchUseCase;
import com.passage.agent.agent.skill.BuiltinSkillCatalog;
import com.passage.agent.agent.skill.SkillContractValidator;
import com.passage.agent.agent.skill.SkillRegistry;
import com.passage.agent.agent.tool.ToolRegistry;
import com.passage.agent.agent.tool.web.JdkWebTransport;
import com.passage.agent.agent.tool.web.WebReaderToolAdapter;
import com.passage.agent.service.ResearchSourceService;
import com.passage.agent.service.ToolCallAuditService;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 配置类
 * 提供 Agent 相关的全局配置和共享组件
 */
@Configuration
@Getter
public class AgentConfig {

    /**
     * 是否启用多智能体编排器
     * true: 使用新的 Spring AI Alibaba 多智能体编排
     * false: 使用原有的 ArticleAgentService
     */
    @Value("${article.agent.orchestrator.enabled:true}")
    private boolean orchestratorEnabled;

    /**
     * Agent 最大迭代次数
     */
    @Value("${article.agent.max-iterations:10}")
    private int maxIterations;

    /** P3 main-flow migration remains opt-in until its adapters and recovery path are fully wired. */
    @Value("${article.agent.quality-loop.enabled:false}")
    private boolean qualityLoopEnabled;

    @Value("${article.agent.quality-loop.max-concurrency:3}")
    private int qualityLoopMaxConcurrency;

    /**
     * 提供内存状态保存器（单例）
     * 用于 Agent 对话记忆管理
     */
    @Bean
    public MemorySaver memorySaver() {
        return new MemorySaver();
    }

    /** E4 production assembly: callers can only reach the reader through the policy gateway. */
    @Bean
    public ToolPolicy researchToolPolicy() {
        return ToolPolicy.strictDefaults();
    }

    @Bean
    public UrlSafetyValidator researchUrlSafetyValidator(ToolPolicy researchToolPolicy) {
        return new UrlSafetyValidator(HostResolver.system(), researchToolPolicy.maxUrlLength());
    }

    @Bean
    public WebReaderToolAdapter webReaderToolAdapter(UrlSafetyValidator researchUrlSafetyValidator) {
        return new WebReaderToolAdapter(researchUrlSafetyValidator, new JdkWebTransport());
    }

    @Bean
    public ToolRegistry researchToolRegistry(WebReaderToolAdapter webReaderToolAdapter) {
        return new ToolRegistry(java.util.List.of(webReaderToolAdapter));
    }

    @Bean
    public ToolAuditSink persistentToolAuditSink(ToolCallAuditService toolCallAuditService) {
        return toolCallAuditService::record;
    }

    @Bean
    public ToolPolicyGateway researchToolPolicyGateway(ToolRegistry researchToolRegistry, ToolPolicy researchToolPolicy,
                                                        ToolAuditSink persistentToolAuditSink) {
        return new ToolPolicyGateway(researchToolRegistry, researchToolPolicy, persistentToolAuditSink);
    }

    @Bean
    public ResearchUseCase researchUseCase(ToolPolicyGateway researchToolPolicyGateway, ResearchSourceService researchSourceService) {
        return new GatewayResearchUseCase(researchToolPolicyGateway, researchSourceService);
    }

    @Bean
    public SkillRegistry skillRegistry() {
        return new SkillRegistry(BuiltinSkillCatalog.definitions());
    }

    @Bean
    public SkillContractValidator skillContractValidator() {
        return new SkillContractValidator();
    }
}
