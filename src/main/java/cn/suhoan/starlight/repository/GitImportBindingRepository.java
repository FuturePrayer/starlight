package cn.suhoan.starlight.repository;

import cn.suhoan.starlight.entity.GitImportBinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Git 导入映射数据访问层。
 */
public interface GitImportBindingRepository extends JpaRepository<GitImportBinding, String> {

    List<GitImportBinding> findBySourceIdOrderByBindingTypeAscRelativePathAsc(String sourceId);

    void deleteBySourceId(String sourceId);
}

