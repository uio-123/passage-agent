package com.passage.agent.agent.review;

import com.passage.agent.agent.research.ResearchBundle;
import com.passage.agent.agent.writing.SectionDraft;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Runs the two independent reviewers concurrently and verifies their declared report types. */
@Service
public class ParallelSectionReviewUseCase {
    public ReviewPair review(SectionDraft draft, ResearchBundle research, FactChecker factChecker, StyleReviewer styleReviewer) {
        if (draft == null || research == null || factChecker == null || styleReviewer == null) {
            throw new IllegalArgumentException("review inputs must not be null");
        }
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CompletableFuture<ReviewReport> fact = CompletableFuture.supplyAsync(() -> factChecker.review(new FactReviewRequest(draft, research)), executor);
            CompletableFuture<ReviewReport> style = CompletableFuture.supplyAsync(() -> styleReviewer.review(draft), executor);
            ReviewReport factReport = fact.join();
            ReviewReport styleReport = style.join();
            if (factReport == null || factReport.type() != ReviewType.FACT || styleReport == null || styleReport.type() != ReviewType.STYLE) {
                throw new IllegalArgumentException("Reviewers returned an unexpected report type");
            }
            return new ReviewPair(factReport, styleReport);
        }
    }

    public record ReviewPair(ReviewReport fact, ReviewReport style) { }
}
