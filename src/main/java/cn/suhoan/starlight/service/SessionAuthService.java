package cn.suhoan.starlight.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.suhoan.starlight.entity.UserAccount;
import cn.suhoan.starlight.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * 会话认证服务。
 * <p>基于 Sa-Token 实现的用户会话管理，提供登录、登出、权限校验等功能。</p>
 *
 * @author suhoan
 */
@Service
public class SessionAuthService {

    private static final Logger log = LoggerFactory.getLogger(SessionAuthService.class);

    private final UserAccountRepository userAccountRepository;

    public SessionAuthService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    /**
     * 将用户注册到当前会话中（登录）。
     *
     * @param userId 用户 ID
     */
    public void login(String userId) {
        log.info("会话登录: userId={}", userId);
        StpUtil.login(userId);
    }

    /**
     * 退出当前会话（登出）。
     */
    public void logout() {
        String userId = StpUtil.isLogin() ? StpUtil.getLoginIdAsString() : "unknown";
        log.info("会话登出: userId={}", userId);
        StpUtil.logout();
    }

    /**
     * 获取当前会话的用户 ID。
     *
     * @return 当前登录用户的 ID
     * @throws ResponseStatusException 当用户未登录时
     */
    public String getCurrentUserId() {
        if (!StpUtil.isLogin()) {
            log.warn("未登录用户尝试访问受保护资源");
            throw new ResponseStatusException(UNAUTHORIZED, "请先登录");
        }
        return StpUtil.getLoginIdAsString();
    }

    /**
     * 获取当前登录用户的完整实体，必须已登录。
     *
     * @return 当前登录用户的 UserAccount
     * @throws ResponseStatusException 当用户未登录或账户不存在时
     */
    public UserAccount requireUser() {
        String userId = getCurrentUserId();
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("登录状态已失效，用户不存在: userId={}", userId);
                    return new ResponseStatusException(UNAUTHORIZED, "登录状态已失效，请重新登录");
                });
    }

    /**
     * 获取当前登录用户并验证管理员权限。
     *
     * @return 具有管理员权限的 UserAccount
     * @throws ResponseStatusException 当用户未登录或不是管理员时
     */
    public UserAccount requireAdmin() {
        UserAccount userAccount = requireUser();
        if (!userAccount.isAdminFlag()) {
            log.warn("非管理员尝试访问管理资源: userId={}", userAccount.getId());
            throw new ResponseStatusException(FORBIDDEN, "需要管理员权限");
        }
        return userAccount;
    }

    /**
     * 根据用户 ID 查找用户。
     *
     * @param userId 用户 ID
     * @return 用户实体
     * @throws ResponseStatusException 当用户不存在时
     */
    public UserAccount findUserById(String userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("用户不存在: userId={}", userId);
                    return new ResponseStatusException(UNAUTHORIZED, "用户不存在");
                });
    }
}
