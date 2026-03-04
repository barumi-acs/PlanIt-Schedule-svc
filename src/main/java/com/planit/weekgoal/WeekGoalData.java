package com.planit.weekgoal;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.planit.global.BaseTimeEntity;

import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "week_goals")
@SQLDelete(sql = "UPDATE week_goals SET deleted_at = CURRENT_TIMESTAMP(6) WHERE week_goals_id = ?")
@SQLRestriction("deleted_at IS NULL")
public class WeekGoalData extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "week_goals_id")
    private Long weekGoalsId;

    @Column(name = "goals_id", nullable = false)
    private Long goalsId;

    @Column(nullable = false)
    private String title;
}
