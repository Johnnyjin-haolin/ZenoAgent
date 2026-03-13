package com.aiagent.domain.tool.todo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Todo 任务项
 *
 * @author aiagent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TodoItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 任务ID（UUID）
     */
    private String id;

    /**
     * 任务内容描述
     */
    private String content;

    /**
     * 任务状态
     */
    private TodoStatus status;

    /**
     * 优先级（1-3，1最高）
     */
    private int priority;

    /**
     * 创建时间戳
     */
    private long createdAt;

    /**
     * Todo 状态枚举
     */
    public enum TodoStatus {
        /**
         * 待完成
         */
        pending,

        /**
         * 已完成
         */
        completed,

        /**
         * 已取消/删除
         */
        cancelled
    }
}
