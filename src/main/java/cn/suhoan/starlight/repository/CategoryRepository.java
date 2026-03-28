package cn.suhoan.starlight.repository;

import cn.suhoan.starlight.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 分类数据访问层。
 * <p>提供笔记分类的持久化操作。</p>
 */
public interface CategoryRepository extends JpaRepository<Category, String> {

    List<Category> findByOwnerIdOrderByNameAsc(String ownerId);

    Optional<Category> findByIdAndOwnerId(String id, String ownerId);
}

