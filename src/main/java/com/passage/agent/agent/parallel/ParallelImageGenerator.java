package com.passage.agent.agent.parallel;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.passage.agent.agent.context.StreamHandlerContext;
import com.passage.agent.agent.state.ArticleWorkflowKeys;
import com.passage.agent.agent.tools.ImageGenerationTool;
import com.passage.agent.model.dto.article.ArticleState;
import com.passage.agent.model.enums.SseMessageTypeEnum;
import com.passage.agent.utils.GsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 并行图片生成器
 * 根据 imageSource 分组，并行执行不同类型的图片生成任务
 */
@Component
@Slf4j
public class ParallelImageGenerator implements NodeAction {

    private final ImageGenerationTool imageGenerationTool;
    private final IdempotentImageGenerationGateway imageGateway;

    public ParallelImageGenerator(ImageGenerationTool imageGenerationTool) {
        this(imageGenerationTool, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ParallelImageGenerator(ImageGenerationTool imageGenerationTool, IdempotentImageGenerationGateway imageGateway) {
        this.imageGenerationTool = imageGenerationTool;
        this.imageGateway = imageGateway;
    }

    public static final String INPUT_IMAGE_REQUIREMENTS = ArticleWorkflowKeys.IMAGE_REQUIREMENTS;
    public static final String OUTPUT_IMAGES = ArticleWorkflowKeys.IMAGES;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        @SuppressWarnings("unchecked")
        List<ArticleState.ImageRequirement> imageRequirements = state.value(INPUT_IMAGE_REQUIREMENTS)
                .map(v -> {
                    if (v instanceof List) {
                        List<?> list = (List<?>) v;
                        if (list.isEmpty()) {
                            return new ArrayList<ArticleState.ImageRequirement>();
                        }
                        if (list.get(0) instanceof ArticleState.ImageRequirement) {
                            return (List<ArticleState.ImageRequirement>) v;
                        }
                        // 尝试转换
                        return convertToImageRequirements(list);
                    }
                    return new ArrayList<ArticleState.ImageRequirement>();
                })
                .orElse(new ArrayList<>());
        
        // 从 ThreadLocal 获取流式处理器
        StreamHandlerContext.StreamEventPublisher streamPublisher = StreamHandlerContext.capture();
        
        log.info("ParallelImageGenerator 开始执行: 配图需求数量={}", imageRequirements.size());
        
        if (imageRequirements.isEmpty()) {
            log.info("没有配图需求，跳过图片生成");
            return Map.of(OUTPUT_IMAGES, new ArrayList<>());
        }
        
        // 按 imageSource 分组
        Map<String, List<ArticleState.ImageRequirement>> groupedBySource = imageRequirements.stream()
                .collect(Collectors.groupingBy(ArticleState.ImageRequirement::getImageSource));
        
        log.info("配图需求按类型分组: {}", 
                groupedBySource.entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                e -> e.getValue().size()
                        )));
        
        // 并行执行不同类型的图片生成
        String taskId = state.value(ArticleWorkflowKeys.TASK_ID).map(Object::toString).orElse(null);
        List<ArticleState.ImageResult> allImages = executeParallel(groupedBySource, streamPublisher, taskId);
        
        // 按 position 排序
        allImages.sort((a, b) -> {
            Integer posA = a.getPosition() != null ? a.getPosition() : 0;
            Integer posB = b.getPosition() != null ? b.getPosition() : 0;
            return posA.compareTo(posB);
        });
        
        log.info("ParallelImageGenerator 执行完成: 成功生成 {} 张图片", allImages.size());
        
        return Map.of(OUTPUT_IMAGES, allImages);
    }

    /**
     * 并行执行图片生成任务
     * 不同 imageSource 类型并行执行，同一类型内部串行执行
     */
    private List<ArticleState.ImageResult> executeParallel(
            Map<String, List<ArticleState.ImageRequirement>> groupedBySource,
            StreamHandlerContext.StreamEventPublisher streamPublisher, String taskId) {
        
        // 为每种 imageSource 创建异步任务
        List<CompletableFuture<List<ArticleState.ImageResult>>> futures = groupedBySource.entrySet().stream()
                .map(entry -> CompletableFuture.supplyAsync(() -> {
                    String imageSource = entry.getKey();
                    List<ArticleState.ImageRequirement> requirements = entry.getValue();
                    List<ArticleState.ImageResult> sourceImages = new ArrayList<>();
                    
                    log.info("开始处理 {} 类型的图片，数量: {}", imageSource, requirements.size());
                    
                    // 同一类型内部串行执行
                    for (ArticleState.ImageRequirement req : requirements) {
                        try {
                            ImageGenerationTool.ImageGenerationResult result = generateImage(taskId, req);
                            
                            if (result.isSuccess()) {
                                ArticleState.ImageResult imageResult = convertToImageResult(result);
                                sourceImages.add(imageResult);
                                
                                // 推送单张配图完成消息
                                streamPublisher.publish(
                                        "parallel_image_generator",
                                        SseMessageTypeEnum.IMAGE_COMPLETE,
                                        GsonUtils.toJson(imageResult));
                                
                                log.info("图片生成成功: imageSource={}, position={}", 
                                        imageSource, req.getPosition());
                            } else {
                                log.warn("图片生成失败: imageSource={}, position={}, error={}", 
                                        imageSource, req.getPosition(), result.getError());
                            }
                        } catch (Exception e) {
                            log.error("图片生成异常: imageSource={}, position={}", 
                                    imageSource, req.getPosition(), e);
                        }
                    }
                    
                    log.info("完成处理 {} 类型的图片", imageSource);
                    return sourceImages;
                }))
                .toList();
        
        // 等待所有任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        return futures.stream()
                .flatMap(future -> future.join().stream())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private ImageGenerationTool.ImageGenerationResult generateImage(String taskId, ArticleState.ImageRequirement req) {
        java.util.function.Supplier<ImageGenerationTool.ImageGenerationResult> action = () -> imageGenerationTool.generateImageDirect(
                req.getImageSource(), req.getKeywords(), req.getPrompt(), req.getPosition(), req.getType(),
                req.getSectionTitle(), req.getPlaceholderId());
        if (imageGateway == null || taskId == null || taskId.isBlank()) return action.get();
        String identity = req.getPlaceholderId();
        String nodeId = "image:" + (identity == null || identity.isBlank() ? req.getPosition() : identity);
        return imageGateway.execute(taskId, nodeId, action);
    }


    /**
     * 转换 ImageGenerationResult 为 ArticleState.ImageResult
     */
    private ArticleState.ImageResult convertToImageResult(ImageGenerationTool.ImageGenerationResult genResult) {
        ArticleState.ImageResult imageResult = new ArticleState.ImageResult();
        imageResult.setPosition(genResult.getPosition());
        imageResult.setUrl(genResult.getUrl());
        imageResult.setMethod(genResult.getMethod());
        imageResult.setKeywords(genResult.getKeywords());
        imageResult.setSectionTitle(genResult.getSectionTitle());
        imageResult.setDescription(genResult.getDescription());
        imageResult.setPlaceholderId(genResult.getPlaceholderId());
        return imageResult;
    }

    /**
     * 转换列表为 ImageRequirement 列表
     */
    private List<ArticleState.ImageRequirement> convertToImageRequirements(List<?> list) {
        List<ArticleState.ImageRequirement> results = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof ArticleState.ImageRequirement) {
                results.add((ArticleState.ImageRequirement) item);
            } else if (item instanceof Map) {
                String json = GsonUtils.toJson(item);
                ArticleState.ImageRequirement req = GsonUtils.fromJson(json, ArticleState.ImageRequirement.class);
                results.add(req);
            }
        }
        return results;
    }
}
