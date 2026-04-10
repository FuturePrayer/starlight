package cn.suhoan.starlight.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 前端单页应用路由转发控制器。
 * <p>用于将浏览器直接访问的前端页面路径统一转发到 {@code index.html}，避免刷新深链接时出现 404。</p>
 */
@Controller
public class PageController {

    private static final Logger log = LoggerFactory.getLogger(PageController.class);

    @GetMapping("/")
    public String index() {
        log.debug("转发首页请求到前端入口");
        return "forward:/index.html";
    }

    @GetMapping({"/login", "/register"})
    public String login() {
        log.debug("转发登录/注册页面请求到前端入口");
        return "forward:/index.html";
    }

    @GetMapping("/app")
    public String app() {
        log.debug("转发主应用请求到前端入口");
        return "forward:/index.html";
    }

    /**
     * 支持通过路径参数直接打开指定笔记。
     *
     * @param noteId 笔记 ID
     */
    @GetMapping("/app/{noteId}")
    public String appNote(@PathVariable String noteId) {
        log.debug("转发笔记深链接到前端入口: noteId={}", noteId);
        return "forward:/index.html";
    }

    @GetMapping("/s/{token}")
    public String share(@PathVariable String token) {
        log.debug("转发分享页请求到前端入口: token={}", token);
        return "forward:/index.html";
    }

    @GetMapping("/site/{token}")
    public String publicSite(@PathVariable String token) {
        log.debug("转发星迹书阁首页请求到前端入口: token={}", token);
        return "forward:/index.html";
    }

    @GetMapping("/site/{token}/{noteId}")
    public String publicSiteNote(@PathVariable String token, @PathVariable String noteId) {
        log.debug("转发星迹书阁文章请求到前端入口: token={}, noteId={}", token, noteId);
        return "forward:/index.html";
    }
}

