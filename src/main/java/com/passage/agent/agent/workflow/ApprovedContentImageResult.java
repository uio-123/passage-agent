package com.passage.agent.agent.workflow;

import com.passage.agent.model.dto.article.ArticleState;

import java.util.List;

/** Result of image analysis, generation and markdown merge after quality approval. */
public record ApprovedContentImageResult(
        String fullContent,
        List<ArticleState.ImageRequirement> imageRequirements,
        List<ArticleState.ImageResult> images
) {
    public ApprovedContentImageResult {
        if (fullContent == null || fullContent.isBlank()) throw new IllegalArgumentException("fullContent must not be blank");
        imageRequirements = imageRequirements == null ? List.of() : List.copyOf(imageRequirements);
        images = images == null ? List.of() : List.copyOf(images);
    }
}
