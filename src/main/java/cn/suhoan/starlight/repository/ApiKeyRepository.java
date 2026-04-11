package cn.suhoan.starlight.repository;

import cn.suhoan.starlight.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * API Key 数据访问层。
 */
public interface ApiKeyRepository extends JpaRepository<ApiKey, String> {

    List<ApiKey> findByOwnerIdOrderByUpdatedAtDesc(String ownerId);

    Optional<ApiKey> findByIdAndOwnerId(String id, String ownerId);

    Optional<ApiKey> findBySecretHash(String secretHash);
}

