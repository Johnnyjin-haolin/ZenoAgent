package com.aiagent.service;

import com.aiagent.util.StringUtils;
import com.aiagent.vo.AgentContext;
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
    public ReflectionResult reflect(ActionResult result, AgentContext context, String goal) {
        log.info("开始反思，目标: {}", goal);
        
        ReflectionResult reflection = ReflectionResult.builder()
            .goalAchieved(false)
            .shouldContinue(false)
            .shouldRetry(false)
            .build();
        
        // 分析结果
        if (result.isSuccess()) {
            // 成功：检查是否达成目标
            boolean goalAchieved = checkGoalAchieved(result, goal, context);
            reflection.setGoalAchieved(goalAchieved);
            
            if (goalAchieved) {
                // summary仅用于日志记录，不包含实际数据（避免发送给用户）
                reflection.setSummary("目标已达成");
                log.info("反思结果：目标已达成，数据: {}", result.getData());
            } else {
                reflection.setShouldContinue(true);
                reflection.setSummary("动作执行成功，但目标尚未完全达成，继续执行下一步");
                log.info("反思结果：继续执行");
            }
        } else {
            // 失败：分析失败原因，决定是否重试
            FailureAnalysis analysis = analyzeFailure(result, context);
            reflection.setFailureReason(analysis.getReason());
            reflection.setFailureType(analysis.getType());
            
            if (analysis.isRetryable()) {
                reflection.setShouldRetry(true);
                reflection.setRetryReason(analysis.getRetryReason());
                log.info("反思结果：需要重试，原因: {}", analysis.getRetryReason());
            } else {
                reflection.setShouldContinue(false);
                reflection.setSummary("动作执行失败且不可重试: " + result.getError());
                log.warn("反思结果：失败且不可重试");
            }
        }
        
        return reflection;
    }
    
    /**
     * 检查目标是否已达成
     */
    private boolean checkGoalAchieved(ActionResult result, String goal, AgentContext context) {
        // 简单实现：如果动作类型是COMPLETE，则认为目标已达成
        if ("complete".equals(result.getActionType())) {
            return true;
        }
        
        // 使用LLM判断目标是否达成
        try {
            return checkGoalAchievedWithLLM(result, goal, context);
        } catch (Exception e) {
            log.error("LLM判断目标是否达成失败，使用默认逻辑", e);
            // 降级：如果结果数据不为空，且动作类型是LLM_GENERATE，可能已达成
            if ("llm_generate".equals(result.getActionType()) && result.getData() != null) {
                String dataStr = result.getData().toString();
                // 如果回复长度较长（超过50字符），可能已经回答了问题
                if (dataStr.length() > 50) {
                    return true;
                }
            }
            return false;
        }
    }
    
    /**
     * 使用LLM判断目标是否已达成
     */
    private boolean checkGoalAchievedWithLLM(ActionResult result, String goal, AgentContext context) {
        log.debug("使用LLM判断目标是否达成，目标: {}", goal);
        
        // 构建判断提示词
        String prompt = buildGoalCheckPrompt(result, goal, context);
        
        // 准备消息列表
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage("你是一个智能评估助手，需要判断用户的目标是否已经达成。请严格按照JSON格式返回结果，只返回true或false。"));
        messages.add(new UserMessage(prompt));
        
        // 获取模型ID（从上下文或使用默认值）
        String modelId = context != null ? context.getModelId() : null;
        if (StringUtils.isEmpty(modelId)) {
            modelId = "gpt-4o-mini";
        }
        
        // 调用非流式LLM获取完整响应
        String response = llmChatHandler.chatNonStreaming(modelId, messages);
        
        log.debug("LLM目标达成判断响应: {}", response);
        
        // 解析响应
        return parseGoalCheckResponse(response);
    }
    
    /**
     * 构建目标检查提示词
     */
    private String buildGoalCheckPrompt(ActionResult result, String goal, AgentContext context) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("请判断用户的目标是否已经达成。\n\n");
        prompt.append("**用户目标**: ").append(goal).append("\n\n");
        
        prompt.append("**执行的动作**: ").append(result.getActionType()).append("\n");
        if (result.getActionName() != null) {
            prompt.append("**动作名称**: ").append(result.getActionName()).append("\n");
        }
        prompt.append("\n");
        
        // 执行结果
        if (result.getData() != null) {
            String resultData = result.getData().toString();
            // 限制结果长度，避免提示词过长
            if (resultData.length() > 2000) {
                resultData = resultData.substring(0, 2000) + "... (结果过长，已截断)";
            }
            prompt.append("**执行结果**:\n").append(resultData).append("\n\n");
        }
        
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
        
        prompt.append("**判断标准**:\n");
        prompt.append("1. 如果执行结果已经完整回答了用户的问题，返回 true\n");
        prompt.append("2. 如果执行结果已经完成了用户要求的任务，返回 true\n");
        prompt.append("3. 如果还需要继续执行其他动作才能达成目标，返回 false\n");
        prompt.append("4. 如果执行结果只是中间步骤，还需要更多信息或操作，返回 false\n\n");
        
        prompt.append("**输出格式**:\n");
        prompt.append("请只返回一个JSON对象，格式如下：\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"goalAchieved\": true 或 false\n");
        prompt.append("}\n");
        prompt.append("```\n\n");
        prompt.append("⚠️ **重要**: 只返回JSON对象，不要包含其他文字说明或Markdown代码块标记！\n");
        
        return prompt.toString();
    }
    
    /**
     * 解析目标检查响应
     */
    private boolean parseGoalCheckResponse(String response) {
        try {
            // 清理响应文本，移除可能的Markdown代码块包装和其他文本
            String cleanedResponse = cleanJsonResponse(response);
            log.debug("清理后的目标检查响应: {}", cleanedResponse);
            
            JSONObject json = JSON.parseObject(cleanedResponse);
            Boolean goalAchieved = json.getBoolean("goalAchieved");
            
            if (goalAchieved != null) {
                return goalAchieved;
            } else {
                log.warn("目标检查响应中缺少goalAchieved字段，原始响应: {}", response);
                return false;
            }
            
        } catch (Exception e) {
            log.error("解析目标检查响应失败，原始响应: {}", response, e);
            // 尝试从文本中提取布尔值
            String lowerResponse = response.toLowerCase().trim();
            if (lowerResponse.contains("true") || lowerResponse.contains("是") || lowerResponse.contains("达成")) {
                return true;
            } else if (lowerResponse.contains("false") || lowerResponse.contains("否") || lowerResponse.contains("未达成")) {
                return false;
            }
            return false;
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
}

