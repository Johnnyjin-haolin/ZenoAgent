package com.aiagent.domain.skill;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 私有 Skill 目录树节点
 * <p>
 * 树结构规则：
 * <ul>
 *   <li>目录节点：{@code skillId} 为 null，可包含子节点</li>
 *   <li>叶节点：{@code skillId} 指向 {@link AgentSkill#getId()}</li>
 *   <li>父节点勾选自动全选子节点，子节点可单独取消</li>
 * </ul>
 */
@Data
public class SkillTreeNode {

    /** 节点 ID（UUID，在 Agent 的树内唯一） */
    private String id;

    /** 节点显示名称 */
    private String label;

    /** 是否启用（false 时不注入 System Prompt，也不加载内容） */
    private boolean enabled = true;

    /** 引用的 Skill ID（叶节点专用，目录节点为 null） */
    private String skillId;

    /** 子节点列表 */
    private List<SkillTreeNode> children = new ArrayList<>();
}
