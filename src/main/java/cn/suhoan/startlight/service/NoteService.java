package cn.suhoan.startlight.service;

import cn.suhoan.startlight.entity.Category;
import cn.suhoan.startlight.entity.Note;
import cn.suhoan.startlight.entity.UserAccount;
import cn.suhoan.startlight.repository.CategoryRepository;
import cn.suhoan.startlight.repository.NoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@Transactional
public class NoteService {

    private final NoteRepository noteRepository;
    private final CategoryRepository categoryRepository;
    private final MarkdownService markdownService;

    public NoteService(NoteRepository noteRepository,
                       CategoryRepository categoryRepository,
                       MarkdownService markdownService) {
        this.noteRepository = noteRepository;
        this.categoryRepository = categoryRepository;
        this.markdownService = markdownService;
    }

    public Note createNote(UserAccount owner, String title, String markdownContent, String categoryId) {
        Note note = new Note();
        note.setOwner(owner);
        note.setTitle(normalizeTitle(title, markdownContent));
        note.setMarkdownContent(markdownContent == null ? "" : markdownContent);
        note.setRenderedHtml(markdownService.renderToHtml(note.getMarkdownContent()));
        note.setOutlineJson(markdownService.buildOutlineJson(note.getMarkdownContent()));
        note.setCategory(resolveCategory(owner.getId(), categoryId));
        return noteRepository.save(note);
    }

    public Note updateNote(UserAccount owner, String noteId, String title, String markdownContent, String categoryId) {
        Note note = getOwnedNote(owner.getId(), noteId);
        note.setTitle(normalizeTitle(title, markdownContent));
        note.setMarkdownContent(markdownContent == null ? "" : markdownContent);
        note.setRenderedHtml(markdownService.renderToHtml(note.getMarkdownContent()));
        note.setOutlineJson(markdownService.buildOutlineJson(note.getMarkdownContent()));
        note.setCategory(resolveCategory(owner.getId(), categoryId));
        return noteRepository.save(note);
    }

    @Transactional(readOnly = true)
    public Note getOwnedNote(String ownerId, String noteId) {
        return noteRepository.findByIdAndOwnerId(noteId, ownerId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "笔记不存在"));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listUserNotes(String ownerId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Note note : noteRepository.findByOwnerIdOrderByUpdatedAtDesc(ownerId)) {
            result.add(toSummary(note));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getNoteDetail(String ownerId, String noteId) {
        return toDetail(getOwnedNote(ownerId, noteId));
    }

    public void deleteNote(String ownerId, String noteId) {
        noteRepository.delete(getOwnedNote(ownerId, noteId));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> buildTree(String ownerId) {
        List<Category> categories = categoryRepository.findByOwnerIdOrderByNameAsc(ownerId);
        List<Note> notes = noteRepository.findByOwnerIdOrderByUpdatedAtDesc(ownerId);

        Map<String, Map<String, Object>> categoryMap = new HashMap<>();
        List<Map<String, Object>> roots = new ArrayList<>();

        for (Category category : categories) {
            Map<String, Object> node = new HashMap<>();
            node.put("id", category.getId());
            node.put("name", category.getName());
            node.put("type", "category");
            node.put("children", new ArrayList<Map<String, Object>>());
            categoryMap.put(category.getId(), node);
        }

        for (Category category : categories) {
            Map<String, Object> node = categoryMap.get(category.getId());
            if (category.getParent() != null) {
                Map<String, Object> parent = categoryMap.get(category.getParent().getId());
                if (parent != null) {
                    castChildren(parent).add(node);
                    continue;
                }
            }
            roots.add(node);
        }

        for (Note note : notes) {
            Map<String, Object> node = new HashMap<>();
            node.put("id", note.getId());
            node.put("name", note.getTitle());
            node.put("type", "note");
            if (note.getCategory() != null) {
                Map<String, Object> categoryNode = categoryMap.get(note.getCategory().getId());
                if (categoryNode != null) {
                    castChildren(categoryNode).add(node);
                    continue;
                }
            }
            roots.add(node);
        }

        roots.sort(Comparator.comparing(item -> item.get("name").toString()));
        return Map.of("items", roots);
    }

    public Category createCategory(UserAccount owner, String name, String parentId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("分类名称不能为空");
        }
        Category category = new Category();
        category.setOwner(owner);
        category.setName(name.trim());
        category.setParent(resolveCategory(owner.getId(), parentId));
        return categoryRepository.save(category);
    }

    private Category resolveCategory(String ownerId, String categoryId) {
        if (categoryId == null || categoryId.isBlank()) {
            return null;
        }
        return categoryRepository.findByIdAndOwnerId(categoryId, ownerId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "分类不存在"));
    }

    private String normalizeTitle(String title, String markdownContent) {
        String value = title == null ? "" : title.trim();
        if (!value.isBlank()) {
            return value;
        }
        String source = markdownContent == null ? "" : markdownContent.strip();
        if (!source.isBlank()) {
            String firstLine = source.lines().findFirst().orElse("未命名笔记").trim();
            return firstLine.length() > 80 ? firstLine.substring(0, 80) : firstLine;
        }
        return "未命名笔记";
    }

    private Map<String, Object> toSummary(Note note) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", note.getId());
        map.put("title", note.getTitle());
        map.put("categoryId", note.getCategory() == null ? null : note.getCategory().getId());
        map.put("updatedAt", note.getUpdatedAt());
        return map;
    }

    public Map<String, Object> toDetail(Note note) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", note.getId());
        map.put("title", note.getTitle());
        map.put("markdownContent", note.getMarkdownContent());
        map.put("renderedHtml", note.getRenderedHtml());
        map.put("outlineJson", note.getOutlineJson());
        map.put("categoryId", note.getCategory() == null ? null : note.getCategory().getId());
        map.put("updatedAt", note.getUpdatedAt());
        map.put("createdAt", note.getCreatedAt());
        return map;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castChildren(Map<String, Object> node) {
        return (List<Map<String, Object>>) node.get("children");
    }
}

