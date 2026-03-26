package cn.suhoan.starlight.repository;

import cn.suhoan.starlight.entity.Note;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NoteRepository extends JpaRepository<Note, String> {

    List<Note> findByOwnerIdOrderByUpdatedAtDesc(String ownerId);

    Optional<Note> findByIdAndOwnerId(String id, String ownerId);
}

