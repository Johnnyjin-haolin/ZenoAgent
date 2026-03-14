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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 免费 Web 搜索引擎实现：基于 DuckDuckGo HTML 端点（无需 API Key）
 * <p>
 * 通过 POST https://html.duckduckgo.com/html/ 模拟浏览器搜索行为，
 * 解析返回的 HTML 提取搜索结果，实现 langchain4j {@link WebSearchEngine} 标准接口。
 * </p>
 *
 * @author aiagent
 */
@Slf4j
public class DuckDuckGoWebSearchEngine implements WebSearchEngine {

    private static final String DDG_URL = "https://html.duckduckgo.com/html/";
    private static final int DEFAULT_MAX_RESULTS = 5;

    private final HttpClient httpClient;
    private final int timeoutSeconds;
    private final String userAgent;

    public DuckDuckGoWebSearchEngine(int timeoutSeconds, String userAgent) {
        this.timeoutSeconds = timeoutSeconds;
        this.userAgent = userAgent;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(timeoutSeconds))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    @Override
    public WebSearchResults search(WebSearchRequest webSearchRequest) {
        String query = webSearchRequest.searchTerms();
        int maxResults = webSearchRequest.maxResults() != null
            ? webSearchRequest.maxResults()
            : DEFAULT_MAX_RESULTS;

        log.info("[WebSearch] DuckDuckGo 搜索: query={}, maxResults={}", query, maxResults);

        try {
            String body = "q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&b=&kl=&df=";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(DDG_URL))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("User-Agent", userAgent)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Referer", "https://duckduckgo.com/")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            log.debug("[WebSearch] DuckDuckGo 响应: statusCode={}", statusCode);

            if (statusCode != 200) {
                log.warn("[WebSearch] DuckDuckGo 返回异常状态码: {}", statusCode);
                return emptyResults();
            }

            List<WebSearchOrganicResult> results = parseResults(response.body(), maxResults);
            log.info("[WebSearch] DuckDuckGo 解析完成，共 {} 条结果", results.size());
            return WebSearchResults.from(WebSearchInformationResult.from((long) results.size()), results);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[WebSearch] DuckDuckGo 搜索被中断: query={}", query, e);
            return emptyResults();
        } catch (Exception e) {
            log.error("[WebSearch] DuckDuckGo 搜索失败: query={}", query, e);
            return emptyResults();
        }
    }

    private List<WebSearchOrganicResult> parseResults(String html, int maxResults) {
        List<WebSearchOrganicResult> results = new ArrayList<>();
        Document doc = Jsoup.parse(html);

        // DuckDuckGo HTML 结果条目：class="result results_links results_links_deep web-result"
        Elements resultElements = doc.select(".result.results_links");

        for (Element el : resultElements) {
            if (results.size() >= maxResults) {
                break;
            }

            // 标题 + 链接：.result__a
            Element titleEl = el.selectFirst(".result__a");
            if (titleEl == null) {
                continue;
            }

            String title = titleEl.text().trim();
            String href = titleEl.attr("href");

            // DuckDuckGo 的链接是跳转链接，格式：//duckduckgo.com/l/?uddg=https%3A...
            // 提取真实 URL
            String url = extractRealUrl(href);
            if (url == null || url.isBlank()) {
                continue;
            }

            // 摘要：.result__snippet
            Element snippetEl = el.selectFirst(".result__snippet");
            String snippet = snippetEl != null ? snippetEl.text().trim() : "";

            try {
                results.add(WebSearchOrganicResult.from(
                    title.isBlank() ? url : title,
                    URI.create(url),
                    snippet,
                    null
                ));
            } catch (Exception e) {
                log.debug("[WebSearch] 跳过无效 URL: {}", url);
            }
        }

        return results;
    }

    /**
     * 从 DuckDuckGo 跳转链接中提取真实 URL
     * 格式：/l/?uddg=https%3A%2F%2F...&rut=...
     */
    private String extractRealUrl(String href) {
        if (href == null || href.isBlank()) {
            return null;
        }
        // 绝对链接直接返回
        if (href.startsWith("http://") || href.startsWith("https://")) {
            return href;
        }
        // 解析 uddg 参数
        try {
            String fullUrl = href.startsWith("//") ? "https:" + href : "https://duckduckgo.com" + href;
            URI uri = URI.create(fullUrl);
            String query = uri.getRawQuery();
            if (query == null) {
                return null;
            }
            for (String param : query.split("&")) {
                if (param.startsWith("uddg=")) {
                    return java.net.URLDecoder.decode(
                        param.substring(5), StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            log.debug("[WebSearch] URL 解析失败: {}", href);
        }
        return null;
    }

    private WebSearchResults emptyResults() {
        return WebSearchResults.from(WebSearchInformationResult.from(0L), new ArrayList<>());
    }
}
