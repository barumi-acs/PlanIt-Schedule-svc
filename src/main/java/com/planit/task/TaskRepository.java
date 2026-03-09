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
        // 목표 있음: tasks → weekGoal → goal → category 체인으로 userId 확인
        // 목표 없음: tasks.userId 직접 확인
        @Query("SELECT t FROM TaskData t " +
                        "LEFT JOIN t.weekGoal w " +
                        "LEFT JOIN w.goal g " +
                        "LEFT JOIN g.category c " +
                        "WHERE t.targetDate = :targetDate " +
                        "  AND (" +
                        "    (t.weekGoal IS NOT NULL AND c.userId = :userId) " +
                        "    OR " +
                        "    (t.weekGoal IS NULL AND t.userId = :userId)" +
                        "  )")
        List<TaskData> findByUserIdAndTargetDate(
                        @Param("userId") String userId,
                        @Param("targetDate") LocalDate targetDate);

        // taskId로 할 일 조회 (ActionLog 전송을 위해 weekGoal → goal → category까지 fetch join)
        @Query("SELECT t FROM TaskData t " +
                        "LEFT JOIN FETCH t.weekGoal w " +
                        "LEFT JOIN FETCH w.goal g " +
                        "LEFT JOIN FETCH g.category c " +
                        "WHERE t.taskId = :taskId")
        java.util.Optional<TaskData> findByIdWithWeekGoal(@Param("taskId") Long taskId);
}
