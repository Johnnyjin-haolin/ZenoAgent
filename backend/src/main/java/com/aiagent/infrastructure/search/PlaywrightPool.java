package com.aiagent.infrastructure.search;

import com.aiagent.infrastructure.config.AgentConfig;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Playwright 浏览器连接池
 * <p>
 * Spring 单例 Bean，管理 Playwright 实例和 Chromium Browser 的生命周期：
 * - 启动时（@PostConstruct）预热并保持 Browser 常驻，避免每次搜索重启浏览器（300ms+ 开销）
 * - 使用持久化 UserDataDir 保存 Cookie/Session，让 Bing 识别为老用户，返回真实质量结果
 * - 每次请求通过 newContext() 创建独立的隔离上下文，保证并发安全
 * - 应用关闭时（@PreDestroy）优雅关闭 Browser 和 Playwright，释放资源
 * </p>
 *
 * @author aiagent
 */
@Slf4j
@Component
public class PlaywrightPool {

    @Autowired
    private AgentConfig agentConfig;

    private Playwright playwright;
    private Browser browser;

    @PostConstruct
    public void init() {
        AgentConfig.ToolConfig.WebSearchConfig cfg = agentConfig.getTools().getWebSearch();

        if (!"playwright".equalsIgnoreCase(cfg.getEngine())) {
            log.info("[PlaywrightPool] engine={}, 跳过 Playwright 初始化", cfg.getEngine());
            return;
        }

        log.info("[PlaywrightPool] 开始初始化 Playwright，headless={}, userDataDir={}",
            cfg.isHeadless(), cfg.getUserDataDir());

        try {
            playwright = Playwright.create();

            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                .setHeadless(cfg.isHeadless())
                .setArgs(List.of(
                    "--no-sandbox",
                    "--disable-dev-shm-usage",
                    "--disable-blink-features=AutomationControlled",  // 隐藏自动化特征
                    "--disable-infobars",
                    "--disable-extensions",
                    "--disable-gpu",
                    "--window-size=1920,1080"
                ));

            if (cfg.getUserDataDir() != null && !cfg.getUserDataDir().isBlank()) {
                // 使用持久化 Context：保存 Cookie/Session，让 Bing 认为是老用户
                Path userDataPath = Paths.get(cfg.getUserDataDir()).toAbsolutePath();
                log.info("[PlaywrightPool] 使用持久化用户数据目录: {}", userDataPath);
                BrowserContext persistentCtx = playwright.chromium()
                    .launchPersistentContext(userDataPath, new BrowserType.LaunchPersistentContextOptions()
                        .setHeadless(cfg.isHeadless())
                        .setUserAgent(cfg.getUserAgent())
                        .setViewportSize(1920, 1080)
                        .setArgs(List.of(
                            "--no-sandbox",
                            "--disable-dev-shm-usage",
                            "--disable-blink-features=AutomationControlled",
                            "--disable-infobars",
                            "--disable-extensions",
                            "--disable-gpu"
                        ))
                    );
                // persistentCtx 本身就是一个 BrowserContext，复用它
                this.browser = persistentCtx.browser();
                // 关闭 persistentCtx 对应的默认页，让后续通过 browser.newContext() 创建独立 Context
                persistentCtx.pages().forEach(Page::close);
                persistentCtx.close();
            }

            // 如果没有使用持久化或持久化失败，直接 launch
            if (this.browser == null) {
                this.browser = playwright.chromium().launch(launchOptions);
            }

            log.info("[PlaywrightPool] Playwright 初始化完成");
        } catch (Exception e) {
            log.error("[PlaywrightPool] Playwright 初始化失败，将降级使用 HTTP 模式", e);
            this.browser = null;
        }
    }

    /**
     * 创建新的浏览器页面（独立 Context，并发安全）
     * 调用方负责在 try-with-resources 或 finally 中调用 page.context().close()
     *
     * @param userAgent 使用的 User-Agent
     * @return 新页面实例，已注入反检测脚本
     */
    public Page newPage(String userAgent) {
        if (browser == null) {
            throw new IllegalStateException("Playwright Browser 未初始化，请检查启动日志");
        }

        AgentConfig.ToolConfig.WebSearchConfig cfg = agentConfig.getTools().getWebSearch();

        // 尝试使用持久化 Context（复用已有 Cookie）
        Path userDataPath = (cfg.getUserDataDir() != null && !cfg.getUserDataDir().isBlank())
            ? Paths.get(cfg.getUserDataDir()).toAbsolutePath()
            : null;

        BrowserContext ctx;
        if (userDataPath != null) {
            try {
                ctx = playwright.chromium().launchPersistentContext(
                    userDataPath,
                    new BrowserType.LaunchPersistentContextOptions()
                        .setHeadless(cfg.isHeadless())
                        .setUserAgent(userAgent)
                        .setViewportSize(1920, 1080)
                        .setArgs(List.of(
                            "--no-sandbox",
                            "--disable-dev-shm-usage",
                            "--disable-blink-features=AutomationControlled"
                        ))
                );
            } catch (Exception e) {
                log.warn("[PlaywrightPool] 持久化 Context 创建失败，使用临时 Context: {}", e.getMessage());
                ctx = browser.newContext(new Browser.NewContextOptions()
                    .setUserAgent(userAgent)
                    .setViewportSize(1920, 1080));
            }
        } else {
            ctx = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent(userAgent)
                .setViewportSize(1920, 1080));
        }

        Page page = ctx.newPage();

        // 注入反检测脚本：覆盖 navigator.webdriver，让 Bing 无法通过 JS 检测自动化
        page.addInitScript(
            "Object.defineProperty(navigator, 'webdriver', {get: () => undefined});\n"
            + "window.chrome = {runtime: {}};\n"
            + "Object.defineProperty(navigator, 'plugins', {get: () => [1, 2, 3, 4, 5]});\n"
            + "Object.defineProperty(navigator, 'languages', {get: () => ['zh-CN', 'zh', 'en']});"
        );

        return page;
    }

    /**
     * 关闭页面及其所属 Context
     */
    public void closePage(Page page) {
        if (page == null) {
            return;
        }
        try {
            BrowserContext ctx = page.context();
            page.close();
            ctx.close();
        } catch (Exception e) {
            log.debug("[PlaywrightPool] 关闭页面时发生异常（可忽略）: {}", e.getMessage());
        }
    }

    /**
     * 判断 Playwright 是否可用
     */
    public boolean isAvailable() {
        return browser != null;
    }

    @PreDestroy
    public void destroy() {
        log.info("[PlaywrightPool] 开始关闭 Playwright...");
        if (browser != null) {
            try {
                browser.close();
            } catch (Exception e) {
                log.warn("[PlaywrightPool] 关闭 Browser 异常: {}", e.getMessage());
            }
        }
        if (playwright != null) {
            try {
                playwright.close();
            } catch (Exception e) {
                log.warn("[PlaywrightPool] 关闭 Playwright 异常: {}", e.getMessage());
            }
        }
        log.info("[PlaywrightPool] Playwright 已关闭");
    }
}
