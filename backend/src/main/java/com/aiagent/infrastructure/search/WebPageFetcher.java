package com.aiagent.infrastructure.search;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 网页正文抓取器
 * <p>
 * 使用 JDK HttpClient 发起 HTTP 请求，配合 Jsoup 解析 HTML，
 * 提取网页主体正文内容（去除 script/style/nav/header/footer 等噪音）。
 * 不依赖无头浏览器，适合纯文本页面（新闻、文档、博客等）。
 * </p>
 *
 * @author aiagent
 */
@Slf4j
public class WebPageFetcher {

    private static final int DEFAULT_MAX_CHARS = 5000;

    private final HttpClient httpClient;
    private final int timeoutSeconds;
    private final String userAgent;
    private final int maxContentChars;

    public WebPageFetcher(int timeoutSeconds, String userAgent, int maxContentChars) {
        this.timeoutSeconds = timeoutSeconds;
        this.userAgent = userAgent;
        this.maxContentChars = maxContentChars;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(timeoutSeconds))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    /**
     * 抓取指定 URL 的网页正文（默认截断）
     *
     * @param url 目标网页 URL
     * @return 提取后的纯文本正文（超过 maxContentChars 会截断）
     */
    public String fetch(String url) {
        return fetch(url, true);
    }

    /**
     * 抓取指定 URL 的网页正文
     *
     * @param url      目标网页 URL
     * @param truncate true=截断至 maxContentChars 字符；false=返回完整正文
     * @return 提取后的纯文本正文
     */
    public String fetch(String url, boolean truncate) {
        log.info("[FetchUrl] 开始抓取: url={}, truncate={}", url, truncate);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();

            if (statusCode < 200 || statusCode >= 300) {
                log.warn("[FetchUrl] HTTP 错误: url={}, statusCode={}", url, statusCode);
                return "无法获取网页内容，HTTP 状态码: " + statusCode;
            }

            String html = response.body();
            String text = extractMainText(html, url);

            if (text.isBlank()) {
                return "未能从网页中提取到有效文本内容（可能是 JavaScript 动态渲染页面）。";
            }

            int totalChars = text.length();
            if (truncate && totalChars > maxContentChars) {
                log.debug("[FetchUrl] 正文超长，截断至 {} 字符（原文 {} 字符）", maxContentChars, totalChars);
                text = text.substring(0, maxContentChars)
                    + "\n\n[内容已截断，原文共约 " + totalChars + " 字符。如需完整内容，请以 truncate=false 重新调用。]";
            }

            log.info("[FetchUrl] 抓取成功: url={}, 截断={}, 返回字符数={}", url, truncate, text.length());
            return text;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[FetchUrl] 抓取被中断: {}", url, e);
            return "网页抓取被中断: " + e.getMessage();
        } catch (Exception e) {
            log.error("[FetchUrl] 抓取失败: {}", url, e);
            return "网页抓取失败: " + e.getMessage();
        }
    }

    /**
     * 从 HTML 中提取主要正文文本
     * 优先提取 article / main / [role=main]，降级提取 body
     */
    private String extractMainText(String html, String baseUrl) {
        Document doc = Jsoup.parse(html, baseUrl);

        // 移除噪音标签
        doc.select("script, style, nav, header, footer, aside, iframe, noscript, "
            + ".nav, .navigation, .menu, .sidebar, .ad, .ads, .advertisement, "
            + "#nav, #navigation, #menu, #sidebar, #header, #footer").remove();

        // 优先从语义化标签中提取正文
        Element mainEl = doc.selectFirst("article");
        if (mainEl == null) {
            mainEl = doc.selectFirst("main");
        }
        if (mainEl == null) {
            mainEl = doc.selectFirst("[role=main]");
        }
        if (mainEl == null) {
            // 尝试 content class 常见名称
            for (String selector : new String[]{"#content", ".content", "#main-content", ".main-content", ".post-content", ".entry-content"}) {
                mainEl = doc.selectFirst(selector);
                if (mainEl != null) {
                    break;
                }
            }
        }

        // 最终降级到 body
        if (mainEl == null) {
            mainEl = doc.body();
        }

        if (mainEl == null) {
            return "";
        }

        // 提取纯文本，保留段落分隔
        String text = extractStructuredText(mainEl);
        return text.trim();
    }

    /**
     * 结构化提取文本，保留段落换行
     */
    private String extractStructuredText(Element el) {
        StringBuilder sb = new StringBuilder();
        String[] blockTags = {"p", "h1", "h2", "h3", "h4", "h5", "h6", "li", "td", "th", "div", "section", "blockquote"};

        Elements blocks = el.select(String.join(", ", blockTags));
        if (blocks.isEmpty()) {
            return el.text();
        }

        for (Element block : blocks) {
            String text = block.ownText().trim();
            if (!text.isBlank()) {
                sb.append(text).append("\n");
            }
        }

        // 如果块级提取为空，回退到完整文本
        String result = sb.toString().trim();
        if (result.isBlank()) {
            result = el.text();
        }
        return result;
    }
}
