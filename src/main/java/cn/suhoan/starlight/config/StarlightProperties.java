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

    /** 笔记回收站相关配置。 */
    private NoteTrash noteTrash = new NoteTrash();

    public String getThemeDir() {
        return themeDir;
    }

    public void setThemeDir(String themeDir) {
        this.themeDir = themeDir;
    }

    public NoteTrash getNoteTrash() {
        return noteTrash;
    }

    public void setNoteTrash(NoteTrash noteTrash) {
        this.noteTrash = noteTrash;
    }

    /**
     * 回收站配置。
     */
    public static class NoteTrash {

        /** 回收站保留天数，默认 30 天。 */
        private int retentionDays = 30;

        /** 自动清理的 Cron 表达式。 */
        private String cleanupCron = "0 20 3 * * *";

        public int getRetentionDays() {
            return retentionDays;
        }

        public void setRetentionDays(int retentionDays) {
            this.retentionDays = retentionDays;
        }

        public String getCleanupCron() {
            return cleanupCron;
        }

        public void setCleanupCron(String cleanupCron) {
            this.cleanupCron = cleanupCron;
        }
    }
}

