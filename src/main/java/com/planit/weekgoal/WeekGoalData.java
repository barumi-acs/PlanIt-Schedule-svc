package com.planit.weekgoal;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.planit.global.BaseTimeEntity;
import com.planit.goal.GoalData;

import lombok.Getter;
import lombok.Setter;

/**
 * week_goals 테이블
 *
 * ├ week_goals_id : PK
 * ├ goals_id : FK → goals.goals_id
 * └ title : 주간 목표 제목
 */
@Entity
@Getter
@Setter
@Table(name = "week_goals")
@SQLDelete(sql = "UPDATE week_goals SET deleted_at = CURRENT_TIMESTAMP(6) WHERE week_goals_id = ?")
@SQLRestriction("deleted_at IS NULL")
public class WeekGoalData extends BaseTimeEntity {

    /** PK: week_goals.week_goals_id */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "week_goals_id")
    private Long weekGoalsId;

    /** FK → goals.goals_id */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goals_id", nullable = false)
    private GoalData goal;

    @Column(nullable = false)
    private String title;
}
