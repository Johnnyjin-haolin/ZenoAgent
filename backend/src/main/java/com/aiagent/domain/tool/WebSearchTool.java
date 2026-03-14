package com.aiagent.domain.tool;

import com.aiagent.domain.model.bo.AgentContext;
import com.aiagent.infrastructure.config.AgentConfig;
import com.aiagent.infrastructure.search.DuckDuckGoWebSearchEngine;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * 系统工具：Web 搜索
 * <p>
 * 调用 DuckDuckGo HTML 端点进行免费网络搜索（无需 API Key），
 * 返回搜索结果的标题、链接和摘要列表，供 LLM 进一步分析使用。
 * </p>
 *
 * @author aiagent
 */
@Slf4j
@Component
public class WebSearchTool implements SystemTool {

    private static final String TOOL_NAME = "system_web_search";

    @Autowired
    private AgentConfig agentConfig;

    private DuckDuckGoWebSearchEngine searchEngine;

    @PostConstruct
    public void init() {
        AgentConfig.ToolConfig.WebSearchConfig cfg = agentConfig.getTools().getWebSearch();
        this.searchEngine = new DuckDuckGoWebSearchEngine(
            cfg.getTimeoutSeconds(),
            cfg.getUserAgent()
        );
        log.info("[WebSearch] DuckDuckGo 搜索引擎初始化完成，超时={}s，最大结果数={}", cfg.getTimeoutSeconds(), cfg.getMaxResults());
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public ToolSpecification getSpecification() {
        return ToolSpecification.builder()
            .name(TOOL_NAME)
            .description("在互联网上搜索信息。当用户询问最新资讯、实时数据、外部知识或你不确定的事实时，调用此工具获取搜索结果摘要（标题 + 链接 + 简介）。")
            .parameters(JsonObjectSchema.builder()
                .addStringProperty("query", "搜索关键词，建议使用具体、简洁的描述，英文效果更佳")
                .required("query")
                .build())
            .build();
    }

    @Override
    public String execute(String jsonArguments, AgentContext context) {
        try {
            JSONObject args = JSON.parseObject(jsonArguments);
            String query = args.getString("query");

            if (query == null || query.isBlank()) {
                return "搜索关键词不能为空。";
            }

            AgentConfig.ToolConfig.WebSearchConfig cfg = agentConfig.getTools().getWebSearch();
            WebSearchRequest request = WebSearchRequest.builder()
                .searchTerms(query)
                .maxResults(cfg.getMaxResults())
                .build();

            WebSearchResults results = searchEngine.search(request);

            if (results == null || results.results() == null || results.results().isEmpty()) {
                return "未找到与「" + query + "」相关的搜索结果。可能是搜索服务暂时不可用，请稍后重试或换用其他关键词。";
            }

            return formatResults(query, results.results());

        } catch (Exception e) {
            log.error("[WebSearch] 工具执行失败", e);
            return "Web 搜索失败: " + e.getMessage();
        }
    }

    private String formatResults(String query, List<WebSearchOrganicResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下是「").append(query).append("」的搜索结果：\n\n");

        for (int i = 0; i < results.size(); i++) {
            WebSearchOrganicResult r = results.get(i);
            sb.append(i + 1).append(". **").append(r.title()).append("**\n");
            sb.append("   链接: ").append(r.url()).append("\n");
            if (r.snippet() != null && !r.snippet().isBlank()) {
                sb.append("   摘要: ").append(r.snippet()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("如需获取某条结果的完整内容，可调用 system_fetch_url 工具传入对应链接。");
        return sb.toString();
    }
}
