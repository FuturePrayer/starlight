package cn.suhoan.starlight.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final StarlightProperties starlightProperties;

    public WebConfig(StarlightProperties starlightProperties) {
        this.starlightProperties = starlightProperties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path themePath = Path.of(starlightProperties.getThemeDir()).toAbsolutePath().normalize();
        registry.addResourceHandler("/theme-files/**")
                .addResourceLocations(themePath.toUri().toString());
    }
}

