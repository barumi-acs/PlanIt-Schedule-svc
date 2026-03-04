package com.planit.weekgoal;

import com.planit.global.ApiResponse;
import com.planit.weekgoal.dto.CreateWeekGoalRequest;
import com.planit.weekgoal.dto.UpdateWeekGoalRequest;
import com.planit.weekgoal.dto.UpdateWeekGoalResponse;
import com.planit.weekgoal.dto.WeekGoalListItem;
import com.planit.weekgoal.dto.WeekGoalResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/schedules/goals/{goalsId}/week-goals")
@RequiredArgsConstructor
public class WeekGoalController {

    private final WeekGoalService weekGoalService;

    // POST /api/v1/schedules/goals/{goalsId}/week-goals - 주간 목표 생성
    @PostMapping
    public ResponseEntity<ApiResponse<WeekGoalResponse>> createWeekGoal(
            @PathVariable Long goalsId,
            @RequestBody CreateWeekGoalRequest req) {
        return ResponseEntity.ok(ApiResponse.success(weekGoalService.createWeekGoal(goalsId, req)));
    }

    // GET /api/v1/schedules/goals/{goalsId}/week-goals - 주간 목표 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponse<List<WeekGoalListItem>>> getWeekGoals(
            @PathVariable Long goalsId) {
        return ResponseEntity.ok(ApiResponse.success(weekGoalService.getWeekGoals(goalsId)));
    }

    // PATCH /api/v1/schedules/goals/{goalsId}/week-goals/{weekGoalsId} - 주간 목표 수정
    @PatchMapping("/{weekGoalsId}")
    public ResponseEntity<ApiResponse<UpdateWeekGoalResponse>> updateWeekGoal(
            @PathVariable Long goalsId,
            @PathVariable Long weekGoalsId,
            @RequestBody UpdateWeekGoalRequest req) {
        return ResponseEntity.ok(ApiResponse.success(weekGoalService.updateWeekGoal(goalsId, weekGoalsId, req)));
    }

    // DELETE /api/v1/schedules/goals/{goalsId}/week-goals/{weekGoalsId} - 주간 목표 삭제
    // (Soft Delete)
    @DeleteMapping("/{weekGoalsId}")
    public ResponseEntity<ApiResponse<Void>> deleteWeekGoal(
            @PathVariable Long goalsId,
            @PathVariable Long weekGoalsId) {
        weekGoalService.deleteWeekGoal(goalsId, weekGoalsId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
