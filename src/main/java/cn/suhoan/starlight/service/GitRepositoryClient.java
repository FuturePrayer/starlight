package cn.suhoan.starlight.service;

import java.nio.file.Path;
import java.util.List;

/**
 * Git 仓库访问抽象。
 * <p>将分支查询与浅克隆能力隔离出来，便于后续替换实现或编写测试。</p>
 */
public interface GitRepositoryClient {

    /** 查询远程仓库全部分支。 */
    List<String> listBranches(String repositoryUrl);

    /** 查询指定分支当前对应的提交 ID。 */
    String resolveBranchHeadCommit(String repositoryUrl, String branchName);

    /** 浅克隆指定分支到目标目录。 */
    ClonedRepository shallowClone(String repositoryUrl, String branchName, Path targetDirectory);

    /** 克隆后的仓库信息。 */
    record ClonedRepository(Path workingDirectory, String branchName, String headCommitId) {
    }
}

