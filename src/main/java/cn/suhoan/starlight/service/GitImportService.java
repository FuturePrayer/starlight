package cn.suhoan.starlight.service;

import cn.suhoan.starlight.entity.Category;
import cn.suhoan.starlight.entity.GitImportBinding;
import cn.suhoan.starlight.entity.GitNoteSource;
import cn.suhoan.starlight.entity.GitScheduleType;
import cn.suhoan.starlight.entity.GitSyncHistory;
import cn.suhoan.starlight.entity.Note;
import cn.suhoan.starlight.entity.UserAccount;
import cn.suhoan.starlight.repository.CategoryRepository;
import cn.suhoan.starlight.repository.GitImportBindingRepository;
import cn.suhoan.starlight.repository.GitNoteSourceRepository;
import cn.suhoan.starlight.repository.GitSyncHistoryRepository;
import cn.suhoan.starlight.repository.NoteRepository;
import cn.suhoan.starlight.repository.NoteShareRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * Git 仓库导入服务。
 * <p>提供分支解析、临时预览、Markdown 导入、手动重导入与定时自动同步能力。</p>
 */
@Service
public class GitImportService {

    private static final Logger log = LoggerFactory.getLogger(GitImportService.class);
    private static final String GIT_IMPORT_ENABLED_KEY = SettingsService.GIT_IMPORT_ENABLED_KEY;
    private static final String GIT_IMPORT_MAX_CONCURRENT_KEY = SettingsService.GIT_IMPORT_MAX_CONCURRENT_KEY;
    private static final String GIT_ROOT_CATEGORY_NAME = "来自git";
    private static final String BINDING_TYPE_NOTE = "NOTE";
    private static final String BINDING_TYPE_CATEGORY = "CATEGORY";
    private static final long PREVIEW_EXPIRE_MINUTES = 20;

    private final GitRepositoryClient gitRepositoryClient;
    private final SettingsService settingsService;
    private final NoteService noteService;
    private final CategoryRepository categoryRepository;
    private final NoteRepository noteRepository;
    private final NoteShareRepository noteShareRepository;
    private final GitNoteSourceRepository gitNoteSourceRepository;
    private final GitImportBindingRepository gitImportBindingRepository;
    private final GitSyncHistoryRepository gitSyncHistoryRepository;
    private final TransactionTemplate transactionTemplate;
    private final Object importSlotMonitor = new Object();
    private final ConcurrentHashMap<String, PreviewSession> previewSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> sourceLocks = new ConcurrentHashMap<>();
    private volatile int activeImportCount = 0;

    public GitImportService(GitRepositoryClient gitRepositoryClient,
                            SettingsService settingsService,
                            NoteService noteService,
                            CategoryRepository categoryRepository,
                            NoteRepository noteRepository,
                            NoteShareRepository noteShareRepository,
                            GitNoteSourceRepository gitNoteSourceRepository,
                            GitImportBindingRepository gitImportBindingRepository,
                            GitSyncHistoryRepository gitSyncHistoryRepository,
                            TransactionTemplate transactionTemplate) {
        this.gitRepositoryClient = gitRepositoryClient;
        this.settingsService = settingsService;
        this.noteService = noteService;
        this.categoryRepository = categoryRepository;
        this.noteRepository = noteRepository;
        this.noteShareRepository = noteShareRepository;
        this.gitNoteSourceRepository = gitNoteSourceRepository;
        this.gitImportBindingRepository = gitImportBindingRepository;
        this.gitSyncHistoryRepository = gitSyncHistoryRepository;
        this.transactionTemplate = transactionTemplate;
    }

    /** 查询当前用户可见的 Git 导入功能状态。 */
    public Map<String, Object> getFeatureStatus() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", isGitImportEnabled());
        data.put("maxConcurrentImports", getMaxConcurrentImports());
        return data;
    }

    /** 解析仓库分支。 */
    public Map<String, Object> resolveBranches(String repositoryUrl) {
        ensureGitImportEnabled();
        List<String> branches = gitRepositoryClient.listBranches(repositoryUrl);
        if (branches.isEmpty()) {
            throw new IllegalArgumentException("未解析到任何可用分支");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("repositoryName", deriveRepositoryName(repositoryUrl));
        result.put("branches", branches);
        result.put("defaultBranch", resolveDefaultBranch(branches));
        return result;
    }

    /**
     * 创建预览会话。
     * <p>该阶段会先浅克隆仓库到临时目录，再返回可选的导入目录列表。</p>
     */
    public Map<String, Object> createPreview(UserAccount owner, String repositoryUrl, String branchName) {
        ensureGitImportEnabled();
        String normalizedBranchName = normalizeRequired(branchName, "请选择仓库分支");
        ImportSlot slot = acquireImportSlot();
        Path tempDirectory = null;
        try {
            tempDirectory = Files.createTempDirectory("starlight-git-preview-");
            GitRepositoryClient.ClonedRepository clonedRepository = gitRepositoryClient.shallowClone(repositoryUrl, normalizedBranchName, tempDirectory);
            List<SourceDirectoryOption> directoryOptions = listSourceDirectoryOptions(clonedRepository.workingDirectory());
            String previewToken = UUID.randomUUID().toString();
            PreviewSession session = new PreviewSession(
                    previewToken,
                    owner.getId(),
                    repositoryUrl,
                    deriveRepositoryName(repositoryUrl),
                    normalizedBranchName,
                    clonedRepository.headCommitId(),
                    clonedRepository.workingDirectory(),
                    utcNow(),
                    directoryOptions
            );
            previewSessions.put(previewToken, session);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("previewToken", session.token());
            result.put("repositoryName", session.repositoryName());
            result.put("branchName", session.branchName());
            result.put("headCommitId", abbreviateCommitId(session.headCommitId()));
            result.put("directories", session.directoryOptions().stream().map(this::toDirectoryMap).toList());
            result.put("defaultTargetCategoryName", session.repositoryName());
            result.put("hasMarkdown", session.directoryOptions().stream().anyMatch(item -> item.markdownFileCount() > 0));
            return result;
        } catch (IOException exception) {
            deleteDirectoryQuietly(tempDirectory);
            throw new IllegalStateException("创建临时工作目录失败，请稍后重试");
        } finally {
            slot.close();
        }
    }

    /** 手动关闭预览并删除临时目录。 */
    public void discardPreview(UserAccount owner, String previewToken) {
        PreviewSession session = previewSessions.get(previewToken);
        if (session == null) {
            return;
        }
        if (!owner.getId().equals(session.ownerId())) {
            throw new IllegalArgumentException("预览会话不存在或已失效");
        }
        removePreviewSession(session);
    }

    /**
     * 从预览会话执行首次导入。
     * <p>成功后会保存导入源配置，供后续手动同步和自动同步使用。</p>
     */
    public Map<String, Object> importFromPreview(UserAccount owner, GitImportRequest request) {
        ensureGitImportEnabled();
        PreviewSession session = previewSessions.get(request.previewToken());
        if (session == null || !owner.getId().equals(session.ownerId())) {
            throw new IllegalArgumentException("预览会话已失效，请重新解析仓库");
        }
        ImportSchedule schedule = validateSchedule(request.autoSyncEnabled(), request.scheduleType(),
                request.scheduleTimezone(), request.scheduleHour(), request.scheduleMinute(), request.scheduleDayOfWeek());
        try (ImportSlot _ = acquireImportSlot()) {
            return transactionTemplate.execute(status -> doInitialImport(owner, session, request, schedule));
        } finally {
            removePreviewSession(session);
        }
    }

    /** 列出当前用户已保存的 Git 导入源。 */
    public List<Map<String, Object>> listSources(String ownerId) {
        return gitNoteSourceRepository.findByOwnerIdOrderByUpdatedAtDesc(ownerId).stream()
                .map(this::toSourceMap)
                .toList();
    }

    /** 更新自动同步设置。 */
    public Map<String, Object> updateAutoSync(UserAccount owner, String sourceId, GitAutoSyncRequest request) {
        ensureGitImportEnabled();
        ImportSchedule schedule = validateSchedule(request.autoSyncEnabled(), request.scheduleType(),
                request.scheduleTimezone(), request.scheduleHour(), request.scheduleMinute(), request.scheduleDayOfWeek());
        transactionTemplate.executeWithoutResult(status -> {
            GitNoteSource source = getOwnedSource(owner.getId(), sourceId);
            applySchedule(source, schedule);
            gitNoteSourceRepository.save(source);
        });
        return toSourceMap(getOwnedSource(owner.getId(), sourceId));
    }

    /**
     * 删除已保存的 Git 导入源。
     * <p>仅移除仓库地址、同步历史和绑定关系，不会删除已导入的笔记与分类数据。</p>
     */
    @Transactional
    public void deleteSource(String ownerId, String sourceId) {
        GitNoteSource source = getOwnedSource(ownerId, sourceId);
        gitImportBindingRepository.deleteBySourceId(sourceId);
        gitSyncHistoryRepository.deleteBySourceId(sourceId);
        gitNoteSourceRepository.delete(source);
        sourceLocks.remove(sourceId);
        log.info("Git 导入源已删除: sourceId={}, ownerId={}, repository={}", sourceId, ownerId, source.getRepositoryName());
    }

    /** 手动重导入指定导入源。 */
    public Map<String, Object> syncSourceNow(String ownerId, String sourceId) {
        ensureGitImportEnabled();
        return runSync(getOwnedSource(ownerId, sourceId), "MANUAL", true, false);
    }

    /** 自动同步轮询。 */
    @Scheduled(fixedDelay = 60000)
    public void runAutoSyncScheduler() {
        if (!isGitImportEnabled()) {
            return;
        }
        for (GitNoteSource source : gitNoteSourceRepository.findByAutoSyncEnabledTrueOrderByUpdatedAtAsc()) {
            try {
                if (isDueForAutoSync(source)) {
                    runSync(source, "AUTO", false, true);
                }
            } catch (Exception exception) {
                log.warn("Git 自动同步执行失败: sourceId={}", source.getId(), exception);
            }
        }
    }

    /** 定时清理过期预览目录。 */
    @Scheduled(fixedDelay = 300000)
    public void cleanupExpiredPreviewSessions() {
        LocalDateTime now = utcNow();
        for (PreviewSession session : new ArrayList<>(previewSessions.values())) {
            if (session.createdAtUtc().plusMinutes(PREVIEW_EXPIRE_MINUTES).isBefore(now)) {
                removePreviewSession(session);
            }
        }
    }

    private Map<String, Object> doInitialImport(UserAccount owner,
                                                PreviewSession session,
                                                GitImportRequest request,
                                                ImportSchedule schedule) {
        List<MarkdownFileEntry> files = collectMarkdownFiles(session.workingDirectory(), request.sourcePath());
        if (files.isEmpty()) {
            throw new IllegalArgumentException("所选目录中未找到任何 Markdown 文件，已结束导入");
        }

        Category targetCategory;
        boolean targetCreatedBySource;
        if (hasText(request.existingTargetCategoryId())) {
            targetCategory = noteService.getOwnedCategory(owner.getId(), request.existingTargetCategoryId().trim());
            targetCreatedBySource = false;
        } else {
            Category gitRootCategory = ensureGitRootCategory(owner);
            String requestedTargetName = normalizeImportedName(request.targetCategoryName(), session.repositoryName());
            String actualTargetName = allocateUniqueChildName(owner.getId(), gitRootCategory, requestedTargetName);
            targetCategory = noteService.createCategory(owner, actualTargetName, gitRootCategory.getId());
            targetCreatedBySource = true;
        }

        GitNoteSource source = new GitNoteSource();
        source.setOwner(owner);
        source.setRepositoryUrl(session.repositoryUrl());
        source.setRepositoryName(session.repositoryName());
        source.setBranchName(session.branchName());
        source.setSourcePath(normalizeSourcePath(request.sourcePath()));
        source.setTargetCategoryId(targetCategory.getId());
        source.setTargetCategoryName(targetCategory.getName());
        source.setTargetCategoryCreatedBySource(targetCreatedBySource);
        applySchedule(source, schedule);
        source.setLastSyncedCommitId(session.headCommitId());
        source.setLastSyncAt(utcNow());
        source.setLastSyncSuccess(true);
        source.setLastSyncMessage("首次导入成功");
        gitNoteSourceRepository.save(source);

        ImportExecutionResult importResult = importMarkdownFiles(owner, source, targetCategory, files);
        recordHistory(source, "INITIAL_IMPORT", true, session.headCommitId(), "首次导入成功", importResult.startedAtUtc(), utcNow());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", toSourceMap(source));
        result.put("categoryCount", importResult.createdCategoryCount());
        result.put("noteCount", importResult.createdNoteCount());
        result.put("sourcePath", source.getSourcePath());
        result.put("targetCategoryId", source.getTargetCategoryId());
        result.put("targetCategoryName", source.getTargetCategoryName());
        return result;
    }

    private Map<String, Object> runSync(GitNoteSource source,
                                        String triggerType,
                                        boolean forceReimport,
                                        boolean skipIfBusy) {
        ReentrantLock sourceLock = sourceLocks.computeIfAbsent(source.getId(), ignored -> new ReentrantLock());
        boolean locked;
        if (skipIfBusy) {
            locked = sourceLock.tryLock();
            if (!locked) {
                return Map.of("status", "BUSY", "message", "该仓库已有同步任务正在执行，已跳过本轮自动同步");
            }
        } else {
            sourceLock.lock();
            locked = true;
        }
        ImportSlot slot = acquireImportSlot();
        Path tempDirectory = null;
        LocalDateTime startedAtUtc = utcNow();
        try {
            tempDirectory = Files.createTempDirectory("starlight-git-sync-");
            GitRepositoryClient.ClonedRepository clonedRepository = gitRepositoryClient.shallowClone(
                    source.getRepositoryUrl(), source.getBranchName(), tempDirectory);
            String currentHeadCommitId = clonedRepository.headCommitId();
            if (!forceReimport && "AUTO".equals(triggerType)
                    && hasText(source.getLastSyncedCommitId())
                    && source.getLastSyncedCommitId().equals(currentHeadCommitId)) {
                return transactionTemplate.execute(status -> {
                    GitNoteSource managedSource = gitNoteSourceRepository.findById(source.getId()).orElseThrow();
                    managedSource.setLastScheduledRunAt(utcNow());
                    managedSource.setLastSyncAt(utcNow());
                    managedSource.setLastSyncSuccess(true);
                    managedSource.setLastSyncMessage("远程仓库无新提交，已跳过重新导入");
                    gitNoteSourceRepository.save(managedSource);
                    recordHistory(managedSource, triggerType, true, currentHeadCommitId,
                            "远程仓库无新提交，已跳过重新导入", startedAtUtc, utcNow());
                    return toSyncResultMap(managedSource, 0, 0, true);
                });
            }
            return transactionTemplate.execute(status -> doReimport(source.getId(), triggerType, currentHeadCommitId, clonedRepository.workingDirectory(), startedAtUtc));
        } catch (Exception exception) {
            transactionTemplate.executeWithoutResult(status -> {
                GitNoteSource managedSource = gitNoteSourceRepository.findById(source.getId()).orElse(null);
                if (managedSource != null) {
                    managedSource.setLastScheduledRunAt("AUTO".equals(triggerType) ? utcNow() : managedSource.getLastScheduledRunAt());
                    managedSource.setLastSyncAt(utcNow());
                    managedSource.setLastSyncSuccess(false);
                    managedSource.setLastSyncMessage(trimMessage(exception.getMessage(), "同步失败"));
                    gitNoteSourceRepository.save(managedSource);
                    recordHistory(managedSource, triggerType, false, null,
                            trimMessage(exception.getMessage(), "同步失败"), startedAtUtc, utcNow());
                }
            });
            throw exception instanceof IllegalArgumentException illegalArgumentException
                    ? illegalArgumentException
                    : new IllegalStateException(trimMessage(exception.getMessage(), "同步失败"));
        } finally {
            deleteDirectoryQuietly(tempDirectory);
            slot.close();
            sourceLock.unlock();
        }
    }

    private Map<String, Object> doReimport(String sourceId,
                                           String triggerType,
                                           String currentHeadCommitId,
                                           Path workingDirectory,
                                           LocalDateTime startedAtUtc) {
        GitNoteSource source = gitNoteSourceRepository.findById(sourceId).orElseThrow();
        UserAccount owner = source.getOwner();
        Category targetCategory = ensureExistingTargetCategory(owner, source);
        List<MarkdownFileEntry> files = collectMarkdownFiles(workingDirectory, source.getSourcePath());
        if (files.isEmpty()) {
            throw new IllegalArgumentException("仓库指定目录中未找到任何 Markdown 文件，已结束导入");
        }

        cleanupImportedData(owner.getId(), source);
        ImportExecutionResult importResult = importMarkdownFiles(owner, source, targetCategory, files);

        source.setLastScheduledRunAt("AUTO".equals(triggerType) ? utcNow() : source.getLastScheduledRunAt());
        source.setLastSyncedCommitId(currentHeadCommitId);
        source.setLastSyncAt(utcNow());
        source.setLastSyncSuccess(true);
        source.setLastSyncMessage("同步成功");
        gitNoteSourceRepository.save(source);
        recordHistory(source, triggerType, true, currentHeadCommitId, "同步成功", startedAtUtc, utcNow());
        return toSyncResultMap(source, importResult.createdCategoryCount(), importResult.createdNoteCount(), false);
    }

    private ImportExecutionResult importMarkdownFiles(UserAccount owner,
                                                      GitNoteSource source,
                                                      Category targetCategory,
                                                      List<MarkdownFileEntry> files) {
        LocalDateTime startedAtUtc = utcNow();
        List<Category> existingCategories = categoryRepository.findByOwnerIdAndDeletedAtIsNullOrderByNameAsc(owner.getId());
        Map<String, Category> categoryByRelation = new HashMap<>();
        for (Category category : existingCategories) {
            categoryByRelation.put(buildCategoryRelationKey(category.getParent(), category.getName()), category);
        }

        Map<String, Category> createdCategoryByRelativePath = new HashMap<>();
        int createdCategoryCount = 0;
        int createdNoteCount = 0;

        Set<String> directoryPaths = new HashSet<>();
        for (MarkdownFileEntry file : files) {
            collectDirectoryPaths(file.directorySegments(), directoryPaths);
        }

        List<String> sortedDirectories = directoryPaths.stream()
                .sorted(Comparator.comparingInt((String path) -> path.split("/").length)
                        .thenComparing(String.CASE_INSENSITIVE_ORDER))
                .toList();

        for (String directoryPath : sortedDirectories) {
            List<String> segments = splitPath(directoryPath);
            EnsureCategoryResult result = ensureCategoryPath(owner, targetCategory, segments, categoryByRelation, createdCategoryByRelativePath);
            if (result.createdCategory() != null) {
                createdCategoryCount++;
                saveBinding(source, BINDING_TYPE_CATEGORY, result.createdCategory().getId(), directoryPath, null);
            }
        }

        for (MarkdownFileEntry file : files) {
            Category noteCategory = targetCategory;
            if (!file.directorySegments().isEmpty()) {
                EnsureCategoryResult result = ensureCategoryPath(owner, targetCategory, file.directorySegments(), categoryByRelation, createdCategoryByRelativePath);
                noteCategory = result.lastCategory();
                if (result.createdCategory() != null) {
                    createdCategoryCount++;
                    saveBinding(source, BINDING_TYPE_CATEGORY, result.createdCategory().getId(), String.join("/", file.directorySegments()), null);
                }
            }
            Note note = noteService.createNote(owner, stripMarkdownExtension(file.fileName()), file.content(), noteCategory.getId());
            saveBinding(source, BINDING_TYPE_NOTE, note.getId(), file.relativePath(), file.contentHash());
            createdNoteCount++;
        }
        return new ImportExecutionResult(createdCategoryCount, createdNoteCount, startedAtUtc);
    }

    private Category ensureExistingTargetCategory(UserAccount owner, GitNoteSource source) {
        String targetCategoryId = source.getTargetCategoryId();
        if (hasText(targetCategoryId)) {
            return categoryRepository.findByIdAndOwnerIdAndDeletedAtIsNull(targetCategoryId, owner.getId()).orElseGet(() -> {
                if (!source.isTargetCategoryCreatedBySource()) {
                    throw new IllegalArgumentException("目标分类已不存在，请重新选择导入位置");
                }
                Category gitRootCategory = ensureGitRootCategory(owner);
                Category recreated = noteService.createCategory(owner, source.getTargetCategoryName(), gitRootCategory.getId());
                source.setTargetCategoryId(recreated.getId());
                source.setTargetCategoryName(recreated.getName());
                gitNoteSourceRepository.save(source);
                return recreated;
            });
        }
        if (!source.isTargetCategoryCreatedBySource()) {
            throw new IllegalArgumentException("目标分类已不存在，请重新选择导入位置");
        }
        Category gitRootCategory = ensureGitRootCategory(owner);
        Category recreated = noteService.createCategory(owner, source.getTargetCategoryName(), gitRootCategory.getId());
        source.setTargetCategoryId(recreated.getId());
        source.setTargetCategoryName(recreated.getName());
        gitNoteSourceRepository.save(source);
        return recreated;
    }

    private void cleanupImportedData(String ownerId, GitNoteSource source) {
        List<GitImportBinding> bindings = gitImportBindingRepository.findBySourceIdOrderByBindingTypeAscRelativePathAsc(source.getId());
        if (bindings.isEmpty()) {
            return;
        }

        List<GitImportBinding> categoryBindings = bindings.stream()
                .filter(binding -> BINDING_TYPE_CATEGORY.equals(binding.getBindingType()))
                .sorted(Comparator.comparingInt((GitImportBinding binding) -> binding.getRelativePath().split("/").length))
                .toList();
        Set<String> topLevelCategoryIds = new HashSet<>();
        Set<String> ancestorPaths = new HashSet<>();
        for (GitImportBinding binding : categoryBindings) {
            String path = binding.getRelativePath();
            boolean nested = false;
            String candidate = path;
            while (candidate.contains("/")) {
                candidate = candidate.substring(0, candidate.lastIndexOf('/'));
                if (ancestorPaths.contains(candidate)) {
                    nested = true;
                    break;
                }
            }
            if (!nested) {
                topLevelCategoryIds.add(binding.getEntityId());
                ancestorPaths.add(path);
            }
        }

        Set<String> allSubtreeCategoryIds = new HashSet<>();
        for (String categoryId : topLevelCategoryIds) {
            allSubtreeCategoryIds.addAll(collectDescendantCategoryIds(ownerId, categoryId));
        }

        Set<String> noteIdsToDelete = bindings.stream()
                .filter(binding -> BINDING_TYPE_NOTE.equals(binding.getBindingType()))
                .map(GitImportBinding::getEntityId)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        if (!allSubtreeCategoryIds.isEmpty()) {
            noteIdsToDelete.addAll(noteRepository.findByCategoryIdIn(allSubtreeCategoryIds).stream()
                    .filter(note -> ownerId.equals(note.getOwner().getId()))
                    .map(Note::getId)
                    .toList());
        }
        permanentlyDeleteNotes(ownerId, noteIdsToDelete);

        if (!allSubtreeCategoryIds.isEmpty()) {
            List<Category> categories = categoryRepository.findByOwnerIdOrderByNameAsc(ownerId).stream()
                    .filter(category -> allSubtreeCategoryIds.contains(category.getId()))
                    .sorted(Comparator.comparingInt((Category category) -> categoryDepth(category)).reversed())
                    .toList();
            for (Category category : categories) {
                categoryRepository.delete(category);
            }
            categoryRepository.flush();
        }

        gitImportBindingRepository.deleteBySourceId(source.getId());
    }

    private EnsureCategoryResult ensureCategoryPath(UserAccount owner,
                                                    Category targetRootCategory,
                                                    List<String> rawSegments,
                                                    Map<String, Category> categoryByRelation,
                                                    Map<String, Category> createdCategoryByRelativePath) {
        if (rawSegments == null || rawSegments.isEmpty()) {
            return new EnsureCategoryResult(targetRootCategory, null);
        }
        Category parent = targetRootCategory;
        Category createdCategory = null;
        StringBuilder currentPath = new StringBuilder();
        for (String rawSegment : rawSegments) {
            String categoryName = normalizeImportedName(rawSegment, "未命名分类");
            if (!currentPath.isEmpty()) {
                currentPath.append('/');
            }
            currentPath.append(categoryName);
            String relativePath = currentPath.toString();
            Category existingCreated = createdCategoryByRelativePath.get(relativePath);
            if (existingCreated != null) {
                parent = existingCreated;
                continue;
            }
            String relationKey = buildCategoryRelationKey(parent, categoryName);
            Category category = categoryByRelation.get(relationKey);
            if (category == null) {
                category = noteService.createCategory(owner, categoryName, parent.getId());
                categoryByRelation.put(relationKey, category);
                createdCategory = category;
            }
            createdCategoryByRelativePath.put(relativePath, category);
            parent = category;
        }
        return new EnsureCategoryResult(parent, createdCategory);
    }

    private void saveBinding(GitNoteSource source, String bindingType, String entityId, String relativePath, String contentHash) {
        GitImportBinding binding = new GitImportBinding();
        binding.setSource(source);
        binding.setBindingType(bindingType);
        binding.setEntityId(entityId);
        binding.setRelativePath(relativePath == null ? "" : relativePath);
        binding.setContentHash(contentHash);
        gitImportBindingRepository.save(binding);
    }

    private void permanentlyDeleteNotes(String ownerId, Collection<String> noteIds) {
        if (noteIds == null || noteIds.isEmpty()) {
            return;
        }
        List<Note> notes = noteRepository.findByIdIn(noteIds).stream()
                .filter(note -> ownerId.equals(note.getOwner().getId()))
                .toList();
        for (Note note : notes) {
            long shareCount = noteShareRepository.countByNoteId(note.getId());
            if (shareCount > 0) {
                noteShareRepository.deleteByNoteId(note.getId());
            }
        }
        noteShareRepository.flush();
        noteRepository.deleteAll(notes);
        noteRepository.flush();
    }

    private Set<String> collectDescendantCategoryIds(String ownerId, String rootCategoryId) {
        List<Category> categories = categoryRepository.findByOwnerIdOrderByNameAsc(ownerId);
        Map<String, List<String>> childrenMap = new HashMap<>();
        for (Category category : categories) {
            if (category.getParent() != null) {
                childrenMap.computeIfAbsent(category.getParent().getId(), ignored -> new ArrayList<>())
                        .add(category.getId());
            }
        }
        Set<String> result = new HashSet<>();
        ArrayList<String> queue = new ArrayList<>();
        result.add(rootCategoryId);
        queue.add(rootCategoryId);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            for (String childId : childrenMap.getOrDefault(current, List.of())) {
                if (result.add(childId)) {
                    queue.add(childId);
                }
            }
        }
        return result;
    }

    private int categoryDepth(Category category) {
        int depth = 0;
        Category current = category.getParent();
        while (current != null) {
            depth++;
            current = current.getParent();
        }
        return depth;
    }

    private ImportSchedule validateSchedule(boolean autoSyncEnabled,
                                            String scheduleTypeText,
                                            String timezoneId,
                                            Integer scheduleHour,
                                            Integer scheduleMinute,
                                            Integer scheduleDayOfWeek) {
        if (!autoSyncEnabled) {
            return new ImportSchedule(false, GitScheduleType.MANUAL_ONLY, null, null, null, null);
        }
        GitScheduleType scheduleType;
        try {
            scheduleType = GitScheduleType.valueOf(normalizeRequired(scheduleTypeText, "请选择自动同步频率"));
        } catch (Exception exception) {
            throw new IllegalArgumentException("自动同步频率不正确");
        }
        String safeTimezoneId = normalizeRequired(timezoneId, "开启自动同步时必须提供时区");
        try {
            ZoneId.of(safeTimezoneId);
        } catch (Exception exception) {
            throw new IllegalArgumentException("自动同步时区不正确");
        }
        return switch (scheduleType) {
            case EVERY_30_MINUTES -> new ImportSchedule(true, scheduleType, safeTimezoneId, null, null, null);
            case HOURLY -> new ImportSchedule(true, scheduleType, safeTimezoneId, null,
                    validateMinute(scheduleMinute), null);
            case DAILY -> new ImportSchedule(true, scheduleType, safeTimezoneId,
                    validateHour(scheduleHour), validateMinute(scheduleMinute), null);
            case WEEKLY -> new ImportSchedule(true, scheduleType, safeTimezoneId,
                    validateHour(scheduleHour), validateMinute(scheduleMinute), validateDayOfWeek(scheduleDayOfWeek));
            case MANUAL_ONLY -> throw new IllegalArgumentException("开启自动同步时请选择有效的自动同步频率");
        };
    }

    private int validateHour(Integer value) {
        if (value == null || value < 0 || value > 23) {
            throw new IllegalArgumentException("同步小时必须在 0 到 23 之间");
        }
        return value;
    }

    private int validateMinute(Integer value) {
        if (value == null || value < 0 || value > 59) {
            throw new IllegalArgumentException("同步分钟必须在 0 到 59 之间");
        }
        return value;
    }

    private int validateDayOfWeek(Integer value) {
        if (value == null || value < 1 || value > 7) {
            throw new IllegalArgumentException("每周同步的星期必须在 1 到 7 之间");
        }
        return value;
    }

    private void applySchedule(GitNoteSource source, ImportSchedule schedule) {
        source.setAutoSyncEnabled(schedule.autoSyncEnabled());
        source.setScheduleType(schedule.scheduleType());
        source.setScheduleTimezone(schedule.timezoneId());
        source.setScheduleHour(schedule.hour());
        source.setScheduleMinute(schedule.minute());
        source.setScheduleDayOfWeek(schedule.dayOfWeek());
    }

    private boolean isDueForAutoSync(GitNoteSource source) {
        if (!source.isAutoSyncEnabled() || source.getScheduleType() == null || source.getScheduleType() == GitScheduleType.MANUAL_ONLY) {
            return false;
        }
        ZoneId zoneId = resolveZoneId(source.getScheduleTimezone());
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        LocalDateTime currentSlot = now.withSecond(0).withNano(0).toLocalDateTime();
        boolean due = switch (source.getScheduleType()) {
            case EVERY_30_MINUTES -> now.getMinute() % 30 == 0;
            case HOURLY -> Objects.equals(source.getScheduleMinute(), now.getMinute());
            case DAILY -> Objects.equals(source.getScheduleHour(), now.getHour())
                    && Objects.equals(source.getScheduleMinute(), now.getMinute());
            case WEEKLY -> Objects.equals(source.getScheduleDayOfWeek(), toIsoDayOfWeek(now.getDayOfWeek()))
                    && Objects.equals(source.getScheduleHour(), now.getHour())
                    && Objects.equals(source.getScheduleMinute(), now.getMinute());
            case MANUAL_ONLY -> false;
        };
        if (!due) {
            return false;
        }
        if (source.getLastScheduledRunAt() == null) {
            return true;
        }
        LocalDateTime lastRunInUserZone = source.getLastScheduledRunAt()
                .atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(zoneId)
                .toLocalDateTime()
                .withSecond(0)
                .withNano(0);
        return !lastRunInUserZone.equals(currentSlot);
    }

    private ZoneId resolveZoneId(String timezoneId) {
        try {
            return ZoneId.of(hasText(timezoneId) ? timezoneId : ZoneId.systemDefault().getId());
        } catch (Exception exception) {
            return ZoneId.systemDefault();
        }
    }

    private int toIsoDayOfWeek(DayOfWeek dayOfWeek) {
        return dayOfWeek.getValue();
    }

    private GitNoteSource getOwnedSource(String ownerId, String sourceId) {
        return gitNoteSourceRepository.findByIdAndOwnerId(sourceId, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Git 导入源不存在"));
    }

    private Category ensureGitRootCategory(UserAccount owner) {
        return categoryRepository.findByOwnerIdAndDeletedAtIsNullOrderByNameAsc(owner.getId()).stream()
                .filter(category -> category.getParent() == null)
                .filter(category -> GIT_ROOT_CATEGORY_NAME.equals(category.getName()))
                .findFirst()
                .orElseGet(() -> noteService.createCategory(owner, GIT_ROOT_CATEGORY_NAME, null));
    }

    private String allocateUniqueChildName(String ownerId, Category parentCategory, String baseName) {
        Set<String> siblingNames = categoryRepository.findByOwnerIdAndDeletedAtIsNullOrderByNameAsc(ownerId).stream()
                .filter(category -> category.getParent() != null)
                .filter(category -> parentCategory.getId().equals(category.getParent().getId()))
                .map(Category::getName)
                .collect(java.util.stream.Collectors.toSet());
        if (!siblingNames.contains(baseName)) {
            return baseName;
        }
        int index = 1;
        while (true) {
            String candidate = baseName + "（" + index + "）";
            if (!siblingNames.contains(candidate)) {
                return candidate;
            }
            index++;
        }
    }

    private String deriveRepositoryName(String repositoryUrl) {
        if (!hasText(repositoryUrl)) {
            return "未命名仓库";
        }
        String normalized = repositoryUrl.trim();
        int slashIndex = normalized.lastIndexOf('/');
        String name = slashIndex >= 0 ? normalized.substring(slashIndex + 1) : normalized;
        int queryIndex = name.indexOf('?');
        if (queryIndex >= 0) {
            name = name.substring(0, queryIndex);
        }
        if (name.endsWith(".git")) {
            name = name.substring(0, name.length() - 4);
        }
        return normalizeImportedName(name, "未命名仓库");
    }

    private String resolveDefaultBranch(List<String> branches) {
        if (branches.contains("main")) {
            return "main";
        }
        if (branches.contains("master")) {
            return "master";
        }
        return branches.getFirst();
    }

    private List<SourceDirectoryOption> listSourceDirectoryOptions(Path repositoryRoot) {
        Map<String, Integer> counts = new HashMap<>();
        counts.put("", 0);
        try (Stream<Path> stream = Files.walk(repositoryRoot)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> isMarkdownFile(path.getFileName().toString()))
                    .forEach(path -> {
                        Path parent = path.getParent();
                        String relativeDirectory = "";
                        if (parent != null && !repositoryRoot.equals(parent)) {
                            relativeDirectory = normalizeRelativePath(repositoryRoot.relativize(parent));
                        }
                        incrementDirectoryCounts(counts, relativeDirectory);
                    });
        } catch (IOException exception) {
            throw new IllegalStateException("扫描仓库目录失败，请稍后重试");
        }
        return counts.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, Integer> entry) -> pathDepth(entry.getKey()))
                        .thenComparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER))
                .map(entry -> new SourceDirectoryOption(
                        entry.getKey(),
                        entry.getKey().isBlank() ? "/" : entry.getKey(),
                        entry.getValue()
                ))
                .toList();
    }

    private void incrementDirectoryCounts(Map<String, Integer> counts, String relativeDirectory) {
        counts.compute("", (ignored, value) -> value == null ? 1 : value + 1);
        if (!hasText(relativeDirectory)) {
            return;
        }
        String[] segments = relativeDirectory.split("/");
        StringBuilder builder = new StringBuilder();
        for (String segment : segments) {
            if (!builder.isEmpty()) {
                builder.append('/');
            }
            builder.append(segment);
            String path = builder.toString();
            counts.compute(path, (ignored, value) -> value == null ? 1 : value + 1);
        }
    }

    private List<MarkdownFileEntry> collectMarkdownFiles(Path repositoryRoot, String sourcePath) {
        String normalizedSourcePath = normalizeSourcePath(sourcePath);
        Path importRoot = resolveImportRoot(repositoryRoot, normalizedSourcePath);
        if (!Files.exists(importRoot) || !Files.isDirectory(importRoot)) {
            throw new IllegalArgumentException("所选仓库目录不存在");
        }
        List<MarkdownFileEntry> result = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(importRoot)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> isMarkdownFile(path.getFileName().toString()))
                    .sorted(Comparator.comparing(path -> normalizeRelativePath(importRoot.relativize(path)), String.CASE_INSENSITIVE_ORDER))
                    .forEach(path -> {
                        try {
                            String relativePath = normalizeRelativePath(importRoot.relativize(path));
                            String fileName = path.getFileName().toString();
                            Path parent = path.getParent();
                            List<String> directorySegments = parent == null || parent.equals(importRoot)
                                    ? List.of()
                                    : splitPath(normalizeRelativePath(importRoot.relativize(parent)));
                            String content = Files.readString(path, StandardCharsets.UTF_8);
                            result.add(new MarkdownFileEntry(relativePath, fileName, directorySegments, content, sha256(content)));
                        } catch (IOException exception) {
                            throw new IllegalStateException("读取 Markdown 文件失败，请稍后重试", exception);
                        }
                    });
        } catch (IOException exception) {
            throw new IllegalStateException("扫描 Markdown 文件失败，请稍后重试");
        }
        return result;
    }

    private Path resolveImportRoot(Path repositoryRoot, String normalizedSourcePath) {
        if (!hasText(normalizedSourcePath)) {
            return repositoryRoot;
        }
        Path resolved = repositoryRoot.resolve(normalizedSourcePath).normalize();
        if (!resolved.startsWith(repositoryRoot)) {
            throw new IllegalArgumentException("所选目录非法");
        }
        return resolved;
    }

    private String normalizeSourcePath(String sourcePath) {
        if (!hasText(sourcePath) || "/".equals(sourcePath.trim())) {
            return "";
        }
        String normalized = sourcePath.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.contains("..") || normalized.contains("\0")) {
            throw new IllegalArgumentException("所选仓库目录非法");
        }
        return normalized;
    }

    private String normalizeRelativePath(Path path) {
        String normalized = path.toString().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private boolean isMarkdownFile(String fileName) {
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        return lowerName.endsWith(".md") || lowerName.endsWith(".markdown");
    }

    private void collectDirectoryPaths(List<String> directorySegments, Set<String> directoryPaths) {
        if (directorySegments == null || directorySegments.isEmpty()) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (String segment : directorySegments) {
            if (!builder.isEmpty()) {
                builder.append('/');
            }
            builder.append(normalizeImportedName(segment, "未命名分类"));
            directoryPaths.add(builder.toString());
        }
    }

    private List<String> splitPath(String path) {
        if (!hasText(path)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String segment : path.split("/")) {
            if (hasText(segment)) {
                result.add(segment);
            }
        }
        return result;
    }

    private String normalizeImportedName(String rawName, String fallback) {
        String value = rawName == null ? "" : rawName.strip();
        value = value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
        while (value.endsWith(".") || value.endsWith(" ")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.isBlank() ? fallback : value;
    }

    private String stripMarkdownExtension(String fileName) {
        String normalized = fileName == null ? "" : fileName;
        int dotIndex = normalized.lastIndexOf('.');
        String baseName = dotIndex > 0 ? normalized.substring(0, dotIndex) : normalized;
        return normalizeImportedName(baseName, "未命名笔记");
    }

    private String buildCategoryRelationKey(Category parent, String name) {
        return (parent == null ? "__root__" : parent.getId()) + "\u0000" + name.toLowerCase(Locale.ROOT);
    }

    private String sha256(String content) {
        try {
            byte[] bytes = java.security.MessageDigest.getInstance("SHA-256")
                    .digest((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte value : bytes) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("计算文件哈希失败");
        }
    }

    private void recordHistory(GitNoteSource source,
                               String triggerType,
                               boolean success,
                               String commitId,
                               String message,
                               LocalDateTime startedAtUtc,
                               LocalDateTime finishedAtUtc) {
        GitSyncHistory history = new GitSyncHistory();
        history.setSource(source);
        history.setTriggerType(triggerType);
        history.setSuccessFlag(success);
        history.setCommitId(commitId);
        history.setMessage(trimMessage(message, success ? "同步成功" : "同步失败"));
        history.setStartedAt(startedAtUtc);
        history.setFinishedAt(finishedAtUtc);
        gitSyncHistoryRepository.save(history);

        List<GitSyncHistory> histories = gitSyncHistoryRepository.findBySourceIdOrderByStartedAtDesc(source.getId());
        if (histories.size() > 5) {
            for (int index = 5; index < histories.size(); index++) {
                gitSyncHistoryRepository.delete(histories.get(index));
            }
        }
    }

    private Map<String, Object> toDirectoryMap(SourceDirectoryOption option) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("path", option.path());
        data.put("label", option.label());
        data.put("markdownFileCount", option.markdownFileCount());
        return data;
    }

    private Map<String, Object> toSourceMap(GitNoteSource source) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", source.getId());
        data.put("repositoryName", source.getRepositoryName());
        data.put("repositoryUrlMasked", maskRepositoryUrl(source.getRepositoryUrl()));
        data.put("branchName", source.getBranchName());
        data.put("sourcePath", source.getSourcePath());
        data.put("targetCategoryId", source.getTargetCategoryId());
        data.put("targetCategoryName", resolveTargetCategoryName(source));
        data.put("targetCategoryCreatedBySource", source.isTargetCategoryCreatedBySource());
        data.put("autoSyncEnabled", source.isAutoSyncEnabled());
        data.put("scheduleType", source.getScheduleType() == null ? GitScheduleType.MANUAL_ONLY.name() : source.getScheduleType().name());
        data.put("scheduleTimezone", source.getScheduleTimezone());
        data.put("scheduleHour", source.getScheduleHour());
        data.put("scheduleMinute", source.getScheduleMinute());
        data.put("scheduleDayOfWeek", source.getScheduleDayOfWeek());
        data.put("lastSyncedCommitId", abbreviateCommitId(source.getLastSyncedCommitId()));
        data.put("lastSyncAt", toUtcIsoString(source.getLastSyncAt()));
        data.put("lastSyncSuccess", source.getLastSyncSuccess());
        data.put("lastSyncMessage", source.getLastSyncMessage());
        data.put("targetCategoryMissing", isTargetCategoryMissing(source));
        data.put("histories", gitSyncHistoryRepository.findTop5BySourceIdOrderByStartedAtDesc(source.getId()).stream()
                .map(this::toHistoryMap)
                .toList());
        return data;
    }

    private boolean isTargetCategoryMissing(GitNoteSource source) {
        if (!hasText(source.getTargetCategoryId())) {
            return true;
        }
        return categoryRepository.findByIdAndOwnerIdAndDeletedAtIsNull(source.getTargetCategoryId(), source.getOwner().getId()).isEmpty();
    }

    private String resolveTargetCategoryName(GitNoteSource source) {
        if (!hasText(source.getTargetCategoryId())) {
            return source.getTargetCategoryName();
        }
        return categoryRepository.findByIdAndOwnerIdAndDeletedAtIsNull(source.getTargetCategoryId(), source.getOwner().getId())
                .map(Category::getName)
                .orElse(source.getTargetCategoryName());
    }

    private Map<String, Object> toHistoryMap(GitSyncHistory history) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", history.getId());
        data.put("triggerType", history.getTriggerType());
        data.put("successFlag", history.isSuccessFlag());
        data.put("commitId", abbreviateCommitId(history.getCommitId()));
        data.put("message", history.getMessage());
        data.put("startedAt", toUtcIsoString(history.getStartedAt()));
        data.put("finishedAt", toUtcIsoString(history.getFinishedAt()));
        return data;
    }

    private Map<String, Object> toSyncResultMap(GitNoteSource source, int categoryCount, int noteCount, boolean skipped) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", toSourceMap(source));
        result.put("categoryCount", categoryCount);
        result.put("noteCount", noteCount);
        result.put("skipped", skipped);
        return result;
    }

    private String maskRepositoryUrl(String repositoryUrl) {
        if (!hasText(repositoryUrl)) {
            return "";
        }
        try {
            java.net.URI uri = java.net.URI.create(repositoryUrl.trim());
            if (!hasText(uri.getUserInfo())) {
                return repositoryUrl.trim();
            }
            return uri.getScheme() + "://***@" + uri.getHost()
                    + (uri.getPort() > 0 ? ":" + uri.getPort() : "")
                    + (uri.getRawPath() == null ? "" : uri.getRawPath());
        } catch (Exception exception) {
            return repositoryUrl.trim().replaceAll("//[^/@]+@", "//***@");
        }
    }

    private String toUtcIsoString(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atOffset(ZoneOffset.UTC).toString();
    }

    private String abbreviateCommitId(String commitId) {
        if (!hasText(commitId)) {
            return "";
        }
        return commitId.length() <= 12 ? commitId : commitId.substring(0, 12);
    }

    private String trimMessage(String message, String fallback) {
        String value = hasText(message) ? message.trim() : fallback;
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    private ImportSlot acquireImportSlot() {
        int limit = getMaxConcurrentImports();
        if (limit <= 0) {
            return () -> { };
        }
        synchronized (importSlotMonitor) {
            while (activeImportCount >= limit) {
                try {
                    importSlotMonitor.wait();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("导入任务排队时被中断");
                }
            }
            activeImportCount++;
        }
        return () -> {
            synchronized (importSlotMonitor) {
                activeImportCount = Math.max(0, activeImportCount - 1);
                importSlotMonitor.notifyAll();
            }
        };
    }

    private int getMaxConcurrentImports() {
        try {
            return Integer.parseInt(settingsService.getValue(GIT_IMPORT_MAX_CONCURRENT_KEY, "2").trim());
        } catch (Exception exception) {
            return 2;
        }
    }

    private boolean isGitImportEnabled() {
        return Boolean.parseBoolean(settingsService.getValue(GIT_IMPORT_ENABLED_KEY, "false"));
    }

    private void ensureGitImportEnabled() {
        if (!isGitImportEnabled()) {
            throw new IllegalArgumentException("管理员尚未开启 Git 导入功能");
        }
    }

    private String normalizeRequired(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private int pathDepth(String path) {
        if (!hasText(path)) {
            return 0;
        }
        return path.split("/").length;
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(Clock.systemUTC());
    }

    private void removePreviewSession(PreviewSession session) {
        previewSessions.remove(session.token());
        deleteDirectoryQuietly(session.workingDirectory());
    }

    private void deleteDirectoryQuietly(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(current -> {
                try {
                    Files.deleteIfExists(current);
                } catch (IOException exception) {
                    log.debug("删除临时目录失败: path={}", current, exception);
                }
            });
        } catch (IOException exception) {
            log.debug("遍历临时目录失败: path={}", path, exception);
        }
    }

    private record SourceDirectoryOption(String path, String label, int markdownFileCount) {
    }

    private record MarkdownFileEntry(String relativePath,
                                     String fileName,
                                     List<String> directorySegments,
                                     String content,
                                     String contentHash) {
    }

    private record PreviewSession(String token,
                                  String ownerId,
                                  String repositoryUrl,
                                  String repositoryName,
                                  String branchName,
                                  String headCommitId,
                                  Path workingDirectory,
                                  LocalDateTime createdAtUtc,
                                  List<SourceDirectoryOption> directoryOptions) {
    }

    private record EnsureCategoryResult(Category lastCategory, Category createdCategory) {
    }

    private record ImportExecutionResult(int createdCategoryCount,
                                         int createdNoteCount,
                                         LocalDateTime startedAtUtc) {
    }

    private record ImportSchedule(boolean autoSyncEnabled,
                                  GitScheduleType scheduleType,
                                  String timezoneId,
                                  Integer hour,
                                  Integer minute,
                                  Integer dayOfWeek) {
    }

    @FunctionalInterface
    private interface ImportSlot extends AutoCloseable {
        @Override
        void close();
    }

    /** Git 首次导入请求。 */
    public record GitImportRequest(String previewToken,
                                   String sourcePath,
                                   String existingTargetCategoryId,
                                   String targetCategoryName,
                                   boolean autoSyncEnabled,
                                   String scheduleType,
                                   String scheduleTimezone,
                                   Integer scheduleHour,
                                   Integer scheduleMinute,
                                   Integer scheduleDayOfWeek) {
    }

    /** Git 自动同步设置请求。 */
    public record GitAutoSyncRequest(boolean autoSyncEnabled,
                                     String scheduleType,
                                     String scheduleTimezone,
                                     Integer scheduleHour,
                                     Integer scheduleMinute,
                                     Integer scheduleDayOfWeek) {
    }
}

