package com.aiagent.infrastructure.mapper;

import com.aiagent.domain.model.entity.AgentSkillEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AgentSkill Mapper
 */
@Mapper
public interface AgentSkillMapper {

    void insert(AgentSkillEntity entity);

    AgentSkillEntity selectById(@Param("id") String id);

    /**
     * 批量按 ID 查询（供 Skill 渐进式加载批量构建摘要使用）
     */
    List<AgentSkillEntity> selectByIds(@Param("ids") List<String> ids);

    List<AgentSkillEntity> selectAll();

    /**
     * 分页查询（按创建时间升序）
     */
    List<AgentSkillEntity> selectPage(@Param("offset") int offset, @Param("pageSize") int pageSize);

    /**
     * 查询总数（不含已删除）
     */
    long count();

    void update(AgentSkillEntity entity);

    void deleteById(@Param("id") String id);

    int countById(@Param("id") String id);
}
