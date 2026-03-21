package cn.suhoan.startlight.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.suhoan.startlight.entity.UserAccount;
import cn.suhoan.startlight.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class SessionAuthService {

    private final UserAccountRepository userAccountRepository;

    public SessionAuthService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    public void login(String userId) {
        StpUtil.login(userId);
    }

    public void logout() {
        StpUtil.logout();
    }

    public String getCurrentUserId() {
        if (!StpUtil.isLogin()) {
             throw new ResponseStatusException(UNAUTHORIZED, "请先登录");
        }
        return StpUtil.getLoginIdAsString();
    }

    public UserAccount requireUser() {
        String userId = getCurrentUserId();
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "登录状态已失效，请重新登录"));
    }

    public UserAccount requireAdmin() {
        UserAccount userAccount = requireUser();
        if (!userAccount.isAdminFlag()) {
            throw new ResponseStatusException(FORBIDDEN, "需要管理员权限");
        }
        return userAccount;
    }
}
