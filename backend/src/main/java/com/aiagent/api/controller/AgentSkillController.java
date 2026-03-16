package com.aiagent.api.controller;

import com.aiagent.api.dto.AgentSkillRequest;
import com.aiagent.api.dto.AgentSkillVO;
import com.aiagent.api.dto.Page;
import com.aiagent.api.dto.PageResult;
import com.aiagent.common.response.Result;
import com.aiagent.domain.skill.AgentSkill;
import com.aiagent.domain.skill.AgentSkillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Skill 管理接口
 * <p>
 * 提供 Skill 的 CRUD 接口，供 Agent 配置页使用
 */
@Slf4j
@RestController
@RequestMapping("/aiagent/skills")
@RequiredArgsConstructor
public class AgentSkillController {

    private final AgentSkillService agentSkillService;

    // ──────────────────────────────────────────────────────────────── 查询

    /**
     * 获取 Skill 列表（支持分页，不传 pageNo 则返回全量）
     */
    @GetMapping
    public Result<?> listSkills(
            @RequestParam(required = false) Integer pageNo,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {

        if (pageNo == null) {
            List<AgentSkillVO> vos = agentSkillService.listAll().stream()
                    .map(this::toVO)
                    .collect(Collectors.toList());
            return Result.success(vos);
        }

        int validPage = Math.max(pageNo, 1);
        int validSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (validPage - 1) * validSize;

        List<AgentSkillVO> vos = agentSkillService.listPage(offset, validSize).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        long total = agentSkillService.count();

        Page<AgentSkillVO> page = new Page<>();
        page.setRecords(vos);
        page.setTotal(total);
        page.setCurrent(validPage);
        page.setSize(validSize);

        return Result.success(PageResult.from(page));
    }

    /**
     * 获取单个 Skill
     */
    @GetMapping("/{id}")
    public Result<AgentSkillVO> getSkill(@PathVariable String id) {
        AgentSkill skill = agentSkillService.getById(id);
        if (skill == null) {
            return Result.error("Skill 不存在");
        }
        return Result.success(toVO(skill));
    }

    // ──────────────────────────────────────────────────────────────── 创建

    @PostMapping
    public Result<AgentSkillVO> createSkill(@RequestBody AgentSkillRequest request) {
        if (!StringUtils.hasText(request.getName())) {
            return Result.error("Skill 名称不能为空");
        }
        if (!StringUtils.hasText(request.getSummary())) {
            return Result.error("Skill 摘要不能为空");
        }
        if (!StringUtils.hasText(request.getContent())) {
            return Result.error("Skill 内容不能为空");
        }
        AgentSkill skill = agentSkillService.create(
                request.getName(), request.getSummary(), request.getContent(), request.getTags());
        return Result.success("创建成功", toVO(skill));
    }

    // ──────────────────────────────────────────────────────────────── 更新

    @PutMapping("/{id}")
    public Result<AgentSkillVO> updateSkill(@PathVariable String id,
                                             @RequestBody AgentSkillRequest request) {
        AgentSkill existing = agentSkillService.getById(id);
        if (existing == null) {
            return Result.error("Skill 不存在");
        }
        AgentSkill skill = agentSkillService.update(
                id, request.getName(), request.getSummary(), request.getContent(), request.getTags());
        return Result.success("更新成功", toVO(skill));
    }

    // ──────────────────────────────────────────────────────────────── 删除

    @DeleteMapping("/{id}")
    public Result<Boolean> deleteSkill(@PathVariable String id) {
        AgentSkill existing = agentSkillService.getById(id);
        if (existing == null) {
            return Result.error("Skill 不存在");
        }
        agentSkillService.delete(id);
        return Result.success("删除成功", true);
    }

    // ──────────────────────────────────────────────────────────────── 转换

    private AgentSkillVO toVO(AgentSkill skill) {
        AgentSkillVO vo = new AgentSkillVO();
        vo.setId(skill.getId());
        vo.setName(skill.getName());
        vo.setSummary(skill.getSummary());
        vo.setContent(skill.getContent());
        vo.setTags(skill.getTags());
        vo.setStatus(skill.getStatus());
        vo.setCreateTime(skill.getCreateTime());
        vo.setUpdateTime(skill.getUpdateTime());
        return vo;
    }
}
