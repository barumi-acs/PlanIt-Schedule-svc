package com.planit.category.category_list;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface CategoryListRepository extends JpaRepository<CategoryList, Long> {
    // 카테고리 이름으로 조회
    Optional<CategoryList> findByName(String name);

    // User-svc에서 동기화 시 upsert (list_id를 User-svc category_id와 일치시킴)
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO category_list (list_id, name, color_hex, description, created_at, updated_at) " +
            "VALUES (:listId, :name, :colorHex, :description, NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE name = VALUES(name), color_hex = VALUES(color_hex), description = VALUES(description), updated_at = NOW()",
            nativeQuery = true)
    void upsert(@Param("listId") Long listId,
                @Param("name") String name,
                @Param("colorHex") String colorHex,
                @Param("description") String description);
}
