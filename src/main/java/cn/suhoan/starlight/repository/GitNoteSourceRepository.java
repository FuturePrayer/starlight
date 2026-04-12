package cn.suhoan.starlight.repository;

import cn.suhoan.starlight.entity.GitNoteSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Git 导入源数据访问层。
 */
public interface GitNoteSourceRepository extends JpaRepository<GitNoteSource, String> {

    List<GitNoteSource> findByOwnerIdOrderByUpdatedAtDesc(String ownerId);

    Optional<GitNoteSource> findByIdAndOwnerId(String id, String ownerId);

    /** 查询目标分类命中指定分类集合的导入源。 */
    List<GitNoteSource> findByTargetCategoryIdIn(Collection<String> categoryIds);

    List<GitNoteSource> findByAutoSyncEnabledTrueOrderByUpdatedAtAsc();
}

