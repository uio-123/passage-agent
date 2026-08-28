package com.passage.agent.mapper;

import com.mybatisflex.core.BaseMapper;
import com.passage.agent.model.entity.PaymentRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 支付记录 Mapper
 */
@Mapper
public interface PaymentRecordMapper extends BaseMapper<PaymentRecord> {
}
