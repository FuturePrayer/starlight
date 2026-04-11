package cn.suhoan.starlight;

import cn.suhoan.starlight.controller.AuthController;
import cn.suhoan.starlight.config.PlainTextMigrationRunner;
import cn.suhoan.starlight.dto.ApiResponse;
import cn.suhoan.starlight.entity.Category;
import cn.suhoan.starlight.entity.Note;
import cn.suhoan.starlight.entity.UserAccount;
import cn.suhoan.starlight.entity.UserCredential;
import jakarta.persistence.EntityManager;
import cn.suhoan.starlight.repository.NoteRepository;
import cn.suhoan.starlight.repository.NoteShareRepository;
import cn.suhoan.starlight.repository.UserCredentialRepository;
import cn.suhoan.starlight.service.AuthService;
import cn.suhoan.starlight.service.NoteService;
import cn.suhoan.starlight.service.NoteTransferService;
import cn.suhoan.starlight.service.SettingsService;
import cn.suhoan.starlight.service.ShareService;
import cn.suhoan.starlight.service.WebAuthnService;
import cn.suhoan.starlight.service.search.NoteSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

    @Autowired
    private NoteTransferService noteTransferService;

    @Autowired
    private NoteSearchService noteSearchService;

    @Autowired
    private PlainTextMigrationRunner plainTextMigrationRunner;

    @Autowired
    private NoteRepository noteRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private NoteShareRepository noteShareRepository;

    @Autowired
    private WebAuthnService webAuthnService;

    @Autowired
    private UserCredentialRepository userCredentialRepository;

    @Autowired
    private ObjectMapper objectMapper;

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

    @Test
    void notesCanBeExportedAndImportedAsMarkdownZip() throws Exception {
        UserAccount exporter = authService.register("exporter@example.com", "123456");
        Category backend = noteService.createCategory(exporter, "开发:后端", null);
        Category api = noteService.createCategory(exporter, "接口文档", backend.getId());
        noteService.createCategory(exporter, "空目录", null);

        noteService.createNote(exporter, "README", "# 项目说明\n\n这是根目录笔记。", null);
        noteService.createNote(exporter, "接口/约定?", "## API\n\n- GET /ping", api.getId());

        NoteTransferService.ArchivePayload payload = noteTransferService.exportArchive(exporter.getId());
        assertTrue(payload.content().length > 0);
        assertTrue(payload.fileName().endsWith(".zip"));
        assertEquals(2, payload.noteCount());
        assertEquals(3, payload.categoryCount());

        Set<String> entryNames = new HashSet<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(payload.content()))) {
            var entry = zipInputStream.getNextEntry();
            while (entry != null) {
                entryNames.add(entry.getName());
                entry = zipInputStream.getNextEntry();
            }
        }
        assertTrue(entryNames.contains("README.md"));
        assertTrue(entryNames.contains("开发：后端/接口文档/接口／约定？.md"));
        assertTrue(entryNames.contains("空目录/"));

        settingsService.setRegistrationEnabled(true);
        UserAccount importer = authService.register("importer@example.com", "123456");
        MockMultipartFile zipFile = new MockMultipartFile(
                "file",
                payload.fileName(),
                "application/zip",
                payload.content()
        );

        Map<String, Object> result = noteTransferService.importArchive(importer, zipFile);
        assertEquals(3, result.get("categoryCount"));
        assertEquals(2, result.get("noteCount"));
        assertEquals(0, result.get("ignoredCount"));

        Map<String, Object> tree = noteService.buildTree(importer.getId());
        String treeText = tree.toString();
        assertTrue(treeText.contains("开发：后端"));
        assertTrue(treeText.contains("接口文档"));
        assertTrue(treeText.contains("空目录"));
        assertTrue(treeText.contains("README"));
        assertTrue(treeText.contains("接口／约定？"));

        boolean hasApiContent = noteService.listUserNotes(importer.getId()).stream()
                .map(item -> noteService.getNoteDetail(importer.getId(), item.get("id").toString()))
                .anyMatch(detail -> detail.get("markdownContent").toString().contains("GET /ping"));
        assertTrue(hasApiContent);
    }

    @Test
    void invalidZipSlipArchiveIsRejected() throws IOException {
        UserAccount importer = authService.register("zip-slip@example.com", "123456");
        byte[] invalidZip = createZipWithEntry("../attack.md", "# 不允许的路径");

        MockMultipartFile zipFile = new MockMultipartFile(
                "file",
                "invalid.zip",
                "application/zip",
                invalidZip
        );

        assertThrows(IllegalArgumentException.class, () -> noteTransferService.importArchive(importer, zipFile));
    }

    @Test
    void recycleBinQuickAccessAndTrashFilteringWork() throws Exception {
        UserAccount author = authService.register("trash-owner@example.com", "123456");
        Note pinnedNote = noteService.createNote(author, "周会待办", "# 周会待办\n- 跟进发布", null);
        noteService.createNote(author, "客户回访", "# 客户回访\n- 跟进需求", null);
        Note trashedNote = noteService.createNote(author, "回收站专用词", "# 回收站专用词\n误删测试", null);

        noteService.setPinned(author.getId(), pinnedNote.getId(), true);

        Map<String, Object> trashedShare = shareService.createShare(
                author,
                trashedNote,
                "PUBLIC",
                null,
                null,
                null,
                "http://localhost:8080"
        );
        noteService.deleteNote(author.getId(), trashedNote.getId());

        Map<String, Object> tree = noteService.buildTree(author.getId());
        assertEquals(1, ((List<?>) tree.get("pinnedItems")).size());
        assertEquals(1L, ((Number) tree.get("trashCount")).longValue());

        List<Map<String, Object>> trashNotes = noteService.listTrashNotes(author.getId());
        assertEquals(1, trashNotes.size());
        assertEquals("回收站专用词", trashNotes.getFirst().get("title"));
        assertThrows(RuntimeException.class, () -> noteService.getNoteDetail(author.getId(), trashedNote.getId()));
        assertNotNull(noteService.getTrashNoteDetail(author.getId(), trashedNote.getId()).get("deletedAt"));
        assertThrows(RuntimeException.class, () -> shareService.openShare(trashedShare.get("token").toString(), null));

        List<Map<String, Object>> searchResults = noteSearchService.search(author.getId(), "回收站专用词", 0, 10);
        assertTrue(searchResults.isEmpty());

        NoteTransferService.ArchivePayload payload = noteTransferService.exportArchive(author.getId());
        Set<String> entryNames = new HashSet<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(payload.content()))) {
            var entry = zipInputStream.getNextEntry();
            while (entry != null) {
                entryNames.add(entry.getName());
                entry = zipInputStream.getNextEntry();
            }
        }
        assertTrue(entryNames.contains("客户回访.md"));
        assertTrue(entryNames.contains("周会待办.md"));
        assertFalse(entryNames.contains("回收站专用词.md"));
    }

    @Test
    void legacyNumericPlainTextCanBeRepairedFromMarkdown() {
        UserAccount author = authService.register("plaintext-repair@example.com", "123456");
        Note note = noteService.createNote(author, "历史搜索修复", "# 历史搜索修复\n\n这里是需要被恢复的正文内容", null);

        entityManager.createNativeQuery("UPDATE sl_note SET plain_text = '123456' WHERE id = :id")
                .setParameter("id", note.getId())
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        plainTextMigrationRunner.run(new DefaultApplicationArguments());
        entityManager.flush();
        entityManager.clear();

        Note repaired = noteRepository.findById(note.getId()).orElseThrow();
        assertNotEquals("123456", repaired.getPlainText());
        assertTrue(repaired.getPlainText().contains("这里是需要被恢复的正文内容"));
    }

    @Test
    void trashedNotesCanBeRestoredAndExpiredEntriesAutoPurged() {
        UserAccount author = authService.register("trash-cleanup@example.com", "123456");
        Note note = noteService.createNote(author, "待清理笔记", "# 待清理笔记", null);
        Map<String, Object> share = shareService.createShare(
                author,
                note,
                "PUBLIC",
                null,
                null,
                null,
                "http://localhost:8080"
        );

        noteService.deleteNote(author.getId(), note.getId());
        assertEquals(1, noteService.listTrashNotes(author.getId()).size());

        Note restored = noteService.restoreNote(author.getId(), note.getId());
        assertNotNull(restored);
        assertEquals(0, noteService.listTrashNotes(author.getId()).size());
        assertEquals(1, noteService.listUserNotes(author.getId()).size());

        noteService.deleteNote(author.getId(), note.getId());
        Note deletedAgain = noteRepository.findById(note.getId()).orElseThrow();
        deletedAgain.setDeletedAt(LocalDateTime.now().minusDays(31));
        noteRepository.save(deletedAgain);

        int removed = noteService.cleanupExpiredTrash();
        assertEquals(1, removed);
        assertTrue(noteRepository.findById(note.getId()).isEmpty());
        assertTrue(noteShareRepository.findByToken(share.get("token").toString()).isEmpty());
    }

    @Test
    void passkeyOptionsRemainCompatibleWithFrontendContract() throws IOException {
        settingsService.setShareBaseUrl("https://notes.example.com/app");
        UserAccount user = authService.register("passkey@example.com", "123456");

        String existingCredentialId = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("credential-1".getBytes(StandardCharsets.UTF_8));
        UserCredential credential = new UserCredential();
        credential.setUserId(user.getId());
        credential.setCredentialId(existingCredentialId);
        credential.setPublicKeyCose(Base64.getUrlEncoder().withoutPadding()
                .encodeToString("placeholder-cose".getBytes(StandardCharsets.UTF_8)));
        credential.setSignatureCount(0);
        credential.setNickname("已有通行密钥");
        userCredentialRepository.save(credential);

        Map<String, Object> registration = webAuthnService.startRegistration(user);
        var registrationPublicKey = objectMapper.readTree(registration.get("optionsJson").toString());
        assertNotNull(registration.get("handle"));
        assertEquals("notes.example.com", registrationPublicKey.get("rp").get("id").textValue());
        assertEquals(user.getUsername(), registrationPublicKey.get("user").get("name").textValue());
        assertTrue(registrationPublicKey.has("challenge"));
        assertEquals(existingCredentialId, registrationPublicKey.get("excludeCredentials").get(0).get("id").textValue());

        Map<String, Object> assertion = webAuthnService.startAssertion();
        var assertionPublicKey = objectMapper.readTree(assertion.get("optionsJson").toString());
        assertNotNull(assertion.get("handle"));
        assertEquals("notes.example.com", assertionPublicKey.get("rpId").textValue());
        assertTrue(assertionPublicKey.has("challenge"));
        assertEquals("preferred", assertionPublicKey.get("userVerification").textValue());
    }

    private byte[] createZipWithEntry(String entryName, String content) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            zipOutputStream.putNextEntry(new java.util.zip.ZipEntry(entryName));
            zipOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
            zipOutputStream.closeEntry();
            zipOutputStream.finish();
            return outputStream.toByteArray();
        }
    }
}
