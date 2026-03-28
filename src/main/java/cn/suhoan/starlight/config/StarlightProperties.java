package cn.suhoan.starlight.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Starlight 应用自定义配置属性。
 * <p>通过 {@code starlight.*} 前缀从 application.yml 中读取配置项。</p>
 *
 * @author suhoan
 */
@Component
@ConfigurationProperties(prefix = "starlight")
public class StarlightProperties {

    /** 外部主题文件目录路径，默认为 "themes" */
    private String themeDir = "themes";

    public String getThemeDir() {
        return themeDir;
    }

    public void setThemeDir(String themeDir) {
        this.themeDir = themeDir;
    }
}

