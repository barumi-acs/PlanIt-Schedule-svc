package com.planit.category.category_list;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategoryListRepository extends JpaRepository<CategoryList, Long> {
    // 카테고리 이름으로 조회
    Optional<CategoryList> findByName(String name);
}
