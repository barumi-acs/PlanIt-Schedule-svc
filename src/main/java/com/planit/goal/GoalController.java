package com.planit.goal;

import com.planit.global.ApiResponse;
import com.planit.goal.dto.CreateGoalRequest;
import com.planit.goal.dto.GoalDetailResponse;
import com.planit.goal.dto.GoalResponse;
import com.planit.goal.dto.UpdateGoalRequest;
import com.planit.goal.dto.UpdateGoalResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Slf4j
@RestController
@RequestMapping("/api/v1/schedules/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    // POST /api/v1/schedules/goals - 목표 생성
    @PostMapping
    public ResponseEntity<ApiResponse<GoalResponse>> createGoal(
            @AuthenticationPrincipal String userId,
            @RequestBody CreateGoalRequest req) {
        log.debug("목표 생성 요청", kv("title", req.getTitle()));
        GoalResponse data = goalService.createGoal(userId, req);
        log.info("목표 생성 완료", kv("goalsId", data.getGoalsId()));
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    // GET /api/v1/schedules/goals - 목표 전체 조회
    @GetMapping
    public ResponseEntity<ApiResponse<List<GoalResponse>>> getGoals(
            @AuthenticationPrincipal String userId) {
        log.debug("목표 전체 조회 요청");
        List<GoalResponse> data = goalService.getGoals(userId);
        log.info("목표 전체 조회 완료", kv("count", data.size()));
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    // DELETE /api/v1/schedules/goals/{id} - 목표 삭제 (Soft Delete)
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteGoal(@PathVariable Long id) {
        log.debug("목표 삭제 요청", kv("goalsId", id));
        goalService.deleteGoal(id);
        log.info("목표 삭제 완료", kv("goalsId", id));
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // GET /api/v1/schedules/goals/{goalsId} - 목표 단건 조회
    @GetMapping("/{goalsId}")
    public ResponseEntity<ApiResponse<GoalDetailResponse>> getGoal(@PathVariable Long goalsId) {
        log.debug("목표 단건 조회 요청", kv("goalsId", goalsId));
        GoalDetailResponse data = goalService.getGoal(goalsId);
        log.info("목표 단건 조회 완료", kv("goalsId", goalsId));
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    // PATCH /api/v1/schedules/goals/{goalsId} - 목표 수정
    @PatchMapping("/{goalsId}")
    public ResponseEntity<ApiResponse<UpdateGoalResponse>> updateGoal(
            @PathVariable Long goalsId,
            @RequestBody UpdateGoalRequest req) {
        log.debug("목표 수정 요청", kv("goalsId", goalsId));
        UpdateGoalResponse data = goalService.updateGoal(goalsId, req);
        log.info("목표 수정 완료", kv("goalsId", goalsId));
        return ResponseEntity.ok(ApiResponse.success(data));
    }
}
