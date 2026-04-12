package cn.suhoan.starlight.repository;

import cn.suhoan.starlight.entity.ApiKeyScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * API Key 授权范围数据访问层。
 */
public interface ApiKeyScopeRepository extends JpaRepository<ApiKeyScope, String> {

    List<ApiKeyScope> findByApiKeyIdOrderByCreatedAtAsc(String apiKeyId);

    List<ApiKeyScope> findByApiKeyIdIn(Collection<String> apiKeyIds);

    /** 查询引用了指定分类的授权范围。 */
    List<ApiKeyScope> findByCategoryIdIn(Collection<String> categoryIds);

    void deleteByApiKeyId(String apiKeyId);

    /** 删除引用指定分类集合的授权范围。 */
    void deleteByCategoryIdIn(Collection<String> categoryIds);
}

