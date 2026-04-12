package cn.suhoan.starlight;

import cn.suhoan.starlight.entity.UserAccount;
import cn.suhoan.starlight.service.AuthService;
import cn.suhoan.starlight.service.GitImportService;
import cn.suhoan.starlight.service.GitRepositoryClient;
import cn.suhoan.starlight.service.NoteService;
import cn.suhoan.starlight.service.SettingsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Git 导入功能测试。
 * <p>使用可控的假 Git 客户端模拟远程仓库，避免测试依赖外部网络环境。</p>
 */
@SpringBootTest
@Transactional
class GitImportFeatureTests {

    @Autowired
    private AuthService authService;

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private GitImportService gitImportService;

    @Autowired
    private NoteService noteService;

    @Autowired
    private FakeGitRepositoryClient fakeGitRepositoryClient;

    private final List<Path> tempRepositories = new ArrayList<>();

    @AfterEach
    void cleanupTempRepositories() throws IOException {
        for (Path path : tempRepositories) {
            if (path == null || !Files.exists(path)) {
                continue;
            }
            try (var stream = Files.walk(path)) {
                stream.sorted(java.util.Comparator.reverseOrder()).forEach(current -> {
                    try {
                        Files.deleteIfExists(current);
                    } catch (IOException ignored) {
                    }
                });
            }
        }
        tempRepositories.clear();
    }

    @Test
    void gitImportShouldRespectFeatureToggle() {
        UserAccount user = authService.register("git-toggle@example.com", "123456");
        settingsService.setGitImportEnabled(false);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> gitImportService.createPreview(user, "https://example.com/demo.git", "main"));
        assertTrue(exception.getMessage().contains("管理员尚未开启"));
    }

    @Test
    void gitImportShouldCreateSourceAndImportMarkdownTree() throws Exception {
        UserAccount user = authService.register("git-import@example.com", "123456");
        settingsService.setGitImportEnabled(true);

        Path repository = createRepository(Map.of(
                "README.md", "# README\n\nroot",
                "docs/guide.md", "# Guide\n\nhello",
                "assets/logo.png", "binary-placeholder"
        ));
        fakeGitRepositoryClient.useRepository(repository, List.of("main", "dev"), "1111111111111111111111111111111111111111");

        Map<String, Object> branches = gitImportService.resolveBranches("https://token@example.com/demo.git");
        assertEquals("main", branches.get("defaultBranch"));

        Map<String, Object> preview = gitImportService.createPreview(user, "https://token@example.com/demo.git", "main");
        assertTrue(preview.get("directories").toString().contains("docs"));

        Map<String, Object> result = gitImportService.importFromPreview(user,
                new GitImportService.GitImportRequest(
                        preview.get("previewToken").toString(),
                        "",
                        null,
                        "demo",
                        false,
                        "MANUAL_ONLY",
                        "Asia/Shanghai",
                        null,
                        null,
                        null
                ));

        assertEquals(2, result.get("noteCount"));
        assertEquals("demo", result.get("targetCategoryName"));

        String treeText = noteService.buildTree(user.getId()).toString();
        assertTrue(treeText.contains("来自git"));
        assertTrue(treeText.contains("demo"));
        assertTrue(treeText.contains("docs"));
        assertTrue(treeText.contains("README"));
        assertTrue(treeText.contains("guide"));

        List<Map<String, Object>> sources = gitImportService.listSources(user.getId());
        assertEquals(1, sources.size());
        assertEquals("demo", sources.getFirst().get("repositoryName"));
        assertEquals(false, sources.getFirst().get("autoSyncEnabled"));
    }

    @Test
    void manualReimportShouldOverwriteUserChangesAndHardDeleteOldImportedNotes() throws Exception {
        UserAccount user = authService.register("git-reimport@example.com", "123456");
        settingsService.setGitImportEnabled(true);

        Path repositoryV1 = createRepository(Map.of(
                "guide.md", "# Guide\n\nversion1",
                "docs/keep.md", "# Keep\n\nkeep-v1"
        ));
        fakeGitRepositoryClient.useRepository(repositoryV1, List.of("main"), "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

        Map<String, Object> preview = gitImportService.createPreview(user, "https://example.com/demo.git", "main");
        Map<String, Object> imported = gitImportService.importFromPreview(user,
                new GitImportService.GitImportRequest(
                        preview.get("previewToken").toString(),
                        "",
                        null,
                        "demo-reimport",
                        false,
                        "MANUAL_ONLY",
                        "Asia/Shanghai",
                        null,
                        null,
                        null
                ));

        String sourceId = ((Map<String, Object>) imported.get("source")).get("id").toString();
        List<Map<String, Object>> notesBefore = noteService.listUserNotes(user.getId());
        String guideNoteId = notesBefore.stream()
                .filter(item -> "guide".equals(item.get("title")))
                .findFirst()
                .orElseThrow()
                .get("id").toString();

        Map<String, Object> guideDetail = noteService.getNoteDetail(user.getId(), guideNoteId);
        noteService.updateNote(user, guideNoteId, "Guide（用户改过）", "# Guide\n\nuser-changed", guideDetail.get("categoryId") == null ? null : guideDetail.get("categoryId").toString());
        noteService.deleteNote(user.getId(), guideNoteId);
        assertEquals(1, noteService.listTrashNotes(user.getId()).size());

        Path repositoryV2 = createRepository(Map.of(
                "guide.md", "# Guide\n\nversion2",
                "docs/new-file.md", "# New File\n\nnew-v2"
        ));
        fakeGitRepositoryClient.useRepository(repositoryV2, List.of("main"), "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

        Map<String, Object> syncResult = gitImportService.syncSourceNow(user.getId(), sourceId);
        assertEquals(false, syncResult.get("skipped"));
        assertEquals(0, noteService.listTrashNotes(user.getId()).size());

        List<Map<String, Object>> notesAfter = noteService.listUserNotes(user.getId());
        assertEquals(2, notesAfter.size());
        assertFalse(notesAfter.stream().anyMatch(item -> "Guide（用户改过）".equals(item.get("title"))));
        assertTrue(notesAfter.stream().anyMatch(item -> "guide".equals(item.get("title"))));
        assertTrue(notesAfter.stream().anyMatch(item -> "new-file".equals(item.get("title"))));

        boolean hasVersion2 = notesAfter.stream()
                .map(item -> noteService.getNoteDetail(user.getId(), item.get("id").toString()))
                .anyMatch(detail -> String.valueOf(detail.get("markdownContent")).contains("version2"));
        assertTrue(hasVersion2);
    }

    @Test
    void gitImportShouldSaveAutoSyncSettings() throws Exception {
        UserAccount user = authService.register("git-schedule@example.com", "123456");
        settingsService.setGitImportEnabled(true);

        Path repository = createRepository(Map.of("README.md", "# README\n\ncontent"));
        fakeGitRepositoryClient.useRepository(repository, List.of("main"), "cccccccccccccccccccccccccccccccccccccccc");

        Map<String, Object> preview = gitImportService.createPreview(user, "https://example.com/schedule.git", "main");
        Map<String, Object> imported = gitImportService.importFromPreview(user,
                new GitImportService.GitImportRequest(
                        preview.get("previewToken").toString(),
                        "",
                        null,
                        "schedule-demo",
                        false,
                        "MANUAL_ONLY",
                        "Asia/Shanghai",
                        null,
                        null,
                        null
                ));

        String sourceId = ((Map<String, Object>) imported.get("source")).get("id").toString();
        Map<String, Object> updated = gitImportService.updateAutoSync(user, sourceId,
                new GitImportService.GitAutoSyncRequest(true, "WEEKLY", "Asia/Shanghai", 4, 30, 7));

        assertEquals(true, updated.get("autoSyncEnabled"));
        assertEquals("WEEKLY", updated.get("scheduleType"));
        assertEquals("Asia/Shanghai", updated.get("scheduleTimezone"));
        assertEquals(4, updated.get("scheduleHour"));
        assertEquals(30, updated.get("scheduleMinute"));
        assertEquals(7, updated.get("scheduleDayOfWeek"));
    }

    private Path createRepository(Map<String, String> files) throws IOException {
        Path root = Files.createTempDirectory("starlight-git-fixture-");
        tempRepositories.add(root);
        for (Map.Entry<String, String> entry : files.entrySet()) {
            Path file = root.resolve(entry.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, entry.getValue(), StandardCharsets.UTF_8);
        }
        return root;
    }

    @TestConfiguration
    static class GitImportTestConfiguration {

        @Bean
        @Primary
        FakeGitRepositoryClient fakeGitRepositoryClient() {
            return new FakeGitRepositoryClient();
        }
    }

    static class FakeGitRepositoryClient implements GitRepositoryClient {

        private Path repositoryRoot;
        private List<String> branches = List.of("main");
        private String headCommitId = "test-commit";

        void useRepository(Path repositoryRoot, List<String> branches, String headCommitId) {
            this.repositoryRoot = repositoryRoot;
            this.branches = branches;
            this.headCommitId = headCommitId;
        }

        @Override
        public List<String> listBranches(String repositoryUrl) {
            return branches;
        }

        @Override
        public String resolveBranchHeadCommit(String repositoryUrl, String branchName) {
            return headCommitId;
        }

        @Override
        public ClonedRepository shallowClone(String repositoryUrl, String branchName, Path targetDirectory) {
            if (repositoryRoot == null) {
                throw new IllegalStateException("测试仓库尚未准备好");
            }
            try (var stream = Files.walk(repositoryRoot)) {
                for (Path source : stream.toList()) {
                    Path relative = repositoryRoot.relativize(source);
                    Path target = targetDirectory.resolve(relative.toString());
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            } catch (IOException exception) {
                throw new IllegalStateException("复制测试仓库失败", exception);
            }
            return new ClonedRepository(targetDirectory, branchName, headCommitId);
        }
    }
}

