package com.planit.weekgoal;

import com.planit.global.ApiResponse;
import com.planit.weekgoal.dto.CreateWeekGoalRequest;
import com.planit.weekgoal.dto.UpdateWeekGoalRequest;
import com.planit.weekgoal.dto.UpdateWeekGoalResponse;
import com.planit.weekgoal.dto.WeekGoalListItem;
import com.planit.weekgoal.dto.WeekGoalResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Slf4j
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
        log.debug("주간 목표 생성 요청", kv("goalsId", goalsId), kv("title", req.getTitle()));
        WeekGoalResponse result = weekGoalService.createWeekGoal(goalsId, req);
        log.info("주간 목표 생성 완료", kv("goalsId", goalsId), kv("weekGoalsId", result.getWeekGoalsId()));
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // GET /api/v1/schedules/goals/{goalsId}/week-goals - 주간 목표 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponse<List<WeekGoalListItem>>> getWeekGoals(
            @PathVariable Long goalsId) {
        log.debug("주간 목표 목록 조회 요청", kv("goalsId", goalsId));
        List<WeekGoalListItem> result = weekGoalService.getWeekGoals(goalsId);
        log.info("주간 목표 목록 조회 완료", kv("goalsId", goalsId), kv("count", result.size()));
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // PATCH /api/v1/schedules/goals/{goalsId}/week-goals/{weekGoalsId} - 주간 목표 수정
    @PatchMapping("/{weekGoalsId}")
    public ResponseEntity<ApiResponse<UpdateWeekGoalResponse>> updateWeekGoal(
            @PathVariable Long goalsId,
            @PathVariable Long weekGoalsId,
            @RequestBody UpdateWeekGoalRequest req) {
        log.debug("주간 목표 수정 요청", kv("goalsId", goalsId), kv("weekGoalsId", weekGoalsId));
        UpdateWeekGoalResponse result = weekGoalService.updateWeekGoal(goalsId, weekGoalsId, req);
        log.info("주간 목표 수정 완료", kv("weekGoalsId", weekGoalsId));
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // DELETE /api/v1/schedules/goals/{goalsId}/week-goals/{weekGoalsId} - 주간 목표 삭제
    // (Soft Delete)
    @DeleteMapping("/{weekGoalsId}")
    public ResponseEntity<ApiResponse<Void>> deleteWeekGoal(
            @PathVariable Long goalsId,
            @PathVariable Long weekGoalsId) {
        log.debug("주간 목표 삭제 요청", kv("goalsId", goalsId), kv("weekGoalsId", weekGoalsId));
        weekGoalService.deleteWeekGoal(goalsId, weekGoalsId);
        log.info("주간 목표 삭제 완료", kv("weekGoalsId", weekGoalsId));
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
