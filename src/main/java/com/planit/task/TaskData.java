package com.planit.task;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.planit.global.BaseTimeEntity;
import com.planit.weekgoal.WeekGoalData;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * tasks 테이블
 *
 * ├ task_id : PK
 * ├ week_goals_id : FK → week_goals.week_goals_id
 * ├ content : 할 일 내용
 * ├ complete : 완료 여부
 * └ target_date : 목표 날짜
 *
 * ☈️ user_id가 없는 이유:
 * tasks는 week_goals → goals → category 체인으로 user_id를 택함
 * TaskRepository.findByUserIdAndTargetDate() 에서 JPQL join으로 처리
 */
@Entity
@Getter
@Setter
@Table(name = "tasks")
@SQLDelete(sql = "UPDATE tasks SET deleted_at = CURRENT_TIMESTAMP(6) WHERE task_id = ?")
@SQLRestriction("deleted_at IS NULL")
public class TaskData extends BaseTimeEntity {

    /** PK: tasks.task_id */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "task_id")
    private Long taskId;

    /** FK → category.category_id (이 연결로 user_id 추적) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private com.planit.category.CategoryData category;

    /** FK → week_goals.week_goals_id (목표 없음 할 일에서는 null) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "week_goals_id", nullable = true)
    private WeekGoalData weekGoal;

    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private boolean complete = false;

    @Column(nullable = false)
    private LocalDate targetDate;
}
