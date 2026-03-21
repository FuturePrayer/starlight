package cn.suhoan.startlight.controller;

import cn.suhoan.startlight.dto.ApiResponse;
import cn.suhoan.startlight.entity.Category;
import cn.suhoan.startlight.entity.Note;
import cn.suhoan.startlight.entity.UserAccount;
import cn.suhoan.startlight.service.NoteService;
import cn.suhoan.startlight.service.SessionAuthService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class NoteController {

    private final SessionAuthService sessionAuthService;
    private final NoteService noteService;

    public NoteController(SessionAuthService sessionAuthService, NoteService noteService) {
        this.sessionAuthService = sessionAuthService;
        this.noteService = noteService;
    }

    @GetMapping("/tree")
    public ApiResponse<Map<String, Object>> tree() {
        UserAccount userAccount = sessionAuthService.requireUser();
        return ApiResponse.ok(noteService.buildTree(userAccount.getId()));
    }

    @GetMapping("/notes")
    public ApiResponse<Object> notes() {
        UserAccount userAccount = sessionAuthService.requireUser();
        return ApiResponse.ok(noteService.listUserNotes(userAccount.getId()));
    }

    @PostMapping("/categories")
    public ApiResponse<Map<String, Object>> createCategory(@RequestBody CategoryRequest request) {
        UserAccount userAccount = sessionAuthService.requireUser();
        Category category = noteService.createCategory(userAccount, request.name(), request.parentId());
        Map<String, Object> result = new HashMap<>();
        result.put("id", category.getId());
        result.put("name", category.getName());
        result.put("parentId", category.getParent() == null ? null : category.getParent().getId());
        return ApiResponse.ok(result);
    }

    @PostMapping("/notes")
    public ApiResponse<Map<String, Object>> createNote(@RequestBody NoteRequest request) {
        UserAccount userAccount = sessionAuthService.requireUser();
        Note note = noteService.createNote(userAccount, request.title(), request.markdownContent(), request.categoryId());
        return ApiResponse.ok(noteService.toDetail(note));
    }

    @GetMapping("/notes/{id}")
    public ApiResponse<Map<String, Object>> getNote(@PathVariable String id) {
        UserAccount userAccount = sessionAuthService.requireUser();
        return ApiResponse.ok(noteService.getNoteDetail(userAccount.getId(), id));
    }

    @PutMapping("/notes/{id}")
    public ApiResponse<Map<String, Object>> updateNote(@PathVariable String id,
                                                       @RequestBody NoteRequest request) {
        UserAccount userAccount = sessionAuthService.requireUser();
        Note note = noteService.updateNote(userAccount, id, request.title(), request.markdownContent(), request.categoryId());
        return ApiResponse.ok(noteService.toDetail(note));
    }

    @DeleteMapping("/notes/{id}")
    public ApiResponse<Void> deleteNote(@PathVariable String id) {
        UserAccount userAccount = sessionAuthService.requireUser();
        noteService.deleteNote(userAccount.getId(), id);
        return ApiResponse.okMessage("已删除");
    }

    public record CategoryRequest(String name, String parentId) {
    }

    public record NoteRequest(String title, String markdownContent, String categoryId) {
    }
}

