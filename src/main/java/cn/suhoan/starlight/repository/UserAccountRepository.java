package cn.suhoan.starlight.repository;

import cn.suhoan.starlight.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, String> {

    Optional<UserAccount> findByUsername(String username);

    Optional<UserAccount> findByEmail(String email);

    boolean existsByUsername(String username);

    long countByAdminFlagTrue();
}

