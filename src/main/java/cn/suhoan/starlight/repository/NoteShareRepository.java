package cn.suhoan.starlight.repository;

import cn.suhoan.starlight.entity.NoteShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NoteShareRepository extends JpaRepository<NoteShare, String> {

    List<NoteShare> findByNoteIdAndOwnerIdOrderByCreatedAtDesc(String noteId, String ownerId);

    Optional<NoteShare> findByToken(String token);
}

