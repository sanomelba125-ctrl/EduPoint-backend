package com.campus.modules.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.modules.ai.entity.AiChatMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 智能问答消息 Mapper 接口
 */
@Mapper
public interface AiChatMessageMapper extends BaseMapper<AiChatMessage> {
}
