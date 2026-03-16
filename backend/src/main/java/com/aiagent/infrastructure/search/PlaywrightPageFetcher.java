package com.aiagent.infrastructure.search;

import com.aiagent.infrastructure.config.AgentConfig;
import com.microsoft.playwright.Page;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 基于 Playwright 真实无头浏览器的网页正文抓取器
 * <p>
 * 相比 {@link WebPageFetcher}（JDK HttpClient 静态 HTML），本实现使用真实 Chromium：
 * - 支持 JavaScript 动态渲染的单页应用（SPA）
 * - 等待页面完全加载后再提取内容，避免内容不完整
 * - 携带真实浏览器 Cookie/Session，可访问需要登录的页面
 * </p>
 *
 * @author aiagent
 */
@Slf4j
@Component
public class PlaywrightPageFetcher {

    @Autowired
    private PlaywrightPool playwrightPool;

    @Autowired
    private AgentConfig agentConfig;

    /**
     * 抓取指定 URL 的网页正文（默认截断）
     */
    public String fetch(String url) {
        return fetch(url, true);
    }

    /**
     * 抓取指定 URL 的网页正文
     *
     * @param url      目标网页 URL
     * @param truncate true=截断至 maxContentChars；false=返回完整正文
     * @return 提取后的纯文本正文
     */
    public String fetch(String url, boolean truncate) {
        AgentConfig.ToolConfig.WebSearchConfig cfg = agentConfig.getTools().getWebSearch();
        log.info("[PlaywrightFetch] 开始抓取: url={}, truncate={}", url, truncate);

        Page page = null;
        try {
            page = playwrightPool.newPage(cfg.getUserAgent());

            int timeoutMs = cfg.getTimeoutSeconds() * 1000;

            // 导航并等待页面加载完成
            page.navigate(url, new Page.NavigateOptions().setTimeout(timeoutMs));

            // 等待 body 可见（确保 DOM 基本加载完毕）
            try {
                page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED,
                    new Page.WaitForLoadStateOptions().setTimeout(timeoutMs));
            } catch (Exception e) {
                log.debug("[PlaywrightFetch] waitForLoadState 超时，继续尝试提取: {}", e.getMessage());
            }

            String html = page.content();

            if (html == null || html.isBlank()) {
                return "未能获取网页内容（页面为空）。";
            }

            String text = extractMainText(html, url);

            if (text.isBlank()) {
                return "未能从网页中提取到有效文本内容。";
            }

            int totalChars = text.length();
            int maxChars = cfg.getMaxContentChars();

            if (truncate && totalChars > maxChars) {
                log.debug("[PlaywrightFetch] 正文超长，截断至 {} 字符（原文 {} 字符）", maxChars, totalChars);
                text = text.substring(0, maxChars)
                    + "\n\n[内容已截断，原文共约 " + totalChars + " 字符。如需完整内容，请以 truncate=false 重新调用。]";
            }

            log.info("[PlaywrightFetch] 抓取成功: url={}, 截断={}, 返回字符数={}", url, truncate, text.length());
            return text;

        } catch (Exception e) {
            log.error("[PlaywrightFetch] 抓取失败: {}", url, e);
            return "网页抓取失败: " + e.getMessage();
        } finally {
            playwrightPool.closePage(page);
        }
    }

    /**
     * 从 HTML 中提取主要正文文本（与 WebPageFetcher 逻辑一致）
     */
    private String extractMainText(String html, String baseUrl) {
        Document doc = Jsoup.parse(html, baseUrl);

        doc.select("script, style, nav, header, footer, aside, iframe, noscript, "
            + ".nav, .navigation, .menu, .sidebar, .ad, .ads, .advertisement, "
            + "#nav, #navigation, #menu, #sidebar, #header, #footer").remove();

        Element mainEl = doc.selectFirst("article");
        if (mainEl == null) mainEl = doc.selectFirst("main");
        if (mainEl == null) mainEl = doc.selectFirst("[role=main]");
        if (mainEl == null) {
            for (String selector : new String[]{"#content", ".content", "#main-content", ".main-content", ".post-content", ".entry-content"}) {
                mainEl = doc.selectFirst(selector);
                if (mainEl != null) break;
            }
        }
        if (mainEl == null) mainEl = doc.body();
        if (mainEl == null) return "";

        return extractStructuredText(mainEl).trim();
    }

    private String extractStructuredText(Element el) {
        StringBuilder sb = new StringBuilder();
        String[] blockTags = {"p", "h1", "h2", "h3", "h4", "h5", "h6", "li", "td", "th", "div", "section", "blockquote"};
        Elements blocks = el.select(String.join(", ", blockTags));

        if (blocks.isEmpty()) return el.text();

        for (Element block : blocks) {
            String text = block.ownText().trim();
            if (!text.isBlank()) sb.append(text).append("\n");
        }

        String result = sb.toString().trim();
        return result.isBlank() ? el.text() : result;
    }
}
