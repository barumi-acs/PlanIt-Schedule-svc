package com.planit.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<TaskData, Long> {

    // weekGoal로 할 일 조회 (진행률 계산용)
    List<TaskData> findByWeekGoal_WeekGoalsId(Long weekGoalsId);

    // 유저의 특정 날짜 할 일 조회
    // tasks → weekGoal → goal → category JOIN
    @Query("SELECT t FROM TaskData t " +
            "JOIN t.weekGoal w " +
            "JOIN w.goal g " +
            "JOIN g.category c " +
            "WHERE c.userId = :userId " +
            "  AND t.targetDate = :targetDate")
    List<TaskData> findByUserIdAndTargetDate(
            @Param("userId") String userId,
            @Param("targetDate") LocalDate targetDate);
}
