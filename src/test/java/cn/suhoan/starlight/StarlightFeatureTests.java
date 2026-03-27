package cn.suhoan.starlight;

import cn.suhoan.starlight.controller.AuthController;
import cn.suhoan.starlight.dto.ApiResponse;
import cn.suhoan.starlight.entity.Note;
import cn.suhoan.starlight.entity.UserAccount;
import cn.suhoan.starlight.service.AuthService;
import cn.suhoan.starlight.service.NoteService;
import cn.suhoan.starlight.service.SettingsService;
import cn.suhoan.starlight.service.ShareService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class StarlightFeatureTests {

    @Autowired
    private AuthService authService;

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private AuthController authController;

    @Autowired
    private NoteService noteService;

    @Autowired
    private ShareService shareService;

    @Test
    void firstUserBecomesAdminAndRegistrationDefaultsToDisabled() {
        assertFalse(settingsService.isRegistrationEnabled());
        assertTrue(settingsService.isBootstrapAdminRegistrationRequired());
        assertTrue(settingsService.isRegistrationAvailable());

        ApiResponse<Map<String, Object>> initialStatus = authController.registrationStatus();
        assertTrue((Boolean) initialStatus.data().get("available"));
        assertTrue((Boolean) initialStatus.data().get("bootstrapAdminRequired"));
        assertFalse((Boolean) initialStatus.data().get("enabled"));

        UserAccount admin = authService.register("admin@example.com", "123456");
        assertTrue(admin.isAdminFlag());
        assertEquals("admin", admin.getUsername());
        assertFalse(settingsService.isBootstrapAdminRegistrationRequired());
        assertFalse(settingsService.isRegistrationAvailable());

        ApiResponse<Map<String, Object>> postBootstrapStatus = authController.registrationStatus();
        assertFalse((Boolean) postBootstrapStatus.data().get("available"));
        assertFalse((Boolean) postBootstrapStatus.data().get("bootstrapAdminRequired"));
        assertFalse((Boolean) postBootstrapStatus.data().get("enabled"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.register("user2@example.com", "123456")
        );
        assertTrue(exception.getMessage().contains("注册功能已关闭"));

        settingsService.setRegistrationEnabled(true);
        assertTrue(settingsService.isRegistrationAvailable());
        UserAccount user = authService.register("user2@example.com", "123456");
        assertFalse(user.isAdminFlag());
        assertEquals("user2", user.getUsername());
    }

    @Test
    void markdownOutlineAndPasswordShareWork() {
        UserAccount author = authService.register("writer@example.com", "123456");
        Note note = noteService.createNote(
                author,
                "测试笔记",
                "# 标题\n\n## 第一节\n内容\n\n### 子节\n- 条目",
                null
        );

        Map<String, Object> detail = noteService.toDetail(note);
        assertTrue(detail.get("renderedHtml").toString().contains("<h2 id=\"第一节\">"));
        assertTrue(detail.get("outlineJson").toString().contains("第一节"));
        assertTrue(detail.get("outlineJson").toString().contains("子节"));

        LocalDateTime expiresAt = LocalDateTime.now().plusDays(1);
        Map<String, Object> share = shareService.createShare(author, note, "PASSWORD", "secret1", expiresAt, ZonedDateTime.now().getOffset().getTotalSeconds() / 60 / 60, "http://localhost:8080");
        assertTrue(share.get("url").toString().contains("/s/"));

        String token = share.get("token").toString();
        assertThrows(RuntimeException.class, () -> shareService.openShare(token, "wrong"));

        Map<String, Object> sharedData = shareService.openShare(token, "secret1");
        assertNotNull(sharedData.get("note"));
        assertNotNull(sharedData.get("owner"));
        assertNotNull(sharedData.get("share"));
    }
}

