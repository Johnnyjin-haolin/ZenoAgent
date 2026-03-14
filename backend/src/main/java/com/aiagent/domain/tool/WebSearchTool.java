package com.aiagent.domain.tool;

import com.aiagent.domain.model.bo.AgentContext;
import com.aiagent.infrastructure.config.AgentConfig;
import com.aiagent.infrastructure.search.BingWebSearchEngine;
import com.aiagent.infrastructure.search.PlaywrightSearchEngine;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 系统工具：Web 搜索
 * <p>
 * 支持两种引擎模式（通过 aiagent.tools.web-search.engine 配置切换）：
 * - http：轻量 Jsoup HTTP 爬取 Bing，无需额外依赖，结果质量有限
 * - playwright：真实 Chromium 无头浏览器，携带完整 Cookie/指纹，返回与用户浏览器一致的高质量结果
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

    @Autowired(required = false)
    private PlaywrightSearchEngine playwrightSearchEngine;

    private WebSearchEngine activeSearchEngine;

    @PostConstruct
    public void init() {
        AgentConfig.ToolConfig.WebSearchConfig cfg = agentConfig.getTools().getWebSearch();
        String engine = cfg.getEngine();

        if ("playwright".equalsIgnoreCase(engine) && playwrightSearchEngine != null) {
            this.activeSearchEngine = playwrightSearchEngine;
            log.info("[WebSearch] 使用 Playwright 真实浏览器搜索引擎，超时={}s，最大结果数={}",
                cfg.getTimeoutSeconds(), cfg.getMaxResults());
        } else {
            this.activeSearchEngine = new BingWebSearchEngine(cfg.getTimeoutSeconds(), cfg.getUserAgent());
            if ("playwright".equalsIgnoreCase(engine)) {
                log.warn("[WebSearch] playwright 引擎不可用，降级使用 HTTP Bing 引擎");
            } else {
                log.info("[WebSearch] 使用 HTTP Bing 搜索引擎，超时={}s，最大结果数={}",
                    cfg.getTimeoutSeconds(), cfg.getMaxResults());
            }
        }
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public ToolSpecification getSpecification() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"));
        String description = "在互联网上搜索信息。当用户询问最新资讯、实时数据、外部知识或你不确定的事实时，调用此工具获取搜索结果摘要（标题 + 链接 + 简介）。\n"
            + "当前日期：" + today + "。搜索结果可能包含不同时期的内容，请以摘要中的日期为准，优先引用最近的信息。";
        return ToolSpecification.builder()
            .name(TOOL_NAME)
            .description(description)
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

            WebSearchResults results = activeSearchEngine.search(request);

            if (results == null || results.results() == null || results.results().isEmpty()) {
                return "未找到与「" + query + "」相关的搜索结果。可能是搜索服务暂时不可用，请稍后重试或换用其他关键词。";
            }

            return formatResults(query, results.results(), cfg.getEngine());

        } catch (Exception e) {
            log.error("[WebSearch] 工具执行失败", e);
            return "Web 搜索失败: " + e.getMessage();
        }
    }

    private String formatResults(String query, List<WebSearchOrganicResult> results, String engine) {
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

        if (!"playwright".equalsIgnoreCase(engine)) {
            sb.append("\n\n⚠️ 注意：以上结果通过 HTTP HTML 解析获取，非官方 API，质量可能不稳定。");
            sb.append("请结合摘要内容判断结果可信度，若摘要信息不足或疑似不准确，建议调用 system_fetch_url 获取原文后再作判断。");
        }

        return sb.toString();
    }
}
