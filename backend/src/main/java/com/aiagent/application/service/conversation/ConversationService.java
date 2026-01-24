package com.aiagent.application.service.conversation;

import com.aiagent.api.dto.Page;
import com.aiagent.domain.entity.ConversationEntity;
import com.aiagent.infrastructure.mapper.ConversationMapper;
import com.aiagent.api.dto.ConversationInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 会话服务
 * 
 * @author aiagent
 */
@Slf4j
@Service
public class ConversationService {
    
    @Autowired
    private ConversationMapper conversationMapper;
    
    /**
     * 创建会话
     */
    public void createConversation(ConversationInfo info) {
        ConversationEntity entity = new ConversationEntity();
        entity.setId(info.getId());
        entity.setTitle(info.getTitle());
        entity.setUserId(info.getUserId());
        entity.setModelId(info.getModelId());
        entity.setModelName(info.getModelName());
        entity.setStatus(info.getStatus() != null ? info.getStatus() : "active");
        entity.setMessageCount(info.getMessageCount() != null ? info.getMessageCount() : 0);
        
        conversationMapper.insert(entity);
        log.info("创建会话: id={}, title={}", info.getId(), info.getTitle());
    }
    
    /**
     * 查询会话列表（分页）
     */
    public Page<ConversationInfo> listConversations(int pageNo, int pageSize, String status) {
        int offset = (pageNo - 1) * pageSize;
        List<ConversationEntity> entities = conversationMapper.selectList(status, offset, pageSize);
        int total = conversationMapper.countByStatus(status);
        
        List<ConversationInfo> records = entities.stream()
            .map(this::convertToInfo)
            .collect(Collectors.toList());
        
        Page<ConversationInfo> page = new Page<>();
        page.setRecords(records);
        page.setTotal(total);
        page.setCurrent(pageNo);
        page.setSize(pageSize);
        
        log.debug("查询会话列表: pageNo={}, pageSize={}, total={}", pageNo, pageSize, total);
        return page;
    }
    
    /**
     * 获取会话详情
     */
    public ConversationInfo getConversation(String conversationId) {
        ConversationEntity entity = conversationMapper.selectById(conversationId);
        return entity != null ? convertToInfo(entity) : null;
    }
    
    /**
     * 更新会话标题
     */
    public boolean updateTitle(String conversationId, String title) {
        conversationMapper.updateTitle(conversationId, title);
        log.info("更新会话标题: id={}, title={}", conversationId, title);
        return true;
    }
    
    /**
     * 更新会话状态
     */
    public boolean updateStatus(String conversationId, String status) {
        conversationMapper.updateStatus(conversationId, status);
        log.info("更新会话状态: id={}, status={}", conversationId, status);
        return true;
    }
    
    /**
     * 增加消息数量
     */
    public void incrementMessageCount(String conversationId) {
        conversationMapper.incrementMessageCount(conversationId);
        log.debug("增加消息数量: id={}", conversationId);
    }
    
    /**
     * 删除会话
     */
    public boolean deleteConversation(String conversationId) {
        conversationMapper.deleteById(conversationId);
        log.info("删除会话: id={}", conversationId);
        return true;
    }
    
    /**
     * 转换为ConversationInfo
     */
    private ConversationInfo convertToInfo(ConversationEntity entity) {
        return ConversationInfo.builder()
            .id(entity.getId())
            .title(entity.getTitle())
            .userId(entity.getUserId())
            .modelId(entity.getModelId())
            .modelName(entity.getModelName())
            .status(entity.getStatus())
            .messageCount(entity.getMessageCount())
            .createTime(entity.getCreateTime())
            .updateTime(entity.getUpdateTime())
            .build();
    }
}

