package com.aiagent.infrastructure.search;

import com.aiagent.infrastructure.config.AgentConfig;
import com.microsoft.playwright.Page;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchInformationResult;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 Playwright 真实无头浏览器的 Bing 搜索引擎实现
 * <p>
 * 相比 {@link BingWebSearchEngine}（Jsoup HTTP 直接请求），本实现使用真实 Chromium：
 * - 携带完整 Cookie、Client Hints、Navigator 指纹
 * - Bing 视为正常用户请求，返回真实质量的搜索结果
 * - 支持 JS 渲染（等待 li.b_algo 元素出现后再提取）
 * </p>
 *
 * @author aiagent
 */
@Slf4j
@Component
public class PlaywrightSearchEngine implements WebSearchEngine {

    private static final String BING_SEARCH_URL = "https://www.bing.com/search?q=";
    private static final int DEFAULT_MAX_RESULTS = 5;

    @Autowired
    private PlaywrightPool playwrightPool;

    @Autowired
    private AgentConfig agentConfig;

    @Override
    public WebSearchResults search(WebSearchRequest webSearchRequest) {
        String query = webSearchRequest.searchTerms();
        int maxResults = webSearchRequest.maxResults() != null
            ? webSearchRequest.maxResults()
            : DEFAULT_MAX_RESULTS;

        log.info("[PlaywrightSearch] Bing 搜索: query={}, maxResults={}", query, maxResults);

        AgentConfig.ToolConfig.WebSearchConfig cfg = agentConfig.getTools().getWebSearch();
        Page page = null;
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String searchUrl = BING_SEARCH_URL + encodedQuery;

            page = playwrightPool.newPage(cfg.getUserAgent());

            // 导航到 Bing 搜索结果页
            page.navigate(searchUrl);

            // 等待搜索结果加载（最多等待超时时间）
            int timeoutMs = cfg.getTimeoutSeconds() * 1000;
            try {
                page.waitForSelector("li.b_algo", new Page.WaitForSelectorOptions()
                    .setTimeout(timeoutMs));
            } catch (Exception waitEx) {
                log.warn("[PlaywrightSearch] 等待搜索结果超时，尝试直接解析已加载内容: {}", waitEx.getMessage());
            }

            // 获取完整 HTML，交由 Jsoup 解析（复用已有解析逻辑）
            String html = page.content();
            log.debug("[PlaywrightSearch] 页面 HTML 长度: {}", html.length());

            List<WebSearchOrganicResult> results = parseResults(html, maxResults);
            log.info("[PlaywrightSearch] 解析完成，共 {} 条结果", results.size());

            return WebSearchResults.from(WebSearchInformationResult.from((long) results.size()), results);

        } catch (Exception e) {
            log.error("[PlaywrightSearch] 搜索失败: query={}", query, e);
            return emptyResults();
        } finally {
            playwrightPool.closePage(page);
        }
    }

    /**
     * 解析 Bing 搜索结果页 HTML（与 BingWebSearchEngine 一致）
     */
    private List<WebSearchOrganicResult> parseResults(String html, int maxResults) {
        List<WebSearchOrganicResult> results = new ArrayList<>();
        Document doc = Jsoup.parse(html);

        Elements items = doc.select("li.b_algo");
        log.debug("[PlaywrightSearch] 解析到 {} 个候选条目", items.size());

        for (Element item : items) {
            if (results.size() >= maxResults) {
                break;
            }

            Element anchor = item.selectFirst("h2 > a");
            if (anchor == null) {
                anchor = item.selectFirst("h2 a");
            }
            if (anchor == null) {
                continue;
            }

            String title = anchor.text().trim();
            String href = anchor.attr("href");

            if (href == null || href.isBlank() || !href.startsWith("http")) {
                continue;
            }

            // 摘要：优先 .b_caption p，降级 .b_snippet
            String snippet = "";
            Element captionEl = item.selectFirst(".b_caption p");
            if (captionEl != null) {
                snippet = captionEl.text().trim();
            }
            if (snippet.isBlank()) {
                Element snippetEl = item.selectFirst(".b_snippet");
                if (snippetEl != null) {
                    snippet = snippetEl.text().trim();
                }
            }

            try {
                results.add(WebSearchOrganicResult.from(
                    title.isBlank() ? href : title,
                    URI.create(href),
                    snippet,
                    null
                ));
            } catch (Exception e) {
                log.debug("[PlaywrightSearch] 跳过无效 URL: {}", href);
            }
        }

        return results;
    }

    private WebSearchResults emptyResults() {
        return WebSearchResults.from(WebSearchInformationResult.from(0L), new ArrayList<>());
    }
}
