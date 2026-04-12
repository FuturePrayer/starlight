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
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * 公开站点（星迹书阁）服务。
 * <p>处理分类的公开站点开启/关闭、站点数据查询等业务逻辑。
 * 用户可将某个分类设为公开站点，通过唯一 token 生成只读公开页面，
 * 使应用兼具私人笔记与轻量级个人博客/知识小册的能力。</p>
 * <p>开启星迹书阁后，该分类的所有子分类（递归）将一并参与公开，
 * 子分类不可单独关闭或调整设置。</p>
 *
 * @author suhoan
 */
@Service
@Transactional
public class PublicSiteService {

    private static final Logger log = LoggerFactory.getLogger(PublicSiteService.class);

    /** 首页笔记匹配名称列表（小写），在根分类下的笔记标题匹配即视为首页 */
    private static final Set<String> INDEX_NOTE_NAMES = Set.of(
            "首页", "index", "主页", "home", "welcome", "readme"
    );

    private final CategoryRepository categoryRepository;
    private final NoteRepository noteRepository;
    private final ThemeService themeService;
    private final SettingsService settingsService;
    private final SecureRandom secureRandom = new SecureRandom();

    public PublicSiteService(CategoryRepository categoryRepository,
                             NoteRepository noteRepository,
                             ThemeService themeService,
                             SettingsService settingsService) {
        this.categoryRepository = categoryRepository;
        this.noteRepository = noteRepository;
        this.themeService = themeService;
        this.settingsService = settingsService;
    }

    /**
     * 开启公开站点（星迹书阁）。
     * <p>为指定分类生成唯一的 siteToken，开启后该分类及其所有子分类下的笔记可被匿名访问。
     * 若子分类中已有独立开启星迹书阁的，需要通过 mergeSubSites=true 确认合并。</p>
     *
     * @param owner         当前用户
     * @param categoryId    分类 ID
     * @param siteTitle     站点标题（可为空，使用分类名）
     * @param mergeSubSites 是否确认合并子分类已有的独立站点
     * @return 包含站点信息的 Map（可能包含冲突信息需要前端确认）
     */
    public Map<String, Object> enableSite(UserAccount owner, String categoryId,
                                           String siteTitle, boolean mergeSubSites) {
        Category category = getOwnedCategory(owner.getId(), categoryId);

        // 检查该分类是否已经被某个祖先分类的星迹书阁覆盖
        Category ancestorSite = findAncestorWithSiteToken(category, owner.getId());
        if (ancestorSite != null) {
            log.warn("无法开启星迹书阁，该分类已被父级分类覆盖: categoryId={}, ancestorId={}, ancestorName={}",
                    categoryId, ancestorSite.getId(), ancestorSite.getName());
            throw new ResponseStatusException(BAD_REQUEST,
                    "该分类已被父级分类「" + ancestorSite.getName() + "」的星迹书阁覆盖，请在父级分类中管理");
        }

        // 如果已开启，更新标题后直接返回
        if (category.getSiteToken() != null) {
            log.info("分类已开启星迹书阁，更新标题: categoryId={}, ownerId={}", categoryId, owner.getId());
            category.setSiteTitle(siteTitle);
            categoryRepository.save(category);
            return toSiteInfo(category);
        }

        // 收集所有子分类（递归）
        List<Category> allUserCategories = categoryRepository.findByOwnerIdAndDeletedAtIsNullOrderByNameAsc(owner.getId());
        Set<String> descendantIds = collectDescendantIds(categoryId, allUserCategories);
        log.info("开启星迹书阁，递归子分类数量: categoryId={}, descendantCount={}", categoryId, descendantIds.size());

        // 检查子分类中是否有已独立开启星迹书阁的
        List<Category> conflictingSubs = new ArrayList<>();
        for (Category cat : allUserCategories) {
            if (descendantIds.contains(cat.getId()) && cat.getSiteToken() != null) {
                conflictingSubs.add(cat);
            }
        }

        // 存在冲突子分类，但用户未确认合并 → 返回冲突信息给前端
        if (!conflictingSubs.isEmpty() && !mergeSubSites) {
            log.info("开启星迹书阁发现冲突子分类，等待用户确认合并: categoryId={}, conflictCount={}",
                    categoryId, conflictingSubs.size());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("needConfirmMerge", true);
            result.put("conflictingSubs", conflictingSubs.stream().map(sub -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", sub.getId());
                item.put("name", sub.getName());
                item.put("siteToken", sub.getSiteToken());
                item.put("siteTitle", sub.getSiteTitle());
                return item;
            }).toList());
            result.put("message", "以下子分类已独立开启星迹书阁，开启后将合并到当前分类，原公开链接作废：");
            return result;
        }

        // 用户已确认合并（或无冲突），清除子分类的独立站点设置
        if (!conflictingSubs.isEmpty()) {
            for (Category sub : conflictingSubs) {
                log.info("合并子分类星迹书阁: subCategoryId={}, subName={}, oldToken={}",
                        sub.getId(), sub.getName(), sub.getSiteToken());
                sub.setSiteToken(null);
                sub.setSiteTitle(null);
                categoryRepository.save(sub);
            }
            log.info("子分类星迹书阁合并完成: 共合并 {} 个子分类", conflictingSubs.size());
        }

        // 生成唯一 token 并保存
        String token = generateToken();
        category.setSiteToken(token);
        category.setSiteTitle(siteTitle);
        categoryRepository.save(category);
        log.info("星迹书阁已开启: categoryId={}, ownerId={}, token={}", categoryId, owner.getId(), token);
        return toSiteInfo(category);
    }

    /**
     * 关闭公开站点（星迹书阁）。
     * <p>清除分类的 siteToken，公开页面将不再可访问。
     * 若该分类被某个祖先分类的星迹书阁覆盖，则不允许单独关闭。</p>
     *
     * @param owner      当前用户
     * @param categoryId 分类 ID
     */
    public void disableSite(UserAccount owner, String categoryId) {
        Category category = getOwnedCategory(owner.getId(), categoryId);

        // 检查是否被祖先分类的星迹书阁覆盖
        Category ancestorSite = findAncestorWithSiteToken(category, owner.getId());
        if (ancestorSite != null) {
            log.warn("无法关闭星迹书阁，该分类的公开设置继承自父级: categoryId={}, ancestorId={}",
                    categoryId, ancestorSite.getId());
            throw new ResponseStatusException(BAD_REQUEST,
                    "该分类的公开设置继承自父级分类「" + ancestorSite.getName() + "」，请在父级分类中管理");
        }

        if (category.getSiteToken() == null) {
            log.info("分类未开启星迹书阁，无需关闭: categoryId={}, ownerId={}", categoryId, owner.getId());
            return;
        }

        log.info("星迹书阁已关闭: categoryId={}, ownerId={}, token={}", categoryId, owner.getId(), category.getSiteToken());
        category.setSiteToken(null);
        category.setSiteTitle(null);
        categoryRepository.save(category);
    }

    /**
     * 更新站点标题。
     *
     * @param owner      当前用户
     * @param categoryId 分类 ID
     * @param siteTitle  新的站点标题
     * @return 更新后的站点信息
     */
    public Map<String, Object> updateSiteTitle(UserAccount owner, String categoryId, String siteTitle) {
        Category category = getOwnedCategory(owner.getId(), categoryId);

        // 检查是否被祖先覆盖
        Category ancestorSite = findAncestorWithSiteToken(category, owner.getId());
        if (ancestorSite != null) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "该分类的公开设置继承自父级分类「" + ancestorSite.getName() + "」，请在父级分类中管理");
        }

        if (category.getSiteToken() == null) {
            throw new ResponseStatusException(NOT_FOUND, "该分类未开启星迹书阁");
        }

        category.setSiteTitle(siteTitle);
        categoryRepository.save(category);
        log.info("星迹书阁标题已更新: categoryId={}, ownerId={}, newTitle={}", categoryId, owner.getId(), siteTitle);
        return toSiteInfo(category);
    }

    /**
     * 获取分类的星迹书阁信息（需要登录）。
     * <p>若分类本身未开启但被某个祖先覆盖，则返回继承状态信息。</p>
     *
     * @param owner      当前用户
     * @param categoryId 分类 ID
     * @return 站点信息，若未开启则返回 enabled=false
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSiteInfo(UserAccount owner, String categoryId) {
        Category category = getOwnedCategory(owner.getId(), categoryId);

        // 检查是否被祖先分类的星迹书阁覆盖
        Category ancestorSite = findAncestorWithSiteToken(category, owner.getId());
        if (ancestorSite != null) {
            // 该分类被父级覆盖，返回继承状态
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("categoryId", category.getId());
            info.put("categoryName", category.getName());
            info.put("enabled", true);
            info.put("inherited", true);
            info.put("inheritedFrom", Map.of(
                    "id", ancestorSite.getId(),
                    "name", ancestorSite.getName(),
                    "siteToken", ancestorSite.getSiteToken()
            ));
            info.put("siteToken", null);
            info.put("siteTitle", null);
            return info;
        }

        return toSiteInfo(category);
    }

    /**
     * 公开访问：获取站点首页数据。
     * <p>通过 token 查找分类，返回站点标题、文章列表、统一树形结构和首页笔记 ID。
     * 文章包含该分类及其所有子分类（递归）下的笔记。无需登录。</p>
     *
     * @param token 站点访问令牌
     * @return 站点首页数据
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getPublicSiteIndex(String token) {
        Category category = categoryRepository.findBySiteTokenAndDeletedAtIsNull(token)
                .orElseThrow(() -> {
                    log.warn("公开站点访问失败，令牌无效: token={}", token);
                    return new ResponseStatusException(NOT_FOUND, "站点不存在或已关闭");
                });

        UserAccount owner = category.getOwner();
        Map<String, Object> theme = themeService.resolveTheme(owner.getThemeId());

        // 收集所有子分类 ID（递归）
        List<Category> allUserCategories = categoryRepository.findByOwnerIdAndDeletedAtIsNullOrderByNameAsc(owner.getId());
        Set<String> descendantIds = collectDescendantIds(category.getId(), allUserCategories);
        Set<String> allCategoryIds = new HashSet<>(descendantIds);
        allCategoryIds.add(category.getId());

        log.debug("公开站点首页: token={}, rootCategoryId={}, totalCategoryCount={}", token, category.getId(), allCategoryIds.size());

        // 构建分类 ID → 分类信息的映射
        Map<String, Category> categoryMap = new HashMap<>();
        categoryMap.put(category.getId(), category);
        for (Category cat : allUserCategories) {
            if (descendantIds.contains(cat.getId())) {
                categoryMap.put(cat.getId(), cat);
            }
        }

        // 获取所有相关分类下的笔记
        List<Note> allNotes = noteRepository
                .findByCategoryIdInAndDeletedAtIsNullOrderByPinnedFlagDescTitleAsc(allCategoryIds);

        // 构建统一树形结构（格式与主应用笔记树一致）
        Map<String, Object> siteTree = buildPublicSiteTree(category, allUserCategories, descendantIds, allNotes);

        // 查找首页笔记
        String indexNoteId = findIndexNoteId(allNotes, category.getId());
        if (indexNoteId != null) {
            log.info("公开站点检测到首页笔记: token={}, indexNoteId={}", token, indexNoteId);
        }

        // 构建扁平笔记列表（用于卡片展示）
        List<Map<String, Object>> noteList = allNotes.stream().map(note -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", note.getId());
            item.put("title", note.getTitle());
            item.put("updatedAt", note.getUpdatedAt());
            item.put("createdAt", note.getCreatedAt());
            item.put("pinnedFlag", note.isPinnedFlag());
            item.put("categoryId", note.getCategory() != null ? note.getCategory().getId() : null);
            if (note.getCategory() != null) {
                Category noteCat = categoryMap.get(note.getCategory().getId());
                item.put("categoryName", noteCat != null ? noteCat.getName() : "");
            } else {
                item.put("categoryName", "");
            }
            String summary = note.getPlainText() == null ? "" : note.getPlainText();
            if (summary.length() > 120) {
                summary = summary.substring(0, 120) + "…";
            }
            item.put("summary", summary);
            return item;
        }).toList();

        String siteTitle = (category.getSiteTitle() != null && !category.getSiteTitle().isBlank())
                ? category.getSiteTitle()
                : category.getName();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("siteTitle", siteTitle);
        data.put("categoryName", category.getName());
        data.put("noteCount", noteList.size());
        data.put("notes", noteList);
        data.put("siteTree", siteTree);
        data.put("indexNoteId", indexNoteId);
        data.put("hasSubCategories", !descendantIds.isEmpty());
        data.put("owner", Map.of(
                "username", owner.getUsername(),
                "theme", theme
        ));

        log.debug("公开站点首页访问成功: token={}, categoryId={}, noteCount={}, categoryCount={}",
                token, category.getId(), noteList.size(), allCategoryIds.size());
        return data;
    }

    /**
     * 公开访问：获取站点中某篇笔记的详情。
     * <p>验证笔记确实属于该站点对应的分类或其子分类。无需登录。</p>
     *
     * @param token  站点访问令牌
     * @param noteId 笔记 ID
     * @return 笔记详情数据
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getPublicSiteNote(String token, String noteId) {
        Category category = categoryRepository.findBySiteTokenAndDeletedAtIsNull(token)
                .orElseThrow(() -> {
                    log.warn("公开站点笔记访问失败，令牌无效: token={}", token);
                    return new ResponseStatusException(NOT_FOUND, "站点不存在或已关闭");
                });

        Note note = noteRepository.findById(noteId)
                .orElseThrow(() -> {
                    log.warn("公开站点笔记访问失败，笔记不存在: token={}, noteId={}", token, noteId);
                    return new ResponseStatusException(NOT_FOUND, "笔记不存在");
                });

        // 收集所有子分类 ID（递归），用于验证笔记归属
        List<Category> allUserCategories = categoryRepository.findByOwnerIdAndDeletedAtIsNullOrderByNameAsc(category.getOwner().getId());
        Set<String> descendantIds = collectDescendantIds(category.getId(), allUserCategories);
        Set<String> allCategoryIds = new HashSet<>(descendantIds);
        allCategoryIds.add(category.getId());

        // 验证笔记属于该分类或其子分类，且未被删除
        String noteCategoryId = note.getCategory() == null ? null : note.getCategory().getId();
        if (noteCategoryId == null || !allCategoryIds.contains(noteCategoryId)) {
            log.warn("公开站点笔记访问失败，笔记不属于该站点: token={}, noteId={}, noteCategoryId={}",
                    token, noteId, noteCategoryId);
            throw new ResponseStatusException(FORBIDDEN, "该笔记不属于此站点");
        }
        if (note.getDeletedAt() != null) {
            log.warn("公开站点笔记访问失败，笔记已删除: token={}, noteId={}", token, noteId);
            throw new ResponseStatusException(NOT_FOUND, "笔记已被删除");
        }

        UserAccount owner = category.getOwner();
        Map<String, Object> theme = themeService.resolveTheme(owner.getThemeId());

        String siteTitle = (category.getSiteTitle() != null && !category.getSiteTitle().isBlank())
                ? category.getSiteTitle()
                : category.getName();

        // 获取所有相关分类下的笔记作为导航
        List<Note> siblingNotes = noteRepository
                .findByCategoryIdInAndDeletedAtIsNullOrderByPinnedFlagDescUpdatedAtDesc(allCategoryIds);

        // 构建统一树形结构
        Map<String, Object> siteTree = buildPublicSiteTree(category, allUserCategories, descendantIds, siblingNotes);

        List<Map<String, Object>> noteList = siblingNotes.stream().map(n -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", n.getId());
            item.put("title", n.getTitle());
            item.put("updatedAt", n.getUpdatedAt());
            item.put("categoryId", n.getCategory() != null ? n.getCategory().getId() : null);
            return item;
        }).toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("siteTitle", siteTitle);
        data.put("note", Map.of(
                "id", note.getId(),
                "title", note.getTitle(),
                "renderedHtml", note.getRenderedHtml(),
                "outlineJson", note.getOutlineJson(),
                "markdownContent", note.getMarkdownContent(),
                "updatedAt", note.getUpdatedAt(),
                "createdAt", note.getCreatedAt()
        ));
        data.put("notes", noteList);
        data.put("siteTree", siteTree);
        data.put("hasSubCategories", !descendantIds.isEmpty());
        data.put("owner", Map.of(
                "username", owner.getUsername(),
                "theme", theme
        ));

        log.debug("公开站点笔记访问成功: token={}, noteId={}", token, noteId);
        return data;
    }

    // ──── 私有方法 ────

    /** 获取当前用户拥有的分类 */
    private Category getOwnedCategory(String ownerId, String categoryId) {
        return categoryRepository.findByIdAndOwnerIdAndDeletedAtIsNull(categoryId, ownerId)
                .orElseThrow(() -> {
                    log.warn("分类未找到或不属于该用户: categoryId={}, ownerId={}", categoryId, ownerId);
                    return new ResponseStatusException(NOT_FOUND, "分类不存在");
                });
    }

    /** 生成安全的随机站点 token（12 字节 Base64Url 编码） */
    private String generateToken() {
        byte[] bytes = new byte[12];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 将分类转换为站点信息 Map */
    private Map<String, Object> toSiteInfo(Category category) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("categoryId", category.getId());
        info.put("categoryName", category.getName());
        info.put("enabled", category.getSiteToken() != null);
        info.put("inherited", false);
        info.put("siteToken", category.getSiteToken());
        info.put("siteTitle", category.getSiteTitle());
        return info;
    }

    /**
     * 递归收集指定分类的所有后代分类 ID。
     * <p>在内存中遍历所有用户分类，构建父子关系后递归收集。</p>
     *
     * @param parentId      父分类 ID
     * @param allCategories 用户的所有分类列表
     * @return 所有后代分类 ID 的集合（不包含 parentId 自身）
     */
    private Set<String> collectDescendantIds(String parentId, List<Category> allCategories) {
        // 构建 parentId → 子分类列表 的映射
        Map<String, List<Category>> childrenMap = new HashMap<>();
        for (Category cat : allCategories) {
            String pid = cat.getParent() != null ? cat.getParent().getId() : null;
            if (pid != null) {
                childrenMap.computeIfAbsent(pid, k -> new ArrayList<>()).add(cat);
            }
        }

        // 递归收集所有后代 ID
        Set<String> result = new HashSet<>();
        collectDescendantsRecursive(parentId, childrenMap, result);
        return result;
    }

    /** 递归辅助方法：深度优先遍历收集后代 ID */
    private void collectDescendantsRecursive(String parentId, Map<String, List<Category>> childrenMap, Set<String> result) {
        List<Category> children = childrenMap.get(parentId);
        if (children == null) return;
        for (Category child : children) {
            result.add(child.getId());
            collectDescendantsRecursive(child.getId(), childrenMap, result);
        }
    }

    /**
     * 查找拥有 siteToken 的最近祖先分类。
     * <p>沿着父链向上遍历，找到第一个已开启星迹书阁的祖先（不包含自身）。</p>
     *
     * @param category 当前分类
     * @param ownerId  用户 ID（用于安全校验）
     * @return 拥有 siteToken 的祖先分类，若无则返回 null
     */
    private Category findAncestorWithSiteToken(Category category, String ownerId) {
        // 加载所有用户分类用于内存遍历，避免 N+1 查询
        List<Category> allCategories = categoryRepository.findByOwnerIdAndDeletedAtIsNullOrderByNameAsc(ownerId);
        Map<String, Category> categoryMap = new HashMap<>();
        Map<String, String> parentMap = new HashMap<>();
        for (Category cat : allCategories) {
            categoryMap.put(cat.getId(), cat);
            if (cat.getParent() != null) {
                parentMap.put(cat.getId(), cat.getParent().getId());
            }
        }

        // 沿父链向上查找
        String currentId = category.getId();
        String pid = parentMap.get(currentId);
        while (pid != null) {
            Category ancestor = categoryMap.get(pid);
            if (ancestor != null && ancestor.getSiteToken() != null) {
                return ancestor;
            }
            pid = parentMap.get(pid);
        }
        return null;
    }

    /**
     * 构建公开站点的统一树形结构，格式与主应用的笔记树一致。
     * <p>每一层级中分类排在笔记前面，笔记按标题排序，置顶笔记单独归入 pinnedItems。</p>
     *
     * @param rootCategory    站点根分类
     * @param allCategories   用户所有分类
     * @param descendantIds   所有后代分类 ID
     * @param allNotes        所有相关笔记
     * @return 包含 items 和 pinnedItems 的树形结构
     */
    private Map<String, Object> buildPublicSiteTree(Category rootCategory,
                                                     List<Category> allCategories,
                                                     Set<String> descendantIds,
                                                     List<Note> allNotes) {
        // 按分类 ID 分组笔记
        Map<String, List<Note>> notesByCategory = new HashMap<>();
        for (Note note : allNotes) {
            String catId = note.getCategory() != null ? note.getCategory().getId() : null;
            if (catId != null) {
                notesByCategory.computeIfAbsent(catId, k -> new ArrayList<>()).add(note);
            }
        }

        // 构建 parentId → 子分类列表
        Map<String, List<Category>> childrenMap = new HashMap<>();
        for (Category cat : allCategories) {
            if (!descendantIds.contains(cat.getId())) continue;
            String pid = cat.getParent() != null ? cat.getParent().getId() : null;
            if (pid != null) {
                childrenMap.computeIfAbsent(pid, k -> new ArrayList<>()).add(cat);
            }
        }

        // 收集所有置顶笔记（跨分类），按置顶时间倒序、标题正序排列
        List<Map<String, Object>> pinnedItems = allNotes.stream()
                .filter(Note::isPinnedFlag)
                .sorted(Comparator.comparing(Note::getPinnedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Note::getTitle, String.CASE_INSENSITIVE_ORDER))
                .map(this::toPublicTreeNote)
                .toList();

        // 从根分类开始构建树（分类在前、非置顶笔记在后）
        List<Map<String, Object>> items = buildTreeLevel(rootCategory.getId(), childrenMap, notesByCategory);

        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("items", items);
        tree.put("pinnedItems", pinnedItems);
        return tree;
    }

    /**
     * 递归构建某一层级的树节点列表。
     * <p>分类排在前面按名称排序，非置顶笔记排在后面按标题排序。</p>
     */
    private List<Map<String, Object>> buildTreeLevel(String categoryId,
                                                      Map<String, List<Category>> childrenMap,
                                                      Map<String, List<Note>> notesByCategory) {
        List<Map<String, Object>> items = new ArrayList<>();

        // 添加子分类节点（按名称排序）
        List<Category> subCategories = new ArrayList<>(childrenMap.getOrDefault(categoryId, List.of()));
        subCategories.sort(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER));
        for (Category sub : subCategories) {
            Map<String, Object> catNode = new LinkedHashMap<>();
            catNode.put("id", sub.getId());
            catNode.put("name", sub.getName());
            catNode.put("type", "category");
            catNode.put("children", buildTreeLevel(sub.getId(), childrenMap, notesByCategory));
            items.add(catNode);
        }

        // 添加非置顶笔记节点（按标题排序，置顶笔记已归入 pinnedItems）
        List<Note> notes = notesByCategory.getOrDefault(categoryId, List.of());
        notes.stream()
                .filter(n -> !n.isPinnedFlag())
                .sorted(Comparator.comparing(Note::getTitle, String.CASE_INSENSITIVE_ORDER))
                .map(this::toPublicTreeNote)
                .forEach(items::add);

        return items;
    }

    /** 将笔记转换为公开站点树节点 */
    private Map<String, Object> toPublicTreeNote(Note note) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", note.getId());
        item.put("name", note.getTitle());
        item.put("type", "note");
        item.put("updatedAt", note.getUpdatedAt());
        item.put("pinnedFlag", note.isPinnedFlag());
        return item;
    }

    /**
     * 查找首页笔记。
     * <p>在根分类下查找名称匹配 INDEX_NOTE_NAMES 的笔记，若有多个则选最近更新的。</p>
     *
     * @param allNotes       所有相关笔记
     * @param rootCategoryId 站点根分类 ID
     * @return 首页笔记 ID，无匹配则返回 null
     */
    private String findIndexNoteId(List<Note> allNotes, String rootCategoryId) {
        return allNotes.stream()
                .filter(n -> n.getCategory() != null && n.getCategory().getId().equals(rootCategoryId))
                .filter(n -> INDEX_NOTE_NAMES.contains(n.getTitle().trim().toLowerCase()))
                .max(Comparator.comparing(Note::getUpdatedAt))
                .map(Note::getId)
                .orElse(null);
    }
}
