package com.aiagent.infrastructure.mapper;

import com.aiagent.domain.model.entity.MessageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 消息Mapper接口
 * 
 * @author aiagent
 */
@Mapper
public interface MessageMapper {
    
    /**
     * 插入消息
     */
    void insert(MessageEntity message);
    
    /**
     * 根据会话ID查询消息列表
     */
    List<MessageEntity> selectByConversationId(
        @Param("conversationId") String conversationId,
        @Param("limit") int limit
    );
    
    /**
     * 删除会话的所有消息
     */
    void deleteByConversationId(@Param("conversationId") String conversationId);
}

