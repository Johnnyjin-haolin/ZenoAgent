package com.aiagent.infrastructure.mapper;

import com.aiagent.domain.model.entity.McpServerEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MCP 服务器配置 Mapper
 */
@Mapper
public interface McpServerMapper {

    void insert(McpServerEntity entity);

    McpServerEntity selectById(@Param("id") String id);

    /** 查询所有启用的服务器（按 scope 和 name 排序） */
    List<McpServerEntity> selectAll();

    /** 按 scope 查询 */
    List<McpServerEntity> selectByScope(@Param("scope") int scope);

    /** 按 ID 列表批量查询 */
    List<McpServerEntity> selectByIds(@Param("ids") List<String> ids);

    void update(McpServerEntity entity);

    void updateEnabled(@Param("id") String id, @Param("enabled") int enabled);

    void deleteById(@Param("id") String id);

    int countById(@Param("id") String id);
}
