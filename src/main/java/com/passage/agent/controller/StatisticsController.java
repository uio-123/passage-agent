package com.passage.agent.controller;

import com.passage.agent.annotation.AuthCheck;
import com.passage.agent.common.BaseResponse;
import com.passage.agent.common.ResultUtils;
import com.passage.agent.constant.UserConstant;
import com.passage.agent.model.vo.StatisticsVO;
import com.passage.agent.service.StatisticsService;
import com.passage.agent.service.AgentLogService;
import com.passage.agent.model.vo.AgentGovernanceMetrics;
import com.mybatisflex.core.query.QueryWrapper;
import com.passage.agent.agent.observability.AgentGovernanceMetricsCalculator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import java.util.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统计分析控制器
 */
@RestController
@RequestMapping("/statistics")
@Slf4j
@Tag(name = "StatisticsController", description = "统计分析接口")
public class StatisticsController {

    @Resource
    private StatisticsService statisticsService;
    @Resource private AgentLogService agentLogService;

    /**
     * 获取系统统计数据（仅管理员）
     */
    @GetMapping("/overview")
    @Operation(summary = "获取系统统计数据")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<StatisticsVO> getStatistics() {
        StatisticsVO statistics = statisticsService.getStatistics();
        return ResultUtils.success(statistics);
    }

    @GetMapping("/agent-governance")
    @Operation(summary = "获取 Agent 运行治理聚合指标")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<AgentGovernanceMetrics> governance() {
        var logs = agentLogService.list(QueryWrapper.create().orderBy("createTime", false).limit(10000));
        return ResultUtils.success(AgentGovernanceMetricsCalculator.calculate(logs));
    }
}
