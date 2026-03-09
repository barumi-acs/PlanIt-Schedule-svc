package com.planit.goal;

import com.planit.global.ApiResponse;
import com.planit.goal.dto.CreateGoalRequest;
import com.planit.goal.dto.GoalDetailResponse;
import com.planit.goal.dto.GoalResponse;
import com.planit.goal.dto.UpdateGoalRequest;
import com.planit.goal.dto.UpdateGoalResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/schedules/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    // POST /api/v1/schedules/goals - 목표 생성
    // 임시: JWT 연동 전까지 userId를 Query Param으로 전달 (없으면 dev-user-001 기본값)
    @PostMapping
    public ResponseEntity<ApiResponse<GoalResponse>> createGoal(
            @RequestParam(required = false, defaultValue = "dev-user-001") String userId,
            @RequestBody CreateGoalRequest req) {
        GoalResponse data = goalService.createGoal(userId, req);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    // GET /api/v1/schedules/goals - 목표 전체 조회
    // 임시: JWT 연동 전까지 userId를 Query Param으로 전달 (없으면 dev-user-001 기본값)
    @GetMapping
    public ResponseEntity<ApiResponse<List<GoalResponse>>> getGoals(
            @RequestParam(required = false, defaultValue = "dev-user-001") String userId) {
        List<GoalResponse> data = goalService.getGoals(userId);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    // DELETE /api/v1/schedules/goals/{id} - 목표 삭제 (Soft Delete)
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteGoal(@PathVariable Long id) {
        goalService.deleteGoal(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // GET /api/v1/schedules/goals/{goalsId} - 목표 단건 조회
    @GetMapping("/{goalsId}")
    public ResponseEntity<ApiResponse<GoalDetailResponse>> getGoal(@PathVariable Long goalsId) {
        return ResponseEntity.ok(ApiResponse.success(goalService.getGoal(goalsId)));
    }

    // PATCH /api/v1/schedules/goals/{goalsId} - 목표 수정
    @PatchMapping("/{goalsId}")
    public ResponseEntity<ApiResponse<UpdateGoalResponse>> updateGoal(
            @PathVariable Long goalsId,
            @RequestBody UpdateGoalRequest req) {
        return ResponseEntity.ok(ApiResponse.success(goalService.updateGoal(goalsId, req)));
    }
}
