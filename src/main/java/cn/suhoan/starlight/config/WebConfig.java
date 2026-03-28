package cn.suhoan.starlight.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * Web MVC 配置类。
 * <p>主要配置静态资源映射，将外部主题目录映射为可通过 HTTP 访问的资源路径。</p>
 *
 * @author suhoan
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

    private final StarlightProperties starlightProperties;

    public WebConfig(StarlightProperties starlightProperties) {
        this.starlightProperties = starlightProperties;
    }

    /**
     * 注册外部主题目录为静态资源路径。
     * <p>将 /theme-files/** 请求映射到配置的外部主题目录，使自定义主题的 CSS 文件可被前端加载。</p>
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path themePath = Path.of(starlightProperties.getThemeDir()).toAbsolutePath().normalize();
        log.info("注册外部主题资源目录: /theme-files/** -> {}", themePath);
        registry.addResourceHandler("/theme-files/**")
                .addResourceLocations(themePath.toUri().toString());
    }
}

