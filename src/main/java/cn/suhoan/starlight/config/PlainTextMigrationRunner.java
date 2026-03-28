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

import java.util.List;

/**
 * 应用启动时回填已有笔记的 plainText 字段。
 * 仅处理 plainText 为空的笔记，执行一次后不再重复处理。
 */
@Component
public class PlainTextMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlainTextMigrationRunner.class);

    private final EntityManager entityManager;
    private final MarkdownService markdownService;

    public PlainTextMigrationRunner(EntityManager entityManager, MarkdownService markdownService) {
        this.entityManager = entityManager;
        this.markdownService = markdownService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        TypedQuery<Note> query = entityManager.createQuery(
                "SELECT n FROM Note n WHERE n.plainText = '' OR n.plainText IS NULL", Note.class);
        List<Note> notes = query.getResultList();
        if (notes.isEmpty()) {
            return;
        }
        log.info("Back-filling plainText for {} existing notes…", notes.size());
        for (Note note : notes) {
            note.setPlainText(markdownService.stripToPlainText(note.getMarkdownContent()));
        }
        log.info("plainText back-fill complete");
    }
}

