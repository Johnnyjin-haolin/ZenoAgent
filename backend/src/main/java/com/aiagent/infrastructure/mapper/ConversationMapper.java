package com.aiagent.infrastructure.mapper;

import com.aiagent.domain.entity.ConversationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 会话Mapper接口
 * 
 * @author aiagent
 */
@Mapper
public interface ConversationMapper {
    
    /**
     * 插入会话
     */
    void insert(ConversationEntity conversation);
    
    /**
     * 根据ID查询会话
     */
    ConversationEntity selectById(@Param("id") String id);
    
    /**
     * 查询会话列表（分页）
     */
    List<ConversationEntity> selectList(
        @Param("status") String status,
        @Param("offset") int offset,
        @Param("limit") int limit
    );
    
    /**
     * 统计会话数量
     */
    int countByStatus(@Param("status") String status);
    
    /**
     * 更新会话标题
     */
    void updateTitle(@Param("id") String id, @Param("title") String title);
    
    /**
     * 更新会话状态
     */
    void updateStatus(@Param("id") String id, @Param("status") String status);
    
    /**
     * 增加消息数量
     */
    void incrementMessageCount(@Param("id") String id);
    
    /**
     * 删除会话
     */
    void deleteById(@Param("id") String id);
}

