package com.planit.category;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.planit.category.category_list.CategoryList;
import com.planit.global.BaseTimeEntity;

import lombok.Getter;
import lombok.Setter;

/**
 * category 테이블: 유저와 category_list를 연결하는 중간 테이블
 *
 * ├ category_id : PK
 * ├ list_id : FK → category_list.list_id
 * └ user_id : 유저 식별자 (User Service에서 gRPC로 검증)
 */
@Entity
@Getter
@Setter
@Table(name = "category", indexes = @Index(name = "idx_user_id", columnList = "user_id"))
@SQLDelete(sql = "UPDATE category SET deleted_at = CURRENT_TIMESTAMP(6) WHERE category_id = ?")
@SQLRestriction("deleted_at IS NULL")
public class CategoryData extends BaseTimeEntity {

    /** PK: category.category_id */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long categoryId;

    /** FK → category_list.list_id */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "list_id", nullable = false)
    private CategoryList categoryList;

    /**
     * 유저 식별자 (User Service의 UUID)
     * MSA 독립 DB 구조이므로 외래키 제약 없음 → 문자열로 관리
     */
    @Column(name = "user_id", nullable = false)
    private String userId;
}
