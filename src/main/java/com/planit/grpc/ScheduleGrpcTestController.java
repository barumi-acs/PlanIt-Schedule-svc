package com.planit.grpc;

import com.planit.grpc.schedule.CreatePlanRequest;
import com.planit.grpc.schedule.Goal;
import com.planit.grpc.schedule.Task;
import com.planit.grpc.schedule.WeekGoal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * gRPC 수신 로직 테스트용 임시 REST API
 * 
 * <p>
 * <b>⚠️ 주의: 개발/테스트 전용</b>
 * - 실제 프로덕션에서는 이 컨트롤러를 삭제하거나 비활성화해야 함
 * - gRPC 수신 로직이 잘 동작하는지 확인하기 위한 용도
 * 
 * <p>
 * <b>테스트 방법:</b>
 * <pre>
 * curl -X POST http://$PLANIT_SCHEDULE_SERVICE_HOST:8082/api/v1/test/grpc/plan \
 *   -H "Content-Type: application/json" \
 *   -d '{
 *     "userId": "test-user-123",
 *     "categoryName": "어학/자격증",
 *     "goalTitle": "AWS 자격증 취득",
 *     "startDate": "2026-03-03",
 *     "endDate": "2026-03-28"
 *   }'
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/test/grpc")
@RequiredArgsConstructor
public class ScheduleGrpcTestController {

    private final ScheduleGrpcService scheduleGrpcService;

    /**
     * gRPC 수신 로직 테스트용 간단한 엔드포인트
     * 
     * @param request 간단한 JSON 요청
     * @return 수신 성공 메시지
     */
    @PostMapping("/plan")
    public Map<String, Object> testCreatePlan(@RequestBody TestPlanRequest request) {
        log.info("🧪 테스트 요청 수신 - userId: {}, goal: {}", 
                 request.getUserId(), request.getGoalTitle());

        // JSON → gRPC Proto 메시지 변환
        CreatePlanRequest grpcRequest = CreatePlanRequest.newBuilder()
                .setUserId(request.getUserId())
                .setCategoryName(request.getCategoryName())
                .setGoal(Goal.newBuilder()
                        .setTitle(request.getGoalTitle())
                        .setStartDate(request.getStartDate())
                        .setEndDate(request.getEndDate())
                        .addWeekGoals(WeekGoal.newBuilder()
                                .setTitle("1주차: 기초 학습")
                                .addTasks(Task.newBuilder()
                                        .setContent("IAM, EC2, S3 학습")
                                        .setTargetDate("2026-03-04")
                                        .build())
                                .addTasks(Task.newBuilder()
                                        .setContent("VPC 개념 정리")
                                        .setTargetDate("2026-03-05")
                                        .build())
                                .build())
                        .addWeekGoals(WeekGoal.newBuilder()
                                .setTitle("2주차: 실습")
                                .addTasks(Task.newBuilder()
                                        .setContent("EC2 인스턴스 생성 실습")
                                        .setTargetDate("2026-03-11")
                                        .build())
                                .build())
                        .build())
                .build();

        // gRPC 서비스 호출 (로그 출력만)
        scheduleGrpcService.createPlan(grpcRequest);

        log.info("✅ 테스트 수신 완료");

        return Map.of(
                "success", true,
                "message", "gRPC 수신 로직 테스트 성공 (DB 저장 없음)"
        );
    }

    /**
     * 간단한 테스트 요청 DTO
     */
    public static class TestPlanRequest {
        private String userId;
        private String categoryName;
        private String goalTitle;
        private String startDate;
        private String endDate;

        // Getters & Setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public String getCategoryName() { return categoryName; }
        public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
        
        public String getGoalTitle() { return goalTitle; }
        public void setGoalTitle(String goalTitle) { this.goalTitle = goalTitle; }
        
        public String getStartDate() { return startDate; }
        public void setStartDate(String startDate) { this.startDate = startDate; }
        
        public String getEndDate() { return endDate; }
        public void setEndDate(String endDate) { this.endDate = endDate; }
    }
}
