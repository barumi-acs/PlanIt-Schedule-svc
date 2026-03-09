package com.planit.category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<CategoryData, Long> {

    // 유저의 카테고리 전체 조회
    List<CategoryData> findByUserId(String userId);

    // 유저 + 카테고리 리스트 타입으로 단건 조회 (목표 생성 시 category_id 확인용)
    Optional<CategoryData> findByUserIdAndCategoryList_ListId(String userId, Long listId);
}
