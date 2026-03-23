package cn.suhoan.startlight.repository;

import cn.suhoan.startlight.entity.UserCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserCredentialRepository extends JpaRepository<UserCredential, String> {

    List<UserCredential> findByUserId(String userId);

    Optional<UserCredential> findByCredentialId(String credentialId);

    List<UserCredential> findAllByCredentialId(String credentialId);

    long countByUserId(String userId);

    void deleteAllByUserId(String userId);
}

