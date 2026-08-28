package com.passage.agent.agent;

import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.passage.agent.agent.agents.*;
import com.passage.agent.agent.config.AgentConfig;
import com.passage.agent.agent.context.StreamHandlerContext;
import com.passage.agent.agent.event.AgentStreamEventMapper;
import com.passage.agent.agent.graph.ArticleWorkflowExecutor;
import com.passage.agent.agent.parallel.ParallelImageGenerator;
import com.passage.agent.agent.state.ArticleWorkflowKeys;
import com.passage.agent.model.dto.article.ArticleState;
import com.passage.agent.model.enums.SseMessageTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * 文章智能体编排器
 * 使用 Spring AI Alibaba 的 StateGraph 编排多个 Agent
 */
@Service
@Slf4j
public class ArticleAgentOrchestrator implements ArticleWorkflowExecutor {

    @Resource
    private AgentConfig agentConfig;

    @Resource
    private TitleGeneratorAgent titleGeneratorAgent;

    @Resource
    private OutlineGeneratorAgent outlineGeneratorAgent;

    @Resource
    private ContentGeneratorAgent contentGeneratorAgent;

    @Resource
    private ImageAnalyzerAgent imageAnalyzerAgent;

    @Resource
    private ParallelImageGenerator parallelImageGenerator;

    @Resource
    private ContentMergerAgent contentMergerAgent;

    private volatile CompiledGraph phase1CompiledGraph;

    private volatile CompiledGraph phase2CompiledGraph;

    private volatile CompiledGraph phase3CompiledGraph;

    /** Compile immutable graph topology once for the Spring-managed production bean. */
    @PostConstruct
    void initializeCompiledGraphs() {
        try {
            phase1CompiledGraph = buildPhase1Graph().compile();
            phase2CompiledGraph = buildPhase2Graph().compile();
            phase3CompiledGraph = buildPhase3Graph().compile();
        } catch (GraphStateException e) {
            throw new IllegalStateException("文章智能体图初始化失败", e);
        }
    }

    /**
     * 阶段1：生成标题方案
     *
     * @param state         文章状态
     * @param streamHandler 流式输出处理器
     */
    public void executePhase1_GenerateTitles(ArticleState state, Consumer<String> streamHandler) {
        log.info("阶段1（多智能体编排）：开始生成标题方案, taskId={}", state.getTaskId());
        
        try {
            // 构建输入状态
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(ArticleWorkflowKeys.TASK_ID, state.getTaskId());
            inputs.put(ArticleWorkflowKeys.TOPIC, state.getTopic());
            inputs.put(ArticleWorkflowKeys.STYLE, state.getStyle());
            
            // 构建并执行图
            CompiledGraph compiledGraph = phase1CompiledGraph();
            
            Optional<OverAllState> result = compiledGraph.invoke(inputs);
            
            if (result.isPresent()) {
                OverAllState finalState = result.get();
                
                @SuppressWarnings("unchecked")
                List<ArticleState.TitleOption> titleOptions = finalState.value(ArticleWorkflowKeys.TITLE_OPTIONS)
                        .map(v -> (List<ArticleState.TitleOption>) v)
                        .orElse(null);
                
                if (titleOptions != null) {
                    state.setTitleOptions(titleOptions);
                    streamHandler.accept(SseMessageTypeEnum.AGENT1_COMPLETE.getValue());
                    log.info("阶段1（多智能体编排）：标题方案生成完成, 数量={}", titleOptions.size());
                } else {
                    throw new RuntimeException("标题方案生成失败：结果为空");
                }
            } else {
                throw new RuntimeException("标题方案生成失败：执行结果为空");
            }
            
        } catch (Exception e) {
            log.error("阶段1（多智能体编排）：标题方案生成失败, taskId={}", state.getTaskId(), e);
            throw new RuntimeException("标题方案生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 阶段2：生成大纲
     *
     * @param state         文章状态
     * @param streamHandler 流式输出处理器
     */
    public void executePhase2_GenerateOutline(ArticleState state, Consumer<String> streamHandler) {
        log.info("阶段2（多智能体编排）：开始生成大纲, taskId={}", state.getTaskId());
        
        // 设置流式处理器到 ThreadLocal
        StreamHandlerContext.set(state.getTaskId(), event ->
                streamHandler.accept(AgentStreamEventMapper.toLegacyMessage(event)));
        
        try {
            // 构建输入状态
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(ArticleWorkflowKeys.TASK_ID, state.getTaskId());
            inputs.put(ArticleWorkflowKeys.MAIN_TITLE, state.getTitle().getMainTitle());
            inputs.put(ArticleWorkflowKeys.SUB_TITLE, state.getTitle().getSubTitle());
            inputs.put(ArticleWorkflowKeys.USER_DESCRIPTION, state.getUserDescription());
            inputs.put(ArticleWorkflowKeys.STYLE, state.getStyle());
            
            // 构建并执行图
            CompiledGraph compiledGraph = phase2CompiledGraph();
            
            Optional<OverAllState> result = compiledGraph.invoke(inputs);
            
            if (result.isPresent()) {
                OverAllState finalState = result.get();
                
                ArticleState.OutlineResult outline = finalState.value(ArticleWorkflowKeys.OUTLINE)
                        .map(v -> {
                            if (v instanceof ArticleState.OutlineResult) {
                                return (ArticleState.OutlineResult) v;
                            }
                            return null;
                        })
                        .orElse(null);
                
                if (outline != null) {
                    state.setOutline(outline);
                    streamHandler.accept(SseMessageTypeEnum.AGENT2_COMPLETE.getValue());
                    log.info("阶段2（多智能体编排）：大纲生成完成, 章节数={}", outline.getSections().size());
                } else {
                    throw new RuntimeException("大纲生成失败：结果为空");
                }
            } else {
                throw new RuntimeException("大纲生成失败：执行结果为空");
            }
            
        } catch (Exception e) {
            log.error("阶段2（多智能体编排）：大纲生成失败, taskId={}", state.getTaskId(), e);
            throw new RuntimeException("大纲生成失败: " + e.getMessage(), e);
        } finally {
            // 清理 ThreadLocal
            StreamHandlerContext.clear();
        }
    }

    /**
     * 阶段3：生成正文+配图
     *
     * @param state         文章状态
     * @param streamHandler 流式输出处理器
     */
    public void executePhase3_GenerateContent(ArticleState state, Consumer<String> streamHandler) {
        log.info("阶段3（多智能体编排）：开始生成正文+配图, taskId={}", state.getTaskId());
        
        // 设置流式处理器到 ThreadLocal
        StreamHandlerContext.set(state.getTaskId(), event ->
                streamHandler.accept(AgentStreamEventMapper.toLegacyMessage(event)));
        
        try {
            // 构建输入状态（不再包含 streamHandler，避免序列化问题）
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(ArticleWorkflowKeys.TASK_ID, state.getTaskId());
            inputs.put(ArticleWorkflowKeys.MAIN_TITLE, state.getTitle().getMainTitle());
            inputs.put(ArticleWorkflowKeys.SUB_TITLE, state.getTitle().getSubTitle());
            inputs.put(ArticleWorkflowKeys.OUTLINE, state.getOutline());
            inputs.put(ArticleWorkflowKeys.STYLE, state.getStyle());
            inputs.put(ArticleWorkflowKeys.ENABLED_IMAGE_METHODS, state.getEnabledImageMethods());
            
            // 构建并执行图
            CompiledGraph compiledGraph = phase3CompiledGraph();
            
            Optional<OverAllState> result = compiledGraph.invoke(inputs);
            
            if (result.isPresent()) {
                OverAllState finalState = result.get();
                
                // 提取带占位符的正文（优先使用，如果存在）
                String contentWithPlaceholders = finalState.value(ArticleWorkflowKeys.CONTENT_WITH_PLACEHOLDERS)
                        .map(Object::toString)
                        .orElse(null);
                
                // 提取原始正文（作为备用）
                String content = finalState.value(ArticleWorkflowKeys.CONTENT)
                        .map(Object::toString)
                        .orElse(null);
                
                // 提取配图需求
                @SuppressWarnings("unchecked")
                List<ArticleState.ImageRequirement> imageRequirements = finalState.value(ArticleWorkflowKeys.IMAGE_REQUIREMENTS)
                        .map(v -> (List<ArticleState.ImageRequirement>) v)
                        .orElse(null);
                
                // 提取图片结果
                @SuppressWarnings("unchecked")
                List<ArticleState.ImageResult> images = finalState.value(ArticleWorkflowKeys.IMAGES)
                        .map(v -> (List<ArticleState.ImageResult>) v)
                        .orElse(null);
                
                // 提取完整内容
                String fullContent = finalState.value(ArticleWorkflowKeys.FULL_CONTENT)
                        .map(Object::toString)
                        .orElse(null);
                
                // 更新状态（使用带占位符的正文）
                if (contentWithPlaceholders != null) {
                    state.setContent(contentWithPlaceholders);
                } else if (content != null) {
                    state.setContent(content);
                }
                streamHandler.accept(SseMessageTypeEnum.AGENT3_COMPLETE.getValue());
                
                if (imageRequirements != null) {
                    state.setImageRequirements(imageRequirements);
                    streamHandler.accept(SseMessageTypeEnum.AGENT4_COMPLETE.getValue());
                }
                
                if (images != null) {
                    state.setImages(images);
                    streamHandler.accept(SseMessageTypeEnum.AGENT5_COMPLETE.getValue());
                }
                
                if (fullContent != null) {
                    state.setFullContent(fullContent);
                    streamHandler.accept(SseMessageTypeEnum.MERGE_COMPLETE.getValue());
                }
                
                log.info("阶段3（多智能体编排）：正文+配图生成完成, 正文长度={}, 图片数={}",
                        contentWithPlaceholders != null ? contentWithPlaceholders.length() : (content != null ? content.length() : 0),
                        images != null ? images.size() : 0);
                
            } else {
                throw new RuntimeException("正文+配图生成失败：执行结果为空");
            }
            
        } catch (Exception e) {
            log.error("阶段3（多智能体编排）：正文+配图生成失败, taskId={}", state.getTaskId(), e);
            throw new RuntimeException("正文+配图生成失败: " + e.getMessage(), e);
        } finally {
            // 清理 ThreadLocal
            StreamHandlerContext.clear();
        }
    }

    @Override public void executeTitles(ArticleState state, Consumer<String> handler) { executePhase1_GenerateTitles(state, handler); }
    @Override public void executeOutline(ArticleState state, Consumer<String> handler) { executePhase2_GenerateOutline(state, handler); }
    @Override public void executeContent(ArticleState state, Consumer<String> handler) { executePhase3_GenerateContent(state, handler); }

    // region 构建图

    /**
     * 构建阶段1图：标题生成
     */
    private StateGraph buildPhase1Graph() throws GraphStateException {
        KeyStrategyFactory keyStrategyFactory = createKeyStrategyFactory();
        
        return new StateGraph(keyStrategyFactory)
                .addNode("title_generator", node_async(titleGeneratorAgent))
                .addEdge(START, "title_generator")
                .addEdge("title_generator", END);
    }

    /**
     * 构建阶段2图：大纲生成
     */
    private StateGraph buildPhase2Graph() throws GraphStateException {
        KeyStrategyFactory keyStrategyFactory = createKeyStrategyFactory();
        
        return new StateGraph(keyStrategyFactory)
                .addNode("outline_generator", node_async(outlineGeneratorAgent))
                .addEdge(START, "outline_generator")
                .addEdge("outline_generator", END);
    }

    /**
     * 构建阶段3图：正文+配图生成（顺序执行）
     * 流程：正文生成 -> 配图需求分析 -> 并行配图生成 -> 图文合成
     */
    private StateGraph buildPhase3Graph() throws GraphStateException {
        KeyStrategyFactory keyStrategyFactory = createKeyStrategyFactory();
        
        return new StateGraph(keyStrategyFactory)
                // 节点定义
                .addNode("content_generator", node_async(contentGeneratorAgent))
                .addNode("image_analyzer", node_async(imageAnalyzerAgent))
                .addNode("parallel_image_generator", node_async(parallelImageGenerator))
                .addNode("content_merger", node_async(contentMergerAgent))
                // 边定义：顺序执行
                .addEdge(START, "content_generator")
                .addEdge("content_generator", "image_analyzer")
                .addEdge("image_analyzer", "parallel_image_generator")
                .addEdge("parallel_image_generator", "content_merger")
                .addEdge("content_merger", END);
    }

    private CompiledGraph phase1CompiledGraph() throws GraphStateException {
        if (phase1CompiledGraph == null) {
            synchronized (this) {
                if (phase1CompiledGraph == null) {
                    phase1CompiledGraph = buildPhase1Graph().compile();
                }
            }
        }
        return phase1CompiledGraph;
    }

    private CompiledGraph phase2CompiledGraph() throws GraphStateException {
        if (phase2CompiledGraph == null) {
            synchronized (this) {
                if (phase2CompiledGraph == null) {
                    phase2CompiledGraph = buildPhase2Graph().compile();
                }
            }
        }
        return phase2CompiledGraph;
    }

    private CompiledGraph phase3CompiledGraph() throws GraphStateException {
        if (phase3CompiledGraph == null) {
            synchronized (this) {
                if (phase3CompiledGraph == null) {
                    phase3CompiledGraph = buildPhase3Graph().compile();
                }
            }
        }
        return phase3CompiledGraph;
    }

    /**
     * 创建状态键策略工厂
     * 所有键都使用替换策略
     */
    private KeyStrategyFactory createKeyStrategyFactory() {
        return () -> {
            HashMap<String, KeyStrategy> strategies = new HashMap<>();
            strategies.put(ArticleWorkflowKeys.TASK_ID, new ReplaceStrategy());
            strategies.put(ArticleWorkflowKeys.TOPIC, new ReplaceStrategy());
            strategies.put(ArticleWorkflowKeys.STYLE, new ReplaceStrategy());
            strategies.put(ArticleWorkflowKeys.USER_DESCRIPTION, new ReplaceStrategy());
            strategies.put(ArticleWorkflowKeys.MAIN_TITLE, new ReplaceStrategy());
            strategies.put(ArticleWorkflowKeys.SUB_TITLE, new ReplaceStrategy());
            strategies.put(ArticleWorkflowKeys.TITLE_OPTIONS, new ReplaceStrategy());
            strategies.put(ArticleWorkflowKeys.OUTLINE, new ReplaceStrategy());
            strategies.put(ArticleWorkflowKeys.CONTENT, new ReplaceStrategy());
            strategies.put(ArticleWorkflowKeys.CONTENT_WITH_PLACEHOLDERS, new ReplaceStrategy());
            strategies.put(ArticleWorkflowKeys.IMAGE_REQUIREMENTS, new ReplaceStrategy());
            strategies.put(ArticleWorkflowKeys.IMAGES, new ReplaceStrategy());
            strategies.put(ArticleWorkflowKeys.FULL_CONTENT, new ReplaceStrategy());
            strategies.put(ArticleWorkflowKeys.ENABLED_IMAGE_METHODS, new ReplaceStrategy());
            return strategies;
        };
    }

    // endregion
}
