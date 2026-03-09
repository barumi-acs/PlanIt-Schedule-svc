package com.planit.grpc;

import com.planit.grpc.schedule.CreatePlanRequest;
import com.planit.grpc.schedule.Task;
import com.planit.grpc.schedule.WeekGoal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * gRPC로 받은 계획 데이터를 처리하는 서비스
 * 
 * <p>
 * <b>현재 역할:</b>
 * - Strategy Service로부터 gRPC 요청 수신
 * - 요청 데이터를 로그로 출력
 * - DB 저장 없이 수신 확인만 수행
 * 
 * <p>
 * <b>향후 확장:</b>
 * - DB 저장 로직은 필요 시 추가 예정
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleGrpcService {

    /**
     * gRPC로 받은 계획 데이터를 로그로 출력
     * 
     * @param request Strategy Service에서 받은 CreatePlanRequest
     */
    public void createPlan(CreatePlanRequest request) {
        log.info("📡 Strategy에서 실행 계획 수신");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("👤 userId: {}", request.getUserId());
        log.info("📂 category: {}", request.getCategoryName());
        log.info("🎯 goal: {}", request.getGoal().getTitle());
        log.info("📅 period: {} ~ {}", request.getGoal().getStartDate(), request.getGoal().getEndDate());
        log.info("📊 week_goals count: {}", request.getGoal().getWeekGoalsCount());
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // 주차별 목표 및 할 일 출력
        int weekIndex = 1;
        for (WeekGoal weekGoal : request.getGoal().getWeekGoalsList()) {
            log.info("  📌 Week {}: {}", weekIndex, weekGoal.getTitle());
            
            int taskIndex = 1;
            for (Task task : weekGoal.getTasksList()) {
                log.info("    ✓ Task {}: {} | targetDate: {}", 
                         taskIndex, task.getContent(), task.getTargetDate());
                taskIndex++;
            }
            
            weekIndex++;
        }
        
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("✅ 계획 수신 완료");
    }
}
