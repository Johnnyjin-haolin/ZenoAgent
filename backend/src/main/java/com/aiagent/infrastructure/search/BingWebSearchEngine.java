package com.aiagent.infrastructure.search;

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

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 免费 Web 搜索引擎实现：基于 Bing HTML 搜索页（无需 API Key）
 * <p>
 * 使用 Jsoup 直接请求 Bing 搜索结果页，解析 HTML 提取搜索结果，
 * 实现 langchain4j {@link WebSearchEngine} 标准接口。
 * Bing 搜索结果页为服务端渲染，无需 JavaScript，国内可直连。
 * </p>
 *
 * @author aiagent
 */
@Slf4j
public class BingWebSearchEngine implements WebSearchEngine {

    private static final String BING_SEARCH_URL = "https://cn.bing.com/search";

    /**
     * 构建带时间过滤的 Bing 搜索 URL
     * filters=ex1:"ez1_YYYYMMDD" 表示只返回该日期之后的结果
     */
    private String buildSearchUrl(String query) {
        String oneYearAgo = LocalDate.now().minusYears(1)
            .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return BING_SEARCH_URL
            + "?q=" + query.replace(" ", "+")
            + "&filters=ex1:%22ez1_" + oneYearAgo + "%22"
            + "&setlang=zh-Hans"
            + "&cc=CN";
    }
    private static final int DEFAULT_MAX_RESULTS = 5;

    private final int timeoutSeconds;
    private final String userAgent;

    public BingWebSearchEngine(int timeoutSeconds, String userAgent) {
        this.timeoutSeconds = timeoutSeconds;
        this.userAgent = userAgent;
    }

    @Override
    public WebSearchResults search(WebSearchRequest webSearchRequest) {
        String query = webSearchRequest.searchTerms();
        int maxResults = webSearchRequest.maxResults() != null
            ? webSearchRequest.maxResults()
            : DEFAULT_MAX_RESULTS;

        log.info("[WebSearch] Bing 搜索: query={}, maxResults={}", query, maxResults);

        try {
            String searchUrl = buildSearchUrl(query);
            log.debug("[WebSearch] Bing 搜索 URL: {}", searchUrl);
            Document doc = Jsoup.connect(searchUrl)
                .userAgent(userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .referrer("https://www.bing.com/")
                .cookie("SRCHHPGUSR", "SRCHLANG=zh-Hans")
                .timeout(timeoutSeconds * 1000)
                .ignoreHttpErrors(true)
                .get();

            List<WebSearchOrganicResult> results = parseResults(doc, maxResults);
            log.info("[WebSearch] Bing 解析完成，共 {} 条结果", results.size());
            return WebSearchResults.from(WebSearchInformationResult.from((long) results.size()), results);

        } catch (Exception e) {
            log.error("[WebSearch] Bing 搜索失败: query={}", query, e);
            return emptyResults();
        }
    }

    /**
     * 解析 Bing 搜索结果 HTML
     * <p>
     * Bing 结果页结构：
     * - 结果条目：li.b_algo
     * - 标题+链接：li.b_algo h2 > a
     * - 摘要：li.b_algo .b_caption p
     * </p>
     */
    private List<WebSearchOrganicResult> parseResults(Document doc, int maxResults) {
        List<WebSearchOrganicResult> results = new ArrayList<>();

        Elements items = doc.select("li.b_algo");
        log.debug("[WebSearch] Bing 解析到 {} 个候选条目", items.size());

        for (Element item : items) {
            if (results.size() >= maxResults) {
                break;
            }

            // 标题和链接
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

            // 摘要：优先取 .b_caption p，降级取 .b_snippet
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
                log.debug("[WebSearch] 跳过无效 URL: {}", href);
            }
        }

        return results;
    }

    private WebSearchResults emptyResults() {
        return WebSearchResults.from(WebSearchInformationResult.from(0L), new ArrayList<>());
    }
}
