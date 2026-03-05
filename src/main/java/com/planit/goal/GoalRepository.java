package com.planit.goal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GoalRepository extends JpaRepository<GoalData, Long> {

    // userId 기준으로 목표 조회
    @Query("SELECT g FROM GoalData g JOIN g.category c WHERE c.userId = :userId")
    List<GoalData> findByUserId(@Param("userId") String userId);
}
