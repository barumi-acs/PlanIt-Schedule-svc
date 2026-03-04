package com.planit.goal.create;

import com.planit.global.ApiResponse;
import com.planit.goal.create.dto.CreateGoalRequest;
import com.planit.goal.create.dto.GoalResponse;

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
    @PostMapping
    public ResponseEntity<ApiResponse<GoalResponse>> createGoal(@RequestBody CreateGoalRequest req) {
        GoalResponse data = goalService.createGoal(req);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    // GET /api/v1/schedules/goals - 목표 전체 조회
    @GetMapping
    public ResponseEntity<ApiResponse<List<GoalResponse>>> getGoals() {
        List<GoalResponse> data = goalService.getGoals();
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    // DELETE /api/v1/schedules/goals/{id} - 목표 삭제 (Soft Delete)
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteGoal(@PathVariable Long id) {
        goalService.deleteGoal(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
