package com.planit.task;

import com.planit.global.ApiResponse;
import com.planit.task.dto.CompleteTaskResponse;
import com.planit.task.dto.CreateTaskRequest;
import com.planit.task.dto.DailyTaskResponse;
import com.planit.task.dto.FriendTaskResponse;
import com.planit.task.dto.PostponeTaskResponse;
import com.planit.task.dto.TaskResponse;
import com.planit.task.dto.UpdateTaskRequest;
import com.planit.task.dto.UpdateTaskResponse;
import com.planit.task.emoji.EmojiService;
import com.planit.task.emoji.dto.AddEmojiReactionRequest;
import com.planit.task.emoji.dto.AddEmojiReactionResponse;
import com.planit.task.emoji.dto.TaskReactionListResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Slf4j
@RestController
@RequestMapping("/api/v1/schedules/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final EmojiService emojiService;

    @PostMapping
    public ResponseEntity<ApiResponse<TaskResponse>> createTask(
            @AuthenticationPrincipal String userId,
            @RequestBody CreateTaskRequest req) {
        if (req.getUserId() == null || req.getUserId().isBlank()) {
            req.setUserId(userId);
        }
        log.debug("할 일 생성 요청");
        TaskResponse result = taskService.createTask(req);
        log.info("할 일 생성 완료", kv("taskId", result.getTaskId()));
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/daily")
    public ResponseEntity<ApiResponse<DailyTaskResponse>> getDailyTasks(
            @AuthenticationPrincipal String myUserId,
            @RequestParam(required = false) String targetDate) {
        log.debug("일별 할 일 조회 요청", kv("targetDate", targetDate));
        DailyTaskResponse result = taskService.getDailyTasks(myUserId, targetDate);
        log.info("일별 할 일 조회 완료");
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // GET /api/v1/schedules/tasks/friend/{friendUserId} - 친구의 할 일 조회
    @GetMapping("/friend/{friendUserId}")
    public ResponseEntity<ApiResponse<FriendTaskResponse>> getFriendTasks(
            @PathVariable String friendUserId,
            @AuthenticationPrincipal String myUserId,
            @RequestParam(required = false) String targetDate) {
        log.debug("친구 할 일 조회 요청", kv("friendUserId", friendUserId));
        FriendTaskResponse result = taskService.getFriendTasks(myUserId, friendUserId, targetDate);
        log.info("친구 할 일 조회 완료", kv("friendUserId", friendUserId));
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // PATCH /api/v1/schedules/tasks/{taskId} - 할 일 수정
    @PatchMapping("/{taskId}")
    public ResponseEntity<ApiResponse<UpdateTaskResponse>> updateTask(
            @PathVariable Long taskId,
            @RequestBody UpdateTaskRequest req) {
        log.debug("할 일 수정 요청", kv("taskId", taskId));
        UpdateTaskResponse result = taskService.updateTask(taskId, req);
        log.info("할 일 수정 완료", kv("taskId", taskId));
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // POST /api/v1/schedules/tasks/{taskId}/toggle - 할 일 완료 토글
    @PostMapping("/{taskId}/toggle")
    public ResponseEntity<ApiResponse<CompleteTaskResponse>> toggleComplete(
            @PathVariable Long taskId) {
        log.debug("할 일 완료 토글 요청", kv("taskId", taskId));
        CompleteTaskResponse result = taskService.toggleComplete(taskId);
        log.info("할 일 완료 토글 완료", kv("taskId", taskId), kv("isComplete", result.isComplete()));
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // POST /api/v1/schedules/tasks/{taskId}/postpone - 할 일 미루기 (targetDate +1일)
    @PostMapping("/{taskId}/postpone")
    public ResponseEntity<ApiResponse<PostponeTaskResponse>> postponeTask(
            @PathVariable Long taskId) {
        log.debug("할 일 미루기 요청", kv("taskId", taskId));
        PostponeTaskResponse result = taskService.postponeTask(taskId);
        log.info("할 일 미루기 완료", kv("taskId", taskId));
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // DELETE /api/v1/schedules/tasks/{taskId} - 할 일 삭제 (Soft Delete)
    @DeleteMapping("/{taskId}")
    public ResponseEntity<ApiResponse<Void>> deleteTask(
            @PathVariable Long taskId) {
        log.debug("할 일 삭제 요청", kv("taskId", taskId));
        taskService.deleteTask(taskId);
        log.info("할 일 삭제 완료", kv("taskId", taskId));
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // GET /api/v1/schedules/tasks/{taskId}/emojis - 할 일 이모지 반응 목록 조회 (이모지별 그룹)
    @GetMapping("/{taskId}/emojis")
    public ResponseEntity<ApiResponse<TaskReactionListResponse>> getTaskReactions(
            @PathVariable Long taskId,
            @AuthenticationPrincipal String userId) {
        log.debug("이모지 반응 목록 조회 요청", kv("taskId", taskId));
        TaskReactionListResponse result = emojiService.getReactions(taskId, userId);
        log.info("이모지 반응 목록 조회 완료", kv("taskId", taskId));
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // POST /api/v1/schedules/tasks/{taskId}/emojis - 이모지 리액션 등록
    @PostMapping("/{taskId}/emojis")
    public ResponseEntity<ApiResponse<AddEmojiReactionResponse>> addEmojiReaction(
            @PathVariable Long taskId,
            @AuthenticationPrincipal String userId,
            @RequestBody AddEmojiReactionRequest req) {
        log.debug("이모지 리액션 등록 요청", kv("taskId", taskId));
        AddEmojiReactionResponse result = emojiService.addReaction(taskId, req, userId);
        log.info("이모지 리액션 등록 완료", kv("taskId", taskId));
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // DELETE /api/v1/schedules/tasks/{taskId}/emojis/{emojiId} - 이모지 리액션 삭제
    @DeleteMapping("/{taskId}/emojis/{emojiId}")
    public ResponseEntity<ApiResponse<Void>> deleteEmojiReaction(
            @PathVariable Long taskId,
            @PathVariable Long emojiId,
            @AuthenticationPrincipal String userId) {
        log.debug("이모지 리액션 삭제 요청", kv("taskId", taskId), kv("emojiId", emojiId));
        emojiService.deleteReaction(taskId, emojiId, userId);
        log.info("이모지 리액션 삭제 완료", kv("taskId", taskId), kv("emojiId", emojiId));
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
