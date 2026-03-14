package com.aiagent.domain.tool;

import com.aiagent.domain.model.bo.AgentContext;
import com.aiagent.infrastructure.config.AgentConfig;
import com.aiagent.infrastructure.search.PlaywrightPageFetcher;
import com.aiagent.infrastructure.search.WebPageFetcher;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 系统工具：获取网页正文内容
 * <p>
 * 支持两种引擎模式（通过 aiagent.tools.web-search.engine 配置切换）：
 * - http：JDK HttpClient + Jsoup，轻量，不支持 JS 渲染页面
 * - playwright：真实 Chromium，支持 JS 渲染，携带 Cookie，可抓取动态页面
 * 支持 truncate 参数控制是否截断，由 LLM 根据场景自主选择。
 * </p>
 *
 * @author aiagent
 */
@Slf4j
@Component
public class FetchUrlTool implements SystemTool {

    private static final String TOOL_NAME = "system_fetch_url";

    private static final String DESCRIPTION_HTTP = "获取指定 URL 网页的正文内容。注意：不支持需要 JavaScript 动态渲染的页面。\n\n"
        + "参数 truncate 控制输出长度：\n"
        + "- truncate=true（默认）：返回前 5000 字。适用于【快速了解页面概要、判断内容是否相关、提取关键摘要信息】等场景，大多数情况下应优先使用。\n"
        + "- truncate=false：返回完整正文（可能很长）。仅在以下情况使用：\n"
        + "  1. 用户明确要求「完整内容」或「全文」；\n"
        + "  2. 截断版本已读取但信息不完整，需要继续获取剩余内容；\n"
        + "  3. 需要精确引用原文的特定段落或数据。";

    private static final String DESCRIPTION_PLAYWRIGHT = "获取指定 URL 网页的正文内容。支持 JavaScript 动态渲染页面（SPA）。\n\n"
        + "参数 truncate 控制输出长度：\n"
        + "- truncate=true（默认）：返回前 5000 字。适用于【快速了解页面概要、判断内容是否相关、提取关键摘要信息】等场景，大多数情况下应优先使用。\n"
        + "- truncate=false：返回完整正文（可能很长）。仅在以下情况使用：\n"
        + "  1. 用户明确要求「完整内容」或「全文」；\n"
        + "  2. 截断版本已读取但信息不完整，需要继续获取剩余内容；\n"
        + "  3. 需要精确引用原文的特定段落或数据。";

    @Autowired
    private AgentConfig agentConfig;

    @Autowired(required = false)
    private PlaywrightPageFetcher playwrightPageFetcher;

    // 运行时使用的 fetcher 类型标志
    private boolean usingPlaywright = false;
    private WebPageFetcher httpPageFetcher;

    @PostConstruct
    public void init() {
        AgentConfig.ToolConfig.WebSearchConfig cfg = agentConfig.getTools().getWebSearch();
        String engine = cfg.getEngine();

        if ("playwright".equalsIgnoreCase(engine) && playwrightPageFetcher != null) {
            this.usingPlaywright = true;
            log.info("[FetchUrl] 使用 Playwright 浏览器抓取器，超时={}s，最大字符数={}",
                cfg.getTimeoutSeconds(), cfg.getMaxContentChars());
        } else {
            this.usingPlaywright = false;
            this.httpPageFetcher = new WebPageFetcher(
                cfg.getTimeoutSeconds(), cfg.getUserAgent(), cfg.getMaxContentChars());
            if ("playwright".equalsIgnoreCase(engine)) {
                log.warn("[FetchUrl] playwright 引擎不可用，降级使用 HTTP 抓取器");
            } else {
                log.info("[FetchUrl] 使用 HTTP 网页抓取器，超时={}s，最大字符数={}",
                    cfg.getTimeoutSeconds(), cfg.getMaxContentChars());
            }
        }
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public ToolSpecification getSpecification() {
        String description = usingPlaywright ? DESCRIPTION_PLAYWRIGHT : DESCRIPTION_HTTP;
        return ToolSpecification.builder()
            .name(TOOL_NAME)
            .description(description)
            .parameters(JsonObjectSchema.builder()
                .addStringProperty("url", "目标网页的完整 URL，必须以 http:// 或 https:// 开头")
                .addProperty("truncate", JsonBooleanSchema.builder()
                    .description("是否截断正文。true=返回前 5000 字（默认，适合概览）；false=返回完整正文（仅在需要全文时使用）")
                    .build())
                .required("url")
                .build())
            .build();
    }

    @Override
    public String execute(String jsonArguments, AgentContext context) {
        try {
            JSONObject args = JSON.parseObject(jsonArguments);
            String url = args.getString("url");

            if (url == null || url.isBlank()) {
                return "URL 不能为空。";
            }

            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                return "URL 格式无效，必须以 http:// 或 https:// 开头。";
            }

            boolean truncate = args.getBooleanValue("truncate", true);

            if (usingPlaywright && playwrightPageFetcher != null) {
                return playwrightPageFetcher.fetch(url, truncate);
            } else {
                return httpPageFetcher.fetch(url, truncate);
            }

        } catch (Exception e) {
            log.error("[FetchUrl] 工具执行失败", e);
            return "网页内容获取失败: " + e.getMessage();
        }
    }
}
