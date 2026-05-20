package cn.suhoan.starlight.repository;

import cn.suhoan.starlight.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AssetRepository extends JpaRepository<Asset, String> {

    Optional<Asset> findByIdAndDeletedAtIsNull(String id);

    Optional<Asset> findByIdAndOwnerIdAndDeletedAtIsNull(String id, String ownerId);

    List<Asset> findByIdInAndOwnerIdAndDeletedAtIsNull(Collection<String> ids, String ownerId);

    @Query("select coalesce(sum(a.sizeBytes), 0) from Asset a where a.owner.id = ?1 and a.deletedAt is null")
    long sumSizeBytesByOwnerId(String ownerId);

    @Query("select coalesce(sum(a.sizeBytes), 0) from Asset a where a.deletedAt is null")
    long sumSizeBytesAll();

    @Query("""
            select coalesce(sum(a.sizeBytes), 0)
            from Asset a
            where a.owner.id = ?1
              and a.deletedAt is null
              and not exists (select 1 from NoteAssetRef r where r.asset = a)
            """)
    long sumUnreferencedSizeBytesByOwnerId(String ownerId);

    @Query("""
            select coalesce(sum(a.sizeBytes), 0)
            from Asset a
            where a.deletedAt is null
              and not exists (select 1 from NoteAssetRef r where r.asset = a)
            """)
    long sumUnreferencedSizeBytesAll();

    @Query("""
            select a
            from Asset a
            where a.owner.id = ?1
              and a.deletedAt is null
              and a.unreferencedSince is not null
              and a.unreferencedSince <= ?2
              and not exists (select 1 from NoteAssetRef r where r.asset = a)
            """)
    List<Asset> findCleanupCandidatesByOwnerId(String ownerId, LocalDateTime cutoff);

    @Query("""
            select a
            from Asset a
            where a.deletedAt is null
              and a.unreferencedSince is not null
              and a.unreferencedSince <= ?1
              and not exists (select 1 from NoteAssetRef r where r.asset = a)
            """)
    List<Asset> findCleanupCandidatesAll(LocalDateTime cutoff);

    @Query("""
            select a
            from Asset a
            where a.owner.id = ?1
              and a.deletedAt is null
              and not exists (select 1 from NoteAssetRef r where r.asset = a)
            """)
    List<Asset> findUnreferencedByOwnerId(String ownerId);

    @Query("""
            select a
            from Asset a
            where a.deletedAt is null
              and not exists (select 1 from NoteAssetRef r where r.asset = a)
            """)
    List<Asset> findUnreferencedAll();
}
