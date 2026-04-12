package cn.suhoan.starlight.repository;

import cn.suhoan.starlight.entity.GitSyncHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Git 同步历史数据访问层。
 */
public interface GitSyncHistoryRepository extends JpaRepository<GitSyncHistory, String> {

    List<GitSyncHistory> findBySourceIdOrderByStartedAtDesc(String sourceId);

    List<GitSyncHistory> findTop5BySourceIdOrderByStartedAtDesc(String sourceId);

    /** 删除指定导入源的全部同步历史。 */
    void deleteBySourceId(String sourceId);
}

