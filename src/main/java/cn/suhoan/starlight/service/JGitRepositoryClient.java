package cn.suhoan.starlight.service;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 基于 JGit 的仓库访问实现。
 * <p>优先使用浅克隆和指定分支，减少网络开销并避免依赖外部 git 命令。</p>
 */
@Service
public class JGitRepositoryClient implements GitRepositoryClient {

    private static final Logger log = LoggerFactory.getLogger(JGitRepositoryClient.class);
    private static final String HEADS_PREFIX = "refs/heads/";

    @Override
    public List<String> listBranches(String repositoryUrl) {
        GitRemoteContext context = GitRemoteContext.from(repositoryUrl);
        try {
            LsRemoteCommand command = Git.lsRemoteRepository()
                    .setRemote(context.sanitizedRemoteUrl())
                    .setHeads(true);
            context.optionalCredentialsProvider().ifPresent(command::setCredentialsProvider);
            List<String> branches = command.call().stream()
                    .map(Ref::getName)
                    .filter(name -> name != null && name.startsWith(HEADS_PREFIX))
                    .map(name -> name.substring(HEADS_PREFIX.length()))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
            log.info("Git 分支解析完成: repository={}, branchCount={}", context.maskedRemoteUrl(), branches.size());
            return branches;
        } catch (Exception exception) {
            log.warn("Git 分支解析失败: repository={}", context.maskedRemoteUrl(), exception);
            throw new IllegalArgumentException("无法解析该仓库分支，请检查仓库地址、权限或网络连接");
        }
    }

    @Override
    public String resolveBranchHeadCommit(String repositoryUrl, String branchName) {
        GitRemoteContext context = GitRemoteContext.from(repositoryUrl);
        try {
            LsRemoteCommand command = Git.lsRemoteRepository()
                    .setRemote(context.sanitizedRemoteUrl())
                    .setHeads(true);
            context.optionalCredentialsProvider().ifPresent(command::setCredentialsProvider);
            Map<String, String> branchToCommit = command.call().stream()
                    .filter(ref -> ref.getName() != null && ref.getName().startsWith(HEADS_PREFIX))
                    .collect(Collectors.toMap(
                            ref -> ref.getName().substring(HEADS_PREFIX.length()),
                            ref -> ref.getObjectId() == null ? "" : ref.getObjectId().name(),
                            (left, right) -> left
                    ));
            String commitId = branchToCommit.get(branchName == null ? "" : branchName.trim());
            if (commitId == null || commitId.isBlank()) {
                throw new IllegalArgumentException("指定分支不存在");
            }
            return commitId;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("Git 提交解析失败: repository={}, branch={}", context.maskedRemoteUrl(), branchName, exception);
            throw new IllegalArgumentException("无法读取远程仓库最新提交，请稍后重试");
        }
    }

    @Override
    public ClonedRepository shallowClone(String repositoryUrl, String branchName, Path targetDirectory) {
        GitRemoteContext context = GitRemoteContext.from(repositoryUrl);
        try {
            CloneCommand command = Git.cloneRepository()
                    .setURI(context.sanitizedRemoteUrl())
                    .setDirectory(targetDirectory.toFile())
                    .setCloneAllBranches(false)
                    .setDepth(1)
                    .setBranch(HEADS_PREFIX + branchName)
                    .setBranchesToClone(List.of(HEADS_PREFIX + branchName));
            context.optionalCredentialsProvider().ifPresent(command::setCredentialsProvider);
            try (Git git = command.call()) {
                ObjectId head = git.getRepository().resolve("HEAD");
                String commitId = head == null ? "" : head.name();
                log.info("Git 浅克隆完成: repository={}, branch={}, commitId={}", context.maskedRemoteUrl(), branchName, abbreviate(commitId));
                return new ClonedRepository(targetDirectory, branchName, commitId);
            }
        } catch (Exception exception) {
            log.warn("Git 浅克隆失败: repository={}, branch={}", context.maskedRemoteUrl(), branchName, exception);
            throw new IllegalArgumentException("克隆仓库失败，请检查仓库地址、分支、权限或网络连接");
        }
    }

    private String abbreviate(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.length() <= 12 ? value : value.substring(0, 12);
    }

    /**
     * Git 远程上下文。
     * <p>将日志展示地址与真实鉴权信息拆开，避免把认证信息写入日志。</p>
     */
    private record GitRemoteContext(String originalRemoteUrl,
                                    String sanitizedRemoteUrl,
                                    String maskedRemoteUrl,
                                    CredentialsProvider credentialsProvider) {

        private static GitRemoteContext from(String remoteUrl) {
            if (remoteUrl == null || remoteUrl.isBlank()) {
                throw new IllegalArgumentException("仓库地址不能为空");
            }
            try {
                URI uri = URI.create(remoteUrl.trim());
                String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
                if (!"http".equals(scheme) && !"https".equals(scheme)) {
                    throw new IllegalArgumentException("仅支持 HTTP 或 HTTPS 仓库地址");
                }
                if (uri.getHost() == null || uri.getHost().isBlank()) {
                    throw new IllegalArgumentException("仓库地址格式不正确");
                }
                String userInfo = uri.getUserInfo();
                String sanitizedRemoteUrl = uri.getScheme() + "://" + uri.getHost()
                        + (uri.getPort() > 0 ? ":" + uri.getPort() : "")
                        + (uri.getRawPath() == null ? "" : uri.getRawPath())
                        + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery())
                        + (uri.getRawFragment() == null ? "" : "#" + uri.getRawFragment());
                String maskedRemoteUrl = userInfo == null || userInfo.isBlank()
                        ? sanitizedRemoteUrl
                        : sanitizedRemoteUrl.replaceFirst("://", "://***@");
                CredentialsProvider credentialsProvider = null;
                if (userInfo != null && !userInfo.isBlank()) {
                    String[] parts = userInfo.split(":", 2);
                    String username = decode(parts[0]);
                    String password = parts.length > 1 ? decode(parts[1]) : "";
                    credentialsProvider = new UsernamePasswordCredentialsProvider(username, password);
                }
                return new GitRemoteContext(remoteUrl.trim(), sanitizedRemoteUrl, maskedRemoteUrl, credentialsProvider);
            } catch (Exception exception) {
                throw new IllegalArgumentException("仓库地址格式不正确");
            }
        }

        private static String decode(String value) {
            return URLDecoder.decode(value == null ? "" : value, StandardCharsets.UTF_8);
        }

        private java.util.Optional<CredentialsProvider> optionalCredentialsProvider() {
            return java.util.Optional.ofNullable(credentialsProvider);
        }
    }
}

