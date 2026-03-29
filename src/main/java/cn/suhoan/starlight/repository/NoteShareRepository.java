package cn.suhoan.starlight.repository;

import cn.suhoan.starlight.entity.NoteShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 笔记分享数据访问层。
 * <p>提供分享链接的持久化操作，包括按 token、笔记 ID 和所有者查询。</p>
 *
 * @author suhoan
 */
public interface NoteShareRepository extends JpaRepository<NoteShare, String> {

    List<NoteShare> findByNoteIdAndOwnerIdOrderByCreatedAtDesc(String noteId, String ownerId);

    Optional<NoteShare> findByToken(String token);

    long countByNoteId(String noteId);

    void deleteByNoteId(String noteId);
}

