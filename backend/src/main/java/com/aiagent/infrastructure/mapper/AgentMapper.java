package com.aiagent.infrastructure.mapper;

import com.aiagent.domain.model.entity.AgentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Agent 定义 Mapper
 */
@Mapper
public interface AgentMapper {

    void insert(AgentEntity entity);

    AgentEntity selectById(@Param("id") String id);

    List<AgentEntity> selectAll();

    List<AgentEntity> selectByStatus(@Param("status") String status);

    /**
     * 分页查询（内置优先，按创建时间升序）
     */
    List<AgentEntity> selectPage(@Param("offset") int offset, @Param("pageSize") int pageSize);

    /**
     * 查询总数（不含已删除）
     */
    long count();

    void update(AgentEntity entity);

    void deleteById(@Param("id") String id);

    int countById(@Param("id") String id);
}
