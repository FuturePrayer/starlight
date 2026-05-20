package cn.suhoan.starlight.repository;

import cn.suhoan.starlight.entity.NoteAssetRef;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface NoteAssetRefRepository extends JpaRepository<NoteAssetRef, String> {

    List<NoteAssetRef> findByNoteId(String noteId);

    long countByAssetId(String assetId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from NoteAssetRef r where r.note.id = ?1")
    void deleteByNoteId(String noteId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from NoteAssetRef r where r.note.id in ?1")
    void deleteByNoteIdIn(Collection<String> noteIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from NoteAssetRef r where r.asset.id = ?1")
    void deleteByAssetId(String assetId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from NoteAssetRef r where r.asset.id in ?1")
    void deleteByAssetIdIn(Collection<String> assetIds);
}
