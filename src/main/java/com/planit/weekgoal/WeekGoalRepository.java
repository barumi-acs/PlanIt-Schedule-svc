package com.planit.weekgoal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WeekGoalRepository extends JpaRepository<WeekGoalData, Long> {
    List<WeekGoalData> findByGoal_GoalsId(Long goalsId);
}
