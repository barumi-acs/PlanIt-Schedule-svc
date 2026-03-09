package com.planit.goal;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.planit.category.CategoryData;
import com.planit.global.BaseTimeEntity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * goals 테이블
 *
 * ├ goals_id : PK
 * ├ category_id : FK → category.category_id
 * ├ title : 목표 제목
 * ├ start_date : 시작일
 * └ end_date : 종료일
 */
@Entity
@Getter
@Setter
@Table(name = "goals")
@SQLDelete(sql = "UPDATE goals SET deleted_at = CURRENT_TIMESTAMP(6) WHERE goals_id = ?")
@SQLRestriction("deleted_at IS NULL")
public class GoalData extends BaseTimeEntity {

    /** PK: goals.goals_id */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "goals_id")
    private Long goalsId;

    /** FK → category.category_id (이 연결로 user_id 추적) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private CategoryData category;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;
}
