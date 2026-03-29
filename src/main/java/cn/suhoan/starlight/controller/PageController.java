package cn.suhoan.starlight.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class PageController {

    @GetMapping("/")
    public String index() {
        return "forward:/index.html";
    }

    @GetMapping({"/login", "/register"})
    public String login() {
        return "forward:/index.html";
    }

    @GetMapping("/app")
    public String app() {
        return "forward:/index.html";
    }

    @GetMapping("/s/{token}")
    public String share(@PathVariable String token) {
        return "forward:/index.html";
    }

    @GetMapping("/site/{token}")
    public String publicSite(@PathVariable String token) {
        return "forward:/index.html";
    }

    @GetMapping("/site/{token}/{noteId}")
    public String publicSiteNote(@PathVariable String token, @PathVariable String noteId) {
        return "forward:/index.html";
    }
}

