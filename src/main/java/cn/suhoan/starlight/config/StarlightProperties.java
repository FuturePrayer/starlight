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

    /** 笔记回收站相关配置。 */
    private NoteTrash noteTrash = new NoteTrash();

    /** 图片资产相关配置。 */
    private Assets assets = new Assets();

    /** Git 导入相关配置。 */
    private Git git = new Git();

    public NoteTrash getNoteTrash() {
        return noteTrash;
    }

    public void setNoteTrash(NoteTrash noteTrash) {
        this.noteTrash = noteTrash;
    }

    public Assets getAssets() {
        return assets;
    }

    public void setAssets(Assets assets) {
        this.assets = assets;
    }

    public Git getGit() {
        return git;
    }

    public void setGit(Git git) {
        this.git = git;
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

    /**
     * 图片资产配置。
     */
    public static class Assets {

        /** 单文件最大大小，默认 10 MiB。 */
        private long maxFileSize = 10 * 1024 * 1024;

        /** 允许上传的 MIME 类型，逗号分隔。 */
        private String allowedTypes = "image/png,image/jpeg,image/webp,image/gif,image/avif";

        /** 本地存储配置。 */
        private Local local = new Local();

        /** S3 兼容存储配置。 */
        private S3 s3 = new S3();

        public long getMaxFileSize() {
            return maxFileSize;
        }

        public void setMaxFileSize(long maxFileSize) {
            this.maxFileSize = maxFileSize;
        }

        public String getAllowedTypes() {
            return allowedTypes;
        }

        public void setAllowedTypes(String allowedTypes) {
            this.allowedTypes = allowedTypes;
        }

        public Local getLocal() {
            return local;
        }

        public void setLocal(Local local) {
            this.local = local;
        }

        public S3 getS3() {
            return s3;
        }

        public void setS3(S3 s3) {
            this.s3 = s3;
        }

        public static class Local {

            /** 本地资产根目录。 */
            private String root = "./data/assets";

            public String getRoot() {
                return root;
            }

            public void setRoot(String root) {
                this.root = root;
            }
        }

        public static class S3 {

            private String bucket = "";
            private String region = "";
            private String endpoint = "";
            private String accessKey = "";
            private String secretKey = "";
            private boolean pathStyleAccess = false;
            private String prefix = "assets";

            public String getBucket() {
                return bucket;
            }

            public void setBucket(String bucket) {
                this.bucket = bucket;
            }

            public String getRegion() {
                return region;
            }

            public void setRegion(String region) {
                this.region = region;
            }

            public String getEndpoint() {
                return endpoint;
            }

            public void setEndpoint(String endpoint) {
                this.endpoint = endpoint;
            }

            public String getAccessKey() {
                return accessKey;
            }

            public void setAccessKey(String accessKey) {
                this.accessKey = accessKey;
            }

            public String getSecretKey() {
                return secretKey;
            }

            public void setSecretKey(String secretKey) {
                this.secretKey = secretKey;
            }

            public boolean isPathStyleAccess() {
                return pathStyleAccess;
            }

            public void setPathStyleAccess(boolean pathStyleAccess) {
                this.pathStyleAccess = pathStyleAccess;
            }

            public String getPrefix() {
                return prefix;
            }

            public void setPrefix(String prefix) {
                this.prefix = prefix;
            }
        }
    }

    /**
     * Git 导入配置。
     */
    public static class Git {

        /** 允许访问的 Git 仓库 Host，逗号分隔，支持通配符前缀 {@code *.example.com}。 */
        private String allowedHosts = "github.com,*.github.com,gitee.com,*.gitee.com,gitlab.com,*.gitlab.com,bitbucket.org,*.bitbucket.org,codeberg.org,*.codeberg.org,sourceforge.net,*.sourceforge.net,git.sr.ht,*.sr.ht,atomgit.com,*.atomgit.com,gitcode.com,*.gitcode.com";

        /** JGit 远程访问超时时间，单位秒。 */
        private int timeoutSeconds = 20;

        public String getAllowedHosts() {
            return allowedHosts;
        }

        public void setAllowedHosts(String allowedHosts) {
            this.allowedHosts = allowedHosts;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }
}

