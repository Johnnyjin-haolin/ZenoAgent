package com.aiagent.domain.tool;

import com.aiagent.domain.model.bo.AgentContext;
import com.aiagent.infrastructure.config.AgentConfig;
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
 * 通过 HTTP 请求抓取指定 URL 的网页，使用 Jsoup 提取主体正文（去除导航栏、广告等噪音），
 * 适合在 system_web_search 获得链接后，进一步读取具体页面的详细内容。
 * 支持 truncate 参数控制是否截断，由 LLM 根据场景自主选择。
 * </p>
 *
 * @author aiagent
 */
@Slf4j
@Component
public class FetchUrlTool implements SystemTool {

    private static final String TOOL_NAME = "system_fetch_url";

    private static final String DESCRIPTION = "获取指定 URL 网页的正文内容。注意：不支持需要 JavaScript 动态渲染的页面。\n\n"
        + "参数 truncate 控制输出长度：\n"
        + "- truncate=true（默认）：返回前 5000 字。适用于【快速了解页面概要、判断内容是否相关、提取关键摘要信息】等场景，大多数情况下应优先使用。\n"
        + "- truncate=false：返回完整正文（可能很长）。仅在以下情况使用：\n"
        + "  1. 用户明确要求「完整内容」或「全文」；\n"
        + "  2. 截断版本已读取但信息不完整，需要继续获取剩余内容；\n"
        + "  3. 需要精确引用原文的特定段落或数据。";

    @Autowired
    private AgentConfig agentConfig;

    private WebPageFetcher webPageFetcher;

    @PostConstruct
    public void init() {
        AgentConfig.ToolConfig.WebSearchConfig cfg = agentConfig.getTools().getWebSearch();
        this.webPageFetcher = new WebPageFetcher(
            cfg.getTimeoutSeconds(),
            cfg.getUserAgent(),
            cfg.getMaxContentChars()
        );
        log.info("[FetchUrl] 网页抓取器初始化完成，超时={}s，最大字符数={}", cfg.getTimeoutSeconds(), cfg.getMaxContentChars());
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public ToolSpecification getSpecification() {
        return ToolSpecification.builder()
            .name(TOOL_NAME)
            .description(DESCRIPTION)
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

            // truncate 默认为 true，未传时使用截断模式
            boolean truncate = args.getBooleanValue("truncate", true);

            return webPageFetcher.fetch(url, truncate);

        } catch (Exception e) {
            log.error("[FetchUrl] 工具执行失败", e);
            return "网页内容获取失败: " + e.getMessage();
        }
    }
}
