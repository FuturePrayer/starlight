package cn.suhoan.starlight.repository;

import cn.suhoan.starlight.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 分类数据访问层。
 * <p>提供笔记分类的持久化操作。</p>
 *
 * @author suhoan
 */
public interface CategoryRepository extends JpaRepository<Category, String> {

    List<Category> findByOwnerIdOrderByNameAsc(String ownerId);

    Optional<Category> findByIdAndOwnerId(String id, String ownerId);

    /** 根据公开站点令牌查找分类 */
    Optional<Category> findBySiteToken(String siteToken);

    /** 查询指定用户下所有已开启星迹书阁的分类 */
    List<Category> findByOwnerIdAndSiteTokenIsNotNull(String ownerId);

    /** 根据父分类 ID 查询直接子分类 */
    List<Category> findByParentId(String parentId);
}

