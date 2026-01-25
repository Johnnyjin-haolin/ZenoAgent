package com.aiagent.application.service.engine;

import com.aiagent.application.service.action.ActionResult;
import com.aiagent.shared.util.StringUtils;
import com.aiagent.application.model.AgentContext;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 反思引擎
 * 负责评估动作执行结果，决定下一步行动
 * 
 * @author aiagent
 */
@Slf4j
@Component
public class ReflectionEngine {
    
    @Autowired
    private SimpleLLMChatHandler llmChatHandler;
    
    /**
     * 反思：评估结果，决定下一步
     */
    public ReflectionResult reflect(List<ActionResult> results, AgentContext context, String goal) {
        log.info("开始反思，目标: {}, 结果数量: {}", goal, results.size());
        
        ReflectionResult reflection = ReflectionResult.builder()
            .goalAchieved(false)
            .shouldContinue(false)
            .shouldRetry(false)
            .needsSummary(false)
            .build();
        
        if (results == null || results.isEmpty()) {
            log.warn("没有结果需要反思");
            reflection.setShouldContinue(false);
            reflection.setSummary("没有执行结果");
            return reflection;
        }
        
        // 检查是否有成功的结果
        boolean hasSuccess = results.stream().anyMatch(ActionResult::isSuccess);
        
        if (hasSuccess) {
            // 优先使用规则判断，避免不必要的LLM调用
            GoalCheckResult checkResult = checkGoalAchievedWithRules(results);
            if (checkResult == null) {
                // 有成功的结果：使用LLM判断目标是否达成，并同时判断是否需要总结
                checkResult = checkGoalAchievedWithLLM(results, goal, context);
            }
            reflection.setGoalAchieved(checkResult.isGoalAchieved());
            
            if (checkResult.isGoalAchieved()) {
                // 判断是否需要生成友好总结
                reflection.setNeedsSummary(checkResult.isNeedsSummary());
                reflection.setSummary("目标已达成");
                log.info("反思结果：目标已达成，需要总结: {}", checkResult.isNeedsSummary());
            } else {
                reflection.setShouldContinue(true);
                reflection.setSummary("动作执行成功，但目标尚未完全达成，继续执行下一步");
                log.info("反思结果：继续执行");
            }
        } else {
            // 全部失败：分析失败原因，决定是否重试
            // 使用最后一个失败结果进行分析
            ActionResult lastFailure = results.get(results.size() - 1);
            FailureAnalysis analysis = analyzeFailure(lastFailure, context);
            reflection.setFailureReason(analysis.getReason());
            reflection.setFailureType(analysis.getType());
            
            if (analysis.isRetryable()) {
                reflection.setShouldRetry(true);
                reflection.setRetryReason(analysis.getRetryReason());
                log.info("反思结果：需要重试，原因: {}", analysis.getRetryReason());
            } else {
                reflection.setShouldContinue(false);
                reflection.setSummary("动作执行失败且不可重试: " + lastFailure.getError());
                log.warn("反思结果：失败且不可重试");
            }
        }
        
        return reflection;
    }
    
    /**
     * 使用LLM判断目标是否已达成，并同时判断是否需要总结
     * 如果目标达成且需要总结，LLM会直接生成总结文案
     */
    private GoalCheckResult checkGoalAchievedWithLLM(List<ActionResult> results, String goal, AgentContext context) {
        log.debug("使用LLM判断目标是否达成并生成总结，目标: {}, 结果数量: {}", goal, results.size());
        
        // 简单实现：如果动作类型是COMPLETE，则认为目标已达成
        boolean hasComplete = results.stream()
            .anyMatch(r -> "complete".equals(r.getActionType()));
        if (hasComplete) {
            return GoalCheckResult.builder()
                .goalAchieved(true)
                .needsSummary(false)
                .build();
        }
        
        try {
            // 构建判断提示词（包含总结要求）
            String prompt = buildGoalCheckPrompt(results, goal, context);
            
            // 准备消息列表
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new SystemMessage("你是一个智能评估助手，需要判断用户的目标是否已经达成。" +
                "如果目标达成且需要给用户总结，请同时生成一个友好、完整的总结文案。请严格按照JSON格式返回结果。"));
            messages.add(new UserMessage(prompt));
            
            // 获取模型ID（从上下文或使用默认值）
            String modelId = context != null ? context.getModelId() : null;
            if (StringUtils.isEmpty(modelId)) {
                modelId = "gpt-4o-mini";
            }
            
            // 调用非流式LLM获取完整响应
            String response = llmChatHandler.chatNonStreaming(modelId, messages);
            
            log.debug("LLM反思响应: {}", response);
            
            // 解析响应
            return parseGoalCheckResponse(response);
            
        } catch (Exception e) {
            log.error("LLM判断目标是否达成失败，使用默认逻辑", e);
            // 降级：如果结果数据不为空，且动作类型是LLM_GENERATE，可能已达成
            boolean goalAchieved = false;
            ActionResult llmResult = results.stream()
                .filter(r -> "llm_generate".equals(r.getActionType()) && r.getData() != null)
                .findFirst()
                .orElse(null);
            if (llmResult != null) {
                String dataStr = llmResult.getData().toString();
                // 如果回复长度较长（超过50字符），可能已经回答了问题
                if (dataStr.length() > 50) {
                    goalAchieved = true;
                }
            }
            return GoalCheckResult.builder()
                .goalAchieved(goalAchieved)
                .needsSummary(false)
                .build();
        }
    }

    private GoalCheckResult checkGoalAchievedWithRules(List<ActionResult> results) {
        if (results == null || results.size() != 1) {
            return null;
        }
        ActionResult result = results.get(0);
        if (result == null || !result.isSuccess()) {
            return null;
        }
        String actionType = result.getActionType();
        if (StringUtils.isEmpty(actionType)) {
            return null;
        }
        String normalized = actionType.toLowerCase();
        if ("complete".equals(normalized)) {
            return GoalCheckResult.builder().goalAchieved(true).needsSummary(false).build();
        }
        if ("direct_response".equals(normalized) || "llm_generate".equals(normalized)) {
            return GoalCheckResult.builder().goalAchieved(true).needsSummary(false).build();
        }
        if ("tool_call".equals(normalized) || "rag_retrieve".equals(normalized)) {
            return GoalCheckResult.builder().goalAchieved(true).needsSummary(false).build();
        }
        return null;
    }
    
    /**
     * 构建目标检查提示词
     */
    private String buildGoalCheckPrompt(List<ActionResult> results, String goal, AgentContext context) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("请判断用户的目标是否已经达成。\n\n");
        prompt.append("**用户目标**: ").append(goal).append("\n\n");
        
        // 显示所有执行的动作
        prompt.append("**执行的动作**（共 ").append(results.size()).append(" 个）:\n");
        for (int i = 0; i < results.size(); i++) {
            ActionResult result = results.get(i);
            prompt.append("动作 ").append(i + 1).append(": ");
            prompt.append(result.getActionName()).append(" (");
            prompt.append(result.getActionType()).append(")\n");
        }
        prompt.append("\n");
        
        // 执行结果
        prompt.append("**执行结果**（共 ").append(results.size()).append(" 个动作）:\n");
        for (int i = 0; i < results.size(); i++) {
            ActionResult result = results.get(i);
            prompt.append("动作 ").append(i + 1).append(": ");
            prompt.append(result.getActionName()).append(" (");
            prompt.append(result.getActionType()).append(")\n");
            prompt.append("  状态: ").append(result.isSuccess() ? "✅ 成功" : "❌ 失败").append("\n");
            if (result.isSuccess() && result.getData() != null) {
                String dataStr = result.getData().toString();
                if (dataStr.length() > 500) {
                    dataStr = dataStr.substring(0, 500) + "...";
                }
                prompt.append("  结果: ").append(dataStr).append("\n");
            }
            if (!result.isSuccess() && result.getError() != null) {
                prompt.append("  错误: ").append(result.getError()).append("\n");
            }
            prompt.append("\n");
        }
        prompt.append("\n");
        
        // 对话历史（最近3轮）
        if (context != null && context.getMessages() != null && !context.getMessages().isEmpty()) {
            prompt.append("**对话历史**（最近3轮）:\n");
            List<ChatMessage> recentMessages = context.getMessages();
            int start = Math.max(0, recentMessages.size() - 3);
            for (int i = start; i < recentMessages.size(); i++) {
                ChatMessage msg = recentMessages.get(i);
                if (msg instanceof UserMessage) {
                    prompt.append("- 用户: ").append(((UserMessage) msg).singleText()).append("\n");
                } else if (msg instanceof dev.langchain4j.data.message.AiMessage) {
                    dev.langchain4j.data.message.AiMessage aiMsg = (dev.langchain4j.data.message.AiMessage) msg;
                    String text = aiMsg.text();
                    if (text.length() > 500) {
                        text = text.substring(0, 500) + "...";
                    }
                    prompt.append("- 助手: ").append(text).append("\n");
                }
            }
            prompt.append("\n");
        }
        
        // 添加工具调用历史
        if (context != null && context.getToolCallHistory() != null && !context.getToolCallHistory().isEmpty()) {
            prompt.append("**工具调用历史**（最近3次）:\n");
            int historySize = context.getToolCallHistory().size();
            int start = Math.max(0, historySize - 3);
            for (int i = start; i < historySize; i++) {
                Map<String, Object> call = context.getToolCallHistory().get(i);
                prompt.append("- ").append(call.get("toolName"));
                if (call.containsKey("params")) {
                    prompt.append(" (参数: ").append(call.get("params")).append(")");
                }
                prompt.append("\n");
            }
            prompt.append("\n");
        }
        
        // 添加RAG检索历史
        if (context != null && context.getRagRetrieveHistory() != null && !context.getRagRetrieveHistory().isEmpty()) {
            prompt.append("**RAG检索历史**（最近3次）:\n");
            int historySize = context.getRagRetrieveHistory().size();
            int start = Math.max(0, historySize - 3);
            for (int i = start; i < historySize; i++) {
                Map<String, Object> retrieve = context.getRagRetrieveHistory().get(i);
                prompt.append("- 查询: ").append(retrieve.get("query"));
                if (retrieve.containsKey("resultCount")) {
                    prompt.append("，找到 ").append(retrieve.get("resultCount")).append(" 条相关信息");
                }
                prompt.append("\n");
            }
            prompt.append("\n");
        }
        
        prompt.append("**判断标准**:\n");
        prompt.append("1. 如果执行结果已经完整回答了用户的问题，返回 goalAchieved = true\n");
        prompt.append("2. 如果执行结果已经完成了用户要求的任务，返回 goalAchieved = true\n");
        prompt.append("3. 如果还需要继续执行其他动作才能达成目标，返回 goalAchieved = false\n");
        prompt.append("4. 如果执行结果只是中间步骤，还需要更多信息或操作，返回 goalAchieved = false\n\n");
        
        prompt.append("**是否需要总结判断标准**:\n");
        prompt.append("如果 goalAchieved = true，请判断是否需要生成友好总结：\n");
        prompt.append("1. 如果最后动作是 LLM_GENERATE，说明已经生成了友好回复，返回 needsSummary = false\n");
        prompt.append("2. 如果有工具调用历史，说明返回的是原始数据（JSON等），返回 needsSummary = true\n");
        prompt.append("3. 如果有RAG检索历史，可能需要整合多个检索结果，返回 needsSummary = true\n");
        prompt.append("4. 如果是简单对话（打招呼、闲聊等），返回 needsSummary = false\n\n");
        
        prompt.append("**输出格式**:\n");
        prompt.append("请只返回一个JSON对象，格式如下：\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"goalAchieved\": true 或 false,\n");
        prompt.append("  \"needsSummary\": true 或 false（仅在 goalAchieved = true 时判断）\n");
        prompt.append("}\n");
        prompt.append("```\n\n");
        prompt.append("⚠️ **重要**: 只返回JSON对象，不要包含其他文字说明或Markdown代码块标记！\n");
        
        return prompt.toString();
    }
    
    /**
     * 解析目标检查响应（包含总结信息）
     */
    private GoalCheckResult parseGoalCheckResponse(String response) {
        try {
            // 清理响应文本，移除可能的Markdown代码块包装和其他文本
            String cleanedResponse = cleanJsonResponse(response);
            log.debug("清理后的反思响应: {}", cleanedResponse);
            
            JSONObject json = JSON.parseObject(cleanedResponse);
            Boolean goalAchieved = json.getBoolean("goalAchieved");
            
            GoalCheckResult result = GoalCheckResult.builder()
                .goalAchieved(goalAchieved != null ? goalAchieved : false)
                .needsSummary(false)
                .build();
            
            if (goalAchieved != null && goalAchieved) {
                // 如果目标达成，检查是否需要总结
                Boolean needsSummary = json.getBoolean("needsSummary");
                if (needsSummary != null) {
                    result.setNeedsSummary(needsSummary);
                }
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("解析反思响应失败，原始响应: {}", response, e);
            // 尝试从文本中提取布尔值
            String lowerResponse = response.toLowerCase().trim();
            boolean goalAchieved = lowerResponse.contains("true") || 
                                  lowerResponse.contains("是") || 
                                  lowerResponse.contains("达成");
            
            return GoalCheckResult.builder()
                .goalAchieved(goalAchieved)
                .needsSummary(false)
                .build();
        }
    }
    
    /**
     * 清理JSON响应文本
     * 移除Markdown代码块标记、前后空白等
     */
    private String cleanJsonResponse(String response) {
        if (StringUtils.isEmpty(response)) {
            return response;
        }
        
        String cleaned = response.trim();
        
        // 移除Markdown代码块标记（```json ... ``` 或 ``` ... ```）
        if (cleaned.startsWith("```")) {
            int startIdx = cleaned.indexOf('\n');
            if (startIdx > 0) {
                cleaned = cleaned.substring(startIdx + 1);
            }
            int endIdx = cleaned.lastIndexOf("```");
            if (endIdx > 0) {
                cleaned = cleaned.substring(0, endIdx);
            }
        }
        
        // 移除前后空白
        cleaned = cleaned.trim();
        
        // 如果文本中包含JSON对象（以{开头，以}结尾），提取它
        int jsonStart = cleaned.indexOf('{');
        int jsonEnd = cleaned.lastIndexOf('}');
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            cleaned = cleaned.substring(jsonStart, jsonEnd + 1);
        }
        
        return cleaned;
    }
    
    /**
     * 分析失败原因
     */
    private FailureAnalysis analyzeFailure(ActionResult result, AgentContext context) {
        String errorType = result.getErrorType();
        String error = result.getError();
        
        FailureAnalysis analysis = new FailureAnalysis();
        analysis.setReason(error);
        analysis.setType(errorType);
        
        // 判断是否可重试
        if ("TOOL_CALL_ERROR".equals(errorType) || "RAG_RETRIEVE_ERROR".equals(errorType)) {
            // 工具调用或RAG检索错误，可以重试
            analysis.setRetryable(true);
            analysis.setRetryReason("工具调用失败，可能是临时错误，可以重试");
        } else if ("EXCEPTION".equals(errorType)) {
            // 异常，根据错误信息判断
            if (error != null && (error.contains("timeout") || error.contains("网络"))) {
                analysis.setRetryable(true);
                analysis.setRetryReason("网络或超时错误，可以重试");
            } else {
                analysis.setRetryable(false);
            }
        } else {
            analysis.setRetryable(false);
        }
        
        return analysis;
    }
    
    /**
     * 反思结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReflectionResult {
        /**
         * 目标是否已达成
         */
        private boolean goalAchieved;
        
        /**
         * 是否应该继续执行
         */
        private boolean shouldContinue;
        
        /**
         * 是否应该重试
         */
        private boolean shouldRetry;
        
        /**
         * 是否需要生成友好总结
         * true: 需要总结（工具调用、复杂任务等）
         * false: 不需要总结（简单对话、LLM直接回复等）
         */
        private boolean needsSummary;
        
        /**
         * 重试原因
         */
        private String retryReason;
        
        /**
         * 失败原因
         */
        private String failureReason;
        
        /**
         * 失败类型
         */
        private String failureType;
        
        /**
         * 总结
         */
        private String summary;
    }
    
    /**
     * 失败分析
     */
    @Data
    private static class FailureAnalysis {
        private String reason;
        private String type;
        private boolean retryable;
        private String retryReason;
    }
    
    /**
     * 目标检查结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class GoalCheckResult {
        /**
         * 目标是否已达成
         */
        private boolean goalAchieved;
        
        /**
         * 是否需要生成友好总结
         */
        private boolean needsSummary;
    }
}

