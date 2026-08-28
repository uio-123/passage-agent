package com.passage.agent.service;

import com.passage.agent.model.vo.StatisticsVO;

/**
 * 统计服务
 */
public interface StatisticsService {

    /**
     * 获取系统统计数据
     *
     * @return 统计数据
     */
    StatisticsVO getStatistics();
}
