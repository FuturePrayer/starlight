package cn.suhoan.starlight.repository;

import cn.suhoan.starlight.entity.Note;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 笔记数据访问层。
 * <p>提供笔记的持久化操作，包括按所有者查询和全文搜索。</p>
 *
 * @author suhoan
 */
public interface NoteRepository extends JpaRepository<Note, String> {

    List<Note> findByOwnerIdAndDeletedAtIsNullOrderByUpdatedAtDesc(String ownerId);

    List<Note> findByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(String ownerId);

    Optional<Note> findByIdAndOwnerId(String id, String ownerId);

    Optional<Note> findByIdAndOwnerIdAndDeletedAtIsNull(String id, String ownerId);

    Optional<Note> findByIdAndOwnerIdAndDeletedAtIsNotNull(String id, String ownerId);

    long countByOwnerIdAndDeletedAtIsNotNull(String ownerId);

    List<Note> findByDeletedAtBefore(LocalDateTime cutoff);
}

