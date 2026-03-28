package cn.suhoan.starlight.service;

import cn.suhoan.starlight.entity.Category;
import cn.suhoan.starlight.entity.Note;
import cn.suhoan.starlight.entity.UserAccount;
import cn.suhoan.starlight.repository.CategoryRepository;
import cn.suhoan.starlight.repository.NoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 笔记归档导入导出服务。
 * <p>负责将用户全部笔记导出为「Markdown + 文件夹结构」ZIP，
 * 以及将 ZIP 中的目录和 Markdown 文件重新导入为分类和笔记。</p>
 *
 * @author suhoan
 */
@Service
@Transactional
public class NoteTransferService {

    private static final Logger log = LoggerFactory.getLogger(NoteTransferService.class);
    private static final DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Set<String> WINDOWS_RESERVED_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    );

    private final NoteRepository noteRepository;
    private final CategoryRepository categoryRepository;
    private final NoteService noteService;

    public NoteTransferService(NoteRepository noteRepository,
                               CategoryRepository categoryRepository,
                               NoteService noteService) {
        this.noteRepository = noteRepository;
        this.categoryRepository = categoryRepository;
        this.noteService = noteService;
    }

    /**
     * 导出指定用户的全部笔记为 ZIP 二进制内容。
     * <p>分类会导出为目录，笔记会导出为 `.md` 文件。</p>
     *
     * @param ownerId 用户 ID
     * @return 导出的 ZIP 内容与推荐文件名
     */
    @Transactional(readOnly = true)
    public ArchivePayload exportArchive(String ownerId) {
        log.info("开始导出笔记 ZIP: ownerId={}", ownerId);
        List<Category> categories = categoryRepository.findByOwnerIdOrderByNameAsc(ownerId);
        List<Note> notes = noteRepository.findByOwnerIdOrderByUpdatedAtDesc(ownerId);

        Map<String, List<Category>> childCategories = new HashMap<>();
        for (Category category : categories) {
            String parentId = category.getParent() == null ? ROOT_KEY : category.getParent().getId();
            childCategories.computeIfAbsent(parentId, ignored -> new ArrayList<>()).add(category);
        }

        Map<String, List<Note>> notesByCategory = new HashMap<>();
        for (Note note : notes) {
            String categoryId = note.getCategory() == null ? ROOT_KEY : note.getCategory().getId();
            notesByCategory.computeIfAbsent(categoryId, ignored -> new ArrayList<>()).add(note);
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            ExportStat stat = writeDirectoryContents(
                    zipOutputStream,
                    "",
                    childCategories,
                    notesByCategory,
                    ROOT_KEY
            );
            zipOutputStream.finish();
            byte[] bytes = outputStream.toByteArray();
            String fileName = "starlight-notes-" + FILE_TIME_FORMATTER.format(LocalDateTime.now()) + ".zip";
            log.info("笔记 ZIP 导出完成: ownerId={}, categoryCount={}, noteCount={}, bytes={}",
                    ownerId, stat.categoryCount(), stat.noteCount(), bytes.length);
            return new ArchivePayload(bytes, fileName, stat.noteCount(), stat.categoryCount());
        } catch (IOException exception) {
            log.error("导出笔记 ZIP 失败: ownerId={}", ownerId, exception);
            throw new IllegalStateException("导出 ZIP 失败，请稍后重试");
        }
    }

    /**
     * 从 ZIP 文件中导入分类与 Markdown 笔记。
     * <p>导入时会复用已存在的同名父子分类，Markdown 文件会创建为新笔记。</p>
     *
     * @param owner 当前登录用户
     * @param file  用户上传的 ZIP 文件
     * @return 导入结果摘要
     */
    public Map<String, Object> importArchive(UserAccount owner, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请先选择 ZIP 文件");
        }
        String originalFilename = file.getOriginalFilename() == null ? "notes.zip" : file.getOriginalFilename();
        log.info("开始导入笔记 ZIP: ownerId={}, fileName={}, size={}", owner.getId(), originalFilename, file.getSize());

        ParsedArchive parsedArchive = parseArchive(file, originalFilename);
        if (parsedArchive.directories().isEmpty() && parsedArchive.noteEntries().isEmpty()) {
            throw new IllegalArgumentException("ZIP 包中未找到可导入的 Markdown 文件或目录");
        }

        List<Category> existingCategories = categoryRepository.findByOwnerIdOrderByNameAsc(owner.getId());
        Map<String, Category> categoryByRelation = new HashMap<>();
        for (Category category : existingCategories) {
            categoryByRelation.put(buildCategoryRelationKey(category.getParent(), category.getName()), category);
        }

        Map<String, Category> categoryByPath = new HashMap<>();
        int createdCategoryCount = 0;
        int createdNoteCount = 0;

        for (List<String> directorySegments : parsedArchive.directories()) {
            EnsureCategoryResult result = ensureCategoryPath(owner, directorySegments, categoryByRelation, categoryByPath);
            createdCategoryCount += result.createdCount();
        }

        for (ArchiveNoteEntry noteEntry : parsedArchive.noteEntries()) {
            List<String> categorySegments = noteEntry.categorySegments();
            EnsureCategoryResult categoryResult = ensureCategoryPath(owner, categorySegments, categoryByRelation, categoryByPath);
            createdCategoryCount += categoryResult.createdCount();

            String title = resolveImportedNoteTitle(noteEntry.fileName());
            String categoryId = categoryResult.lastCategory() == null ? null : categoryResult.lastCategory().getId();
            noteService.createNote(owner, title, noteEntry.markdownContent(), categoryId);
            createdNoteCount++;
        }

        log.info("笔记 ZIP 导入完成: ownerId={}, fileName={}, createdCategoryCount={}, createdNoteCount={}, ignoredEntryCount={}",
                owner.getId(), originalFilename, createdCategoryCount, createdNoteCount, parsedArchive.ignoredEntryCount());

        Map<String, Object> result = new HashMap<>();
        result.put("fileName", originalFilename);
        result.put("categoryCount", createdCategoryCount);
        result.put("noteCount", createdNoteCount);
        result.put("ignoredCount", parsedArchive.ignoredEntryCount());
        return result;
    }

    /**
     * 将某个目录下的分类与笔记递归写入 ZIP。
     *
     * @param zipOutputStream ZIP 输出流
     * @param parentPath      当前父级目录路径
     * @param childCategories 父分类到子分类的映射
     * @param notesByCategory 分类到笔记的映射
     * @param parentId        当前父分类 ID，根目录使用固定标记
     * @return 当前目录下导出的分类和笔记数量
     */
    private ExportStat writeDirectoryContents(ZipOutputStream zipOutputStream,
                                              String parentPath,
                                              Map<String, List<Category>> childCategories,
                                              Map<String, List<Note>> notesByCategory,
                                              String parentId) throws IOException {
        int categoryCount = 0;
        int noteCount = 0;

        List<Category> categories = new ArrayList<>(childCategories.getOrDefault(parentId, List.of()));
        categories.sort(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Category::getId));

        NameAllocator directoryNameAllocator = new NameAllocator();
        for (Category category : categories) {
            String safeDirectoryName = directoryNameAllocator.allocate(sanitizePathSegment(category.getName(), "未命名分类"));
            String directoryPath = parentPath + safeDirectoryName + "/";
            putDirectoryEntry(zipOutputStream, directoryPath);
            categoryCount++;

            ExportStat nestedStat = writeDirectoryContents(
                    zipOutputStream,
                    directoryPath,
                    childCategories,
                    notesByCategory,
                    category.getId()
            );
            categoryCount += nestedStat.categoryCount();
            noteCount += nestedStat.noteCount();
        }

        List<Note> directoryNotes = new ArrayList<>(notesByCategory.getOrDefault(parentId, List.of()));
        directoryNotes.sort(Comparator.comparing(Note::getTitle, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Note::getId));

        NameAllocator fileNameAllocator = new NameAllocator();
        for (Note note : directoryNotes) {
            String safeFileBaseName = fileNameAllocator.allocate(sanitizePathSegment(note.getTitle(), "未命名笔记"));
            putTextEntry(zipOutputStream, parentPath + safeFileBaseName + ".md", note.getMarkdownContent());
            noteCount++;
        }

        return new ExportStat(noteCount, categoryCount);
    }

    /**
     * 解析上传的 ZIP 文件。
     * <p>只识别目录与 Markdown 文件，其它文件会被忽略。</p>
     */
    private ParsedArchive parseArchive(MultipartFile file, String originalFilename) {
        Set<String> directoryPathSet = new HashSet<>();
        List<ArchiveNoteEntry> noteEntries = new ArrayList<>();
        int ignoredEntryCount = 0;

        try (InputStream inputStream = file.getInputStream();
             ZipInputStream zipInputStream = new ZipInputStream(inputStream, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String normalizedPath = normalizeZipEntryPath(entry.getName());
                if (normalizedPath == null) {
                    ignoredEntryCount++;
                    continue;
                }
                List<String> segments = splitPath(normalizedPath);
                if (segments.isEmpty()) {
                    ignoredEntryCount++;
                    continue;
                }

                if (entry.isDirectory()) {
                    directoryPathSet.add(String.join("/", segments));
                    continue;
                }

                String fileName = segments.get(segments.size() - 1);
                if (!isMarkdownFile(fileName)) {
                    ignoredEntryCount++;
                    log.debug("忽略非 Markdown 文件: fileName={}, entry={}", originalFilename, entry.getName());
                    continue;
                }

                List<String> categorySegments = segments.subList(0, segments.size() - 1);
                collectDirectoryPaths(categorySegments, directoryPathSet);
                noteEntries.add(new ArchiveNoteEntry(
                        new ArrayList<>(categorySegments),
                        fileName,
                        readEntryContent(zipInputStream)
                ));
            }
        } catch (ZipException exception) {
            log.warn("ZIP 文件格式无效: fileName={}", originalFilename, exception);
            throw new IllegalArgumentException("上传的文件不是有效的 ZIP 包");
        } catch (IOException exception) {
            log.error("读取 ZIP 文件失败: fileName={}", originalFilename, exception);
            throw new IllegalStateException("读取 ZIP 文件失败，请稍后重试");
        }

        List<List<String>> directories = directoryPathSet.stream()
                .map(this::splitPath)
                .sorted(Comparator.<List<String>>comparingInt(List::size)
                        .thenComparing(path -> String.join("/", path), String.CASE_INSENSITIVE_ORDER))
                .toList();

        return new ParsedArchive(directories, noteEntries, ignoredEntryCount);
    }

    /**
     * 确保指定目录路径对应的分类存在。
     * <p>如果分类不存在则自动创建；如果已存在则直接复用。</p>
     */
    private EnsureCategoryResult ensureCategoryPath(UserAccount owner,
                                                   List<String> rawSegments,
                                                   Map<String, Category> categoryByRelation,
                                                   Map<String, Category> categoryByPath) {
        if (rawSegments == null || rawSegments.isEmpty()) {
            return new EnsureCategoryResult(null, 0);
        }

        Category parent = null;
        int createdCount = 0;
        StringBuilder currentPath = new StringBuilder();

        for (String rawSegment : rawSegments) {
            String categoryName = normalizeImportedName(rawSegment, "未命名分类");
            if (!currentPath.isEmpty()) {
                currentPath.append('/');
            }
            currentPath.append(categoryName);
            String pathKey = currentPath.toString();

            Category byPath = categoryByPath.get(pathKey);
            if (byPath != null) {
                parent = byPath;
                continue;
            }

            String relationKey = buildCategoryRelationKey(parent, categoryName);
            Category category = categoryByRelation.get(relationKey);
            if (category == null) {
                category = noteService.createCategory(owner, categoryName, parent == null ? null : parent.getId());
                categoryByRelation.put(relationKey, category);
                createdCount++;
            }

            categoryByPath.put(pathKey, category);
            parent = category;
        }

        return new EnsureCategoryResult(parent, createdCount);
    }

    /** 向 ZIP 中写入目录条目。 */
    private void putDirectoryEntry(ZipOutputStream zipOutputStream, String directoryPath) throws IOException {
        ZipEntry entry = new ZipEntry(directoryPath);
        entry.setTime(System.currentTimeMillis());
        zipOutputStream.putNextEntry(entry);
        zipOutputStream.closeEntry();
    }

    /** 向 ZIP 中写入文本文件条目。 */
    private void putTextEntry(ZipOutputStream zipOutputStream, String entryPath, String content) throws IOException {
        ZipEntry entry = new ZipEntry(entryPath);
        entry.setTime(System.currentTimeMillis());
        zipOutputStream.putNextEntry(entry);
        byte[] bytes = (content == null ? "" : content).getBytes(StandardCharsets.UTF_8);
        zipOutputStream.write(bytes);
        zipOutputStream.closeEntry();
    }

    /** 读取当前 ZIP 条目的文本内容。 */
    private String readEntryContent(ZipInputStream zipInputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int length;
        while ((length = zipInputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, length);
        }
        return outputStream.toString(StandardCharsets.UTF_8);
    }

    /**
     * 规范化 ZIP 中的条目路径，并拦截路径穿越。
     *
     * @param entryName 原始 ZIP 条目名
     * @return 规范化后的路径；如需忽略则返回 null
     */
    private String normalizeZipEntryPath(String entryName) {
        if (entryName == null || entryName.isBlank()) {
            return null;
        }
        String normalized = entryName.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank()) {
            return null;
        }

        List<String> result = new ArrayList<>();
        for (String segment : normalized.split("/")) {
            if (segment.isBlank() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                throw new IllegalArgumentException("ZIP 包含非法路径，已拒绝导入");
            }
            if (segment.indexOf('\0') >= 0) {
                throw new IllegalArgumentException("ZIP 包含非法文件名，已拒绝导入");
            }
            if ((result.isEmpty() && "__MACOSX".equalsIgnoreCase(segment))
                    || ".DS_Store".equalsIgnoreCase(segment)
                    || segment.startsWith("._")) {
                return null;
            }
            result.add(segment);
        }
        return result.isEmpty() ? null : String.join("/", result);
    }

    /** 将路径字符串拆分为目录片段列表。 */
    private List<String> splitPath(String path) {
        if (path == null || path.isBlank()) {
            return List.of();
        }
        List<String> segments = new ArrayList<>();
        for (String segment : path.split("/")) {
            if (!segment.isBlank()) {
                segments.add(segment);
            }
        }
        return segments;
    }

    /** 根据 Markdown 文件名生成导入后的笔记标题。 */
    private String resolveImportedNoteTitle(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        String baseName = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
        return normalizeImportedName(baseName, "未命名笔记");
    }

    /** 规范化导入后的名称。 */
    private String normalizeImportedName(String rawName, String fallback) {
        String value = rawName == null ? "" : rawName.strip();
        value = value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
        while (value.endsWith(".") || value.endsWith(" ")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.isBlank() ? fallback : value;
    }

    /** 判断文件是否为 Markdown 文件。 */
    private boolean isMarkdownFile(String fileName) {
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        return lowerName.endsWith(".md") || lowerName.endsWith(".markdown");
    }

    /** 收集目录路径及其所有父级目录。 */
    private void collectDirectoryPaths(List<String> categorySegments, Set<String> directoryPathSet) {
        if (categorySegments == null || categorySegments.isEmpty()) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (String segment : categorySegments) {
            String normalizedSegment = normalizeImportedName(segment, "未命名分类");
            if (!builder.isEmpty()) {
                builder.append('/');
            }
            builder.append(normalizedSegment);
            directoryPathSet.add(builder.toString());
        }
    }

    /** 构建分类关系缓存键。 */
    private String buildCategoryRelationKey(Category parent, String name) {
        return (parent == null ? ROOT_KEY : parent.getId()) + "\u0000" + name.toLowerCase(Locale.ROOT);
    }

    /**
     * 生成适用于 ZIP 路径的安全名称。
     * <p>会保留人类可读性，并尽量避免 Windows 文件名冲突。</p>
     */
    private String sanitizePathSegment(String name, String fallback) {
        String source = name == null ? "" : name.strip();
        if (source.isBlank()) {
            source = fallback;
        }

        StringBuilder builder = new StringBuilder();
        for (char current : source.toCharArray()) {
            switch (current) {
                case '<' -> builder.append('＜');
                case '>' -> builder.append('＞');
                case ':' -> builder.append('：');
                case '"' -> builder.append('＂');
                case '/' -> builder.append('／');
                case '\\' -> builder.append('＼');
                case '|' -> builder.append('｜');
                case '?' -> builder.append('？');
                case '*' -> builder.append('＊');
                case '\t', '\r', '\n' -> builder.append(' ');
                default -> {
                    if (Character.isISOControl(current)) {
                        builder.append(' ');
                    } else {
                        builder.append(current);
                    }
                }
            }
        }

        String sanitized = builder.toString().strip();
        while (sanitized.endsWith(".") || sanitized.endsWith(" ")) {
            sanitized = sanitized.substring(0, sanitized.length() - 1);
        }
        if (sanitized.isBlank()) {
            sanitized = fallback;
        }
        if (WINDOWS_RESERVED_NAMES.contains(sanitized.toUpperCase(Locale.ROOT))) {
            sanitized = sanitized + "_";
        }
        if (sanitized.length() > 120) {
            sanitized = sanitized.substring(0, 120).strip();
        }
        return sanitized.isBlank() ? fallback : sanitized;
    }

    private static final String ROOT_KEY = "__root__";

    /** 导出的 ZIP 内容载体。 */
    public record ArchivePayload(byte[] content, String fileName, int noteCount, int categoryCount) {
    }

    /** 单个 Markdown 条目的解析结果。 */
    private record ArchiveNoteEntry(List<String> categorySegments, String fileName, String markdownContent) {
    }

    /** ZIP 解析后的整体结果。 */
    private record ParsedArchive(List<List<String>> directories, List<ArchiveNoteEntry> noteEntries, int ignoredEntryCount) {
    }

    /** 递归写入 ZIP 时的统计信息。 */
    private record ExportStat(int noteCount, int categoryCount) {
    }

    /** 确保分类路径存在后的结果。 */
    private record EnsureCategoryResult(Category lastCategory, int createdCount) {
    }

    /**
     * 同级目录/文件名分配器。
     * <p>用于处理导出时同名冲突，自动追加 ` (2)`、` (3)` 后缀。</p>
     */
    private static final class NameAllocator {
        private final Map<String, Integer> counters = new HashMap<>();

        private String allocate(String name) {
            String key = name.toLowerCase(Locale.ROOT);
            int next = counters.getOrDefault(key, 0) + 1;
            counters.put(key, next);
            return next == 1 ? name : name + " (" + next + ")";
        }
    }
}


