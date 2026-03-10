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

    // 유저의 특정 날짜 할 일 조회 (카테고리 및 카테고리 리스트 페치 조인)
    @Query("SELECT t FROM TaskData t " +
           "JOIN FETCH t.category c " +
           "JOIN FETCH c.categoryList cl " +
           "WHERE c.userId = :userId AND t.targetDate = :targetDate")
    List<TaskData> findByUserIdAndTargetDate(
            @Param("userId") String userId,
            @Param("targetDate") LocalDate targetDate);

    // taskId로 할 일 조회 (카테고리/주간목표/목표 페치 조인)
    @Query("SELECT t FROM TaskData t " +
           "JOIN FETCH t.category c " +
           "LEFT JOIN FETCH t.weekGoal w " +
           "LEFT JOIN FETCH w.goal g " +
           "WHERE t.taskId = :taskId")
    java.util.Optional<TaskData> findByIdWithWeekGoal(@Param("taskId") Long taskId);
}
