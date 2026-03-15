package com.aiagent.domain.tool;

import com.aiagent.api.dto.RAGConfig;
import com.aiagent.domain.model.bo.AgentContext;
import com.aiagent.domain.model.bo.AgentKnowledgeDocument;
import com.aiagent.domain.model.bo.AgentKnowledgeResult;
import com.aiagent.domain.rag.RAGEnhancer;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 系统工具：RAG 知识库查询
 *
 * @author aiagent
 */
@Slf4j
@Component
public class RagSystemTool implements SystemTool {

    private static final String TOOL_NAME = "system_rag_query";

    @Autowired
    private RAGEnhancer ragEnhancer;

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public ToolSpecification getSpecification() {
        return ToolSpecification.builder()
            .name(TOOL_NAME)
            .description("在知识库中语义检索相关文档。当需要查询业务知识、文档内容、历史记录时调用此工具。")
            .parameters(JsonObjectSchema.builder()
                .addStringProperty("query", "检索关键词或问题描述")
                .addProperty("knowledgeIds", JsonArraySchema.builder()
                    .description("指定知识库ID列表，为空则查询所有可用知识库")
                    .items(JsonStringSchema.builder().build())
                    .build())
                .required("query")
                .build())
            .build();
    }

    @Override
    public String execute(String jsonArguments, AgentContext context) {
        try {
            JSONObject args = JSON.parseObject(jsonArguments);
            String query = args.getString("query");
            List<String> knowledgeIds = args.getList("knowledgeIds", String.class);

            if (knowledgeIds == null || knowledgeIds.isEmpty()) {
                knowledgeIds = context.getKnowledgeIds();
            }

            RAGConfig ragConfig = context.getRagConfig() != null
                ? context.getRagConfig()
                : RAGConfig.builder().build();

            AgentKnowledgeResult result = ragEnhancer.retrieve(query, knowledgeIds, ragConfig);

            if (result == null || result.isEmpty()) {
                return "未检索到相关知识。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("检索到 ").append(result.getTotalCount()).append(" 条相关知识： ");
            List<AgentKnowledgeDocument> docs = result.getDocuments();
            for (int i = 0; i < docs.size(); i++) {
                AgentKnowledgeDocument doc = docs.get(i);
                sb.append("[").append(i + 1).append("] ");
                if (doc.getDocName() != null) {
                    sb.append(doc.getDocName()).append(" ");
                }
                sb.append(doc.getContent()).append(" ");
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("RAG 查询失败", e);
            return "知识库查询失败: " + e.getMessage();
        }
    }
}
