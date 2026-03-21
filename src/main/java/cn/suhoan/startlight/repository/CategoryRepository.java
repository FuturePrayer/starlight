package cn.suhoan.startlight.repository;

import cn.suhoan.startlight.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, String> {

    List<Category> findByOwnerIdOrderByNameAsc(String ownerId);

    Optional<Category> findByIdAndOwnerId(String id, String ownerId);
}

