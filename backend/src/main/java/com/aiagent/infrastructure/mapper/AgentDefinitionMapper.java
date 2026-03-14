package com.aiagent.infrastructure.mapper;

import com.aiagent.domain.model.entity.AgentDefinitionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Agent 定义 Mapper
 */
@Mapper
public interface AgentDefinitionMapper {

    void insert(AgentDefinitionEntity entity);

    AgentDefinitionEntity selectById(@Param("id") String id);

    List<AgentDefinitionEntity> selectAll();

    List<AgentDefinitionEntity> selectByStatus(@Param("status") String status);

    void update(AgentDefinitionEntity entity);

    void deleteById(@Param("id") String id);

    int countById(@Param("id") String id);
}
