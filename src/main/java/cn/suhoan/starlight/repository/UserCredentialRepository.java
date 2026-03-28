package cn.suhoan.starlight.repository;

import cn.suhoan.starlight.entity.UserCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 用户通行密钥凭据数据访问层。
 * <p>提供 WebAuthn 通行密钥的持久化操作。</p>
 *
 * @author suhoan
 */
public interface UserCredentialRepository extends JpaRepository<UserCredential, String> {

    List<UserCredential> findByUserId(String userId);

    Optional<UserCredential> findByCredentialId(String credentialId);

    List<UserCredential> findAllByCredentialId(String credentialId);

    long countByUserId(String userId);

    void deleteAllByUserId(String userId);
}

