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

    void deleteByApiKeyId(String apiKeyId);
}

