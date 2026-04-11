package cn.suhoan.starlight.config;

import cn.suhoan.starlight.entity.Note;
import cn.suhoan.starlight.service.MarkdownService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 应用启动时回填已有笔记的 plainText 字段。
 * 同时修复历史 PostgreSQL 数据中遗留的纯数字大对象引用。
 *
 * @author suhoan
 */
@Component
public class PlainTextMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlainTextMigrationRunner.class);
    private static final Pattern LEGACY_OID_TEXT = Pattern.compile("^\\d+$");

    private final EntityManager entityManager;
    private final MarkdownService markdownService;

    public PlainTextMigrationRunner(EntityManager entityManager, MarkdownService markdownService) {
        this.entityManager = entityManager;
        this.markdownService = markdownService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Note> notes = loadRepairCandidates();
        if (notes.isEmpty()) {
            return;
        }
        int repairedCount = 0;
        for (Note note : notes) {
            String recalculated = markdownService.stripToPlainText(note.getMarkdownContent());
            if (!Objects.equals(note.getPlainText(), recalculated)) {
                note.setPlainText(recalculated);
                repairedCount++;
            }
        }
        if (repairedCount == 0) {
            return;
        }
        log.info("plainText back-fill complete, repaired {} notes", repairedCount);
    }

    private List<Note> loadRepairCandidates() {
        TypedQuery<Note> missingQuery = entityManager.createQuery(
                "SELECT n FROM Note n WHERE n.plainText = '' OR n.plainText IS NULL", Note.class);
        List<Note> missingNotes = new ArrayList<>(missingQuery.getResultList());
        Set<String> existingIds = new HashSet<>();
        for (Note note : missingNotes) {
            existingIds.add(note.getId());
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("SELECT id, plain_text FROM sl_note").getResultList();
        List<String> suspiciousIds = rows.stream()
                .filter(row -> row[1] instanceof String plainText && LEGACY_OID_TEXT.matcher(plainText).matches())
                .map(row -> row[0].toString())
                .filter(existingIds::add)
                .toList();

        if (!suspiciousIds.isEmpty()) {
            TypedQuery<Note> suspiciousQuery = entityManager.createQuery(
                    "SELECT n FROM Note n WHERE n.id IN :ids", Note.class);
            suspiciousQuery.setParameter("ids", suspiciousIds);
            missingNotes.addAll(suspiciousQuery.getResultList());
        }
        return missingNotes;
    }
}

