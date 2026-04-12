package cn.suhoan.starlight.repository;

import cn.suhoan.starlight.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 分类数据访问层。
 * <p>提供笔记分类的持久化操作。</p>
 *
 * @author suhoan
 */
public interface CategoryRepository extends JpaRepository<Category, String> {

    /** 查询指定用户仍处于正常状态的分类。 */
    List<Category> findByOwnerIdAndDeletedAtIsNullOrderByNameAsc(String ownerId);

    /** 查询指定用户的全部分类（包含回收站中的分类）。 */
    List<Category> findByOwnerIdOrderByNameAsc(String ownerId);

    /** 查询指定用户仍处于正常状态的分类。 */
    Optional<Category> findByIdAndOwnerIdAndDeletedAtIsNull(String id, String ownerId);

    Optional<Category> findByIdAndOwnerId(String id, String ownerId);

    /** 查询指定用户回收站中的分类。 */
    Optional<Category> findByIdAndOwnerIdAndDeletedAtIsNotNull(String id, String ownerId);

    /** 根据公开站点令牌查找分类 */
    Optional<Category> findBySiteTokenAndDeletedAtIsNull(String siteToken);

    Optional<Category> findBySiteToken(String siteToken);

    /** 查询指定用户下所有已开启星迹书阁的分类 */
    List<Category> findByOwnerIdAndSiteTokenIsNotNullAndDeletedAtIsNull(String ownerId);

    List<Category> findByOwnerIdAndSiteTokenIsNotNull(String ownerId);

    /** 根据父分类 ID 查询直接子分类 */
    List<Category> findByParentIdAndDeletedAtIsNull(String parentId);

    List<Category> findByParentId(String parentId);

    /** 根据多个 ID 批量查询分类。 */
    List<Category> findByIdIn(Collection<String> ids);

    /** 统计指定用户回收站中的分类数量。 */
    long countByOwnerIdAndDeletedAtIsNotNull(String ownerId);

    /** 查询超出保留期的回收站分类。 */
    List<Category> findByDeletedAtBefore(LocalDateTime cutoff);
}

