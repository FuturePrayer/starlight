package cn.suhoan.starlight.service;

import cn.suhoan.starlight.config.StarlightProperties;
import org.springframework.stereotype.Service;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Git 远程仓库地址安全校验。
 * <p>Git 导入会由服务端主动访问用户输入的 URL，因此必须先限制可访问 Host 并拒绝私网/本机地址。</p>
 */
@Service
public class GitRemoteSecurityService {

    private final StarlightProperties starlightProperties;

    public GitRemoteSecurityService(StarlightProperties starlightProperties) {
        this.starlightProperties = starlightProperties;
    }

    public VerifiedRemote verify(String repositoryUrl) {
        URI uri = parseUri(repositoryUrl);
        String scheme = normalizedScheme(uri);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("仅支持 HTTP 或 HTTPS 仓库地址");
        }
        String host = normalizedHost(uri);
        ensureAllowedHost(host);
        ensurePublicAddress(host);
        return new VerifiedRemote(uri, host, allowedHosts(), timeoutSeconds());
    }

    public Set<String> allowedHosts() {
        String value = starlightProperties.getGit().getAllowedHosts();
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        LinkedHashSet<String> hosts = new LinkedHashSet<>();
        Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .map(item -> item.toLowerCase(Locale.ROOT))
                .forEach(hosts::add);
        return hosts;
    }

    public int timeoutSeconds() {
        return Math.clamp(starlightProperties.getGit().getTimeoutSeconds(), 1, 300);
    }

    private URI parseUri(String repositoryUrl) {
        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            throw new IllegalArgumentException("仓库地址不能为空");
        }
        try {
            return URI.create(repositoryUrl.trim());
        } catch (Exception exception) {
            throw new IllegalArgumentException("仓库地址格式不正确");
        }
    }

    private String normalizedScheme(URI uri) {
        return uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    }

    private String normalizedHost(URI uri) {
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("仓库地址格式不正确");
        }
        return host.toLowerCase(Locale.ROOT);
    }

    private void ensureAllowedHost(String host) {
        Set<String> allowedHosts = allowedHosts();
        if (allowedHosts.isEmpty()) {
            throw new IllegalArgumentException("Git 仓库 Host 白名单为空，请先配置 STARLIGHT_GIT_ALLOWED_HOSTS");
        }
        boolean matched = allowedHosts.stream().anyMatch(pattern -> matchesHost(pattern, host));
        if (!matched) {
            throw new IllegalArgumentException("Git 仓库 Host 不在白名单中: " + host);
        }
    }

    private boolean matchesHost(String pattern, String host) {
        if (pattern == null || pattern.isBlank()) {
            return false;
        }
        String normalizedPattern = pattern.toLowerCase(Locale.ROOT);
        if (normalizedPattern.startsWith("*.")) {
            String suffix = normalizedPattern.substring(1);
            return host.endsWith(suffix) && host.length() > suffix.length();
        }
        return host.equals(normalizedPattern);
    }

    private void ensurePublicAddress(String host) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) {
                throw new IllegalArgumentException("无法解析 Git 仓库 Host: " + host);
            }
            for (InetAddress address : addresses) {
                if (!isPublicAddress(address)) {
                    throw new IllegalArgumentException("Git 仓库 Host 解析到了不允许访问的内网或本机地址: " + host);
                }
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("无法解析 Git 仓库 Host: " + host);
        }
    }

    private boolean isPublicAddress(InetAddress address) {
        return !address.isAnyLocalAddress()
                && !address.isLoopbackAddress()
                && !address.isLinkLocalAddress()
                && !address.isSiteLocalAddress()
                && !address.isMulticastAddress()
                && !isCarrierGradeNat(address)
                && !isDocumentationAddress(address)
                && !isUniqueLocalIpv6(address);
    }

    private boolean isCarrierGradeNat(InetAddress address) {
        if (!(address instanceof Inet4Address)) {
            return false;
        }
        byte[] bytes = address.getAddress();
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        return first == 100 && second >= 64 && second <= 127;
    }

    private boolean isDocumentationAddress(InetAddress address) {
        if (!(address instanceof Inet4Address)) {
            return false;
        }
        byte[] bytes = address.getAddress();
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        int third = bytes[2] & 0xff;
        return (first == 192 && second == 0 && third == 2)
                || (first == 198 && second == 51 && third == 100)
                || (first == 203 && second == 0 && third == 113);
    }

    private boolean isUniqueLocalIpv6(InetAddress address) {
        if (!(address instanceof Inet6Address)) {
            return false;
        }
        int first = address.getAddress()[0] & 0xff;
        return (first & 0xfe) == 0xfc;
    }

    public record VerifiedRemote(URI uri, String host, Set<String> allowedHosts, int timeoutSeconds) {
    }
}
