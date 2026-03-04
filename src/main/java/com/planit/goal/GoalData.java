package com.planit.goal;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.planit.global.BaseTimeEntity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Getter
@Setter
@Table(name = "goals")
@SQLDelete(sql = "UPDATE goals SET deleted_at = CURRENT_TIMESTAMP(6) WHERE goals_id = ?") // soft delete를 위한 가로채기 설정
@SQLRestriction("deleted_at IS NULL") // soft delete를 위한 설정
public class GoalData extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "goals_id")
    private Long goalsId;

    @Column(name = "list_id")
    private Long listId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;
}
