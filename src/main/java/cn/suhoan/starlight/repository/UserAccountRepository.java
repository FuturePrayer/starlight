package cn.suhoan.starlight.repository;

import cn.suhoan.starlight.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 用户账户数据访问层。
 * <p>提供用户账户的持久化操作，包括按邮箱、用户名查询和管理员计数。</p>
 *
 * @author suhoan
 */
public interface UserAccountRepository extends JpaRepository<UserAccount, String> {

    Optional<UserAccount> findByUsername(String username);

    Optional<UserAccount> findByEmail(String email);

    boolean existsByUsername(String username);

    long countByAdminFlagTrue();
}

