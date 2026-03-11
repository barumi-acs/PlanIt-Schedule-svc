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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
        return ResponseEntity.ok(ApiResponse.success(taskService.createTask(req)));
    }

    @GetMapping("/daily")
    public ResponseEntity<ApiResponse<DailyTaskResponse>> getDailyTasks(
            @AuthenticationPrincipal String myUserId,
            @RequestParam(required = false) String targetDate) {
        return ResponseEntity.ok(ApiResponse.success(taskService.getDailyTasks(myUserId, targetDate)));
    }

    // GET /api/v1/schedules/tasks/friend/{friendUserId} - 친구의 할 일 조회
    @GetMapping("/friend/{friendUserId}")
    public ResponseEntity<ApiResponse<FriendTaskResponse>> getFriendTasks(
            @PathVariable String friendUserId,
            @AuthenticationPrincipal String myUserId,
            @RequestParam(required = false) String targetDate) {
        return ResponseEntity.ok(ApiResponse.success(
                taskService.getFriendTasks(myUserId, friendUserId, targetDate)));
    }

    // PATCH /api/v1/schedules/tasks/{taskId} - 할 일 수정
    @PatchMapping("/{taskId}")
    public ResponseEntity<ApiResponse<UpdateTaskResponse>> updateTask(
            @PathVariable Long taskId,
            @RequestBody UpdateTaskRequest req) {
        return ResponseEntity.ok(ApiResponse.success(taskService.updateTask(taskId, req)));
    }

    // POST /api/v1/schedules/tasks/{taskId}/toggle - 할 일 완료 토글
    @PostMapping("/{taskId}/toggle")
    public ResponseEntity<ApiResponse<CompleteTaskResponse>> toggleComplete(
            @PathVariable Long taskId) {
        return ResponseEntity.ok(ApiResponse.success(taskService.toggleComplete(taskId)));
    }

    // POST /api/v1/schedules/tasks/{taskId}/postpone - 할 일 미루기 (targetDate +1일)
    @PostMapping("/{taskId}/postpone")
    public ResponseEntity<ApiResponse<PostponeTaskResponse>> postponeTask(
            @PathVariable Long taskId) {
        return ResponseEntity.ok(ApiResponse.success(taskService.postponeTask(taskId)));
    }

    // DELETE /api/v1/schedules/tasks/{taskId} - 할 일 삭제 (Soft Delete)
    @DeleteMapping("/{taskId}")
    public ResponseEntity<ApiResponse<Void>> deleteTask(
            @PathVariable Long taskId) {
        taskService.deleteTask(taskId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // GET /api/v1/schedules/tasks/{taskId}/emojis - 할 일 이모지 반응 목록 조회 (이모지별 그룹)
    @GetMapping("/{taskId}/emojis")
    public ResponseEntity<ApiResponse<TaskReactionListResponse>> getTaskReactions(
            @PathVariable Long taskId,
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(ApiResponse.success(emojiService.getReactions(taskId, userId)));
    }

    // POST /api/v1/schedules/tasks/{taskId}/emojis - 이모지 리액션 등록
    @PostMapping("/{taskId}/emojis")
    public ResponseEntity<ApiResponse<AddEmojiReactionResponse>> addEmojiReaction(
            @PathVariable Long taskId,
            @AuthenticationPrincipal String userId,
            @RequestBody AddEmojiReactionRequest req) {
        return ResponseEntity.ok(ApiResponse.success(
                emojiService.addReaction(taskId, req, userId)));
    }

    // DELETE /api/v1/schedules/tasks/{taskId}/emojis/{emojiId} - 이모지 리액션 삭제
    @DeleteMapping("/{taskId}/emojis/{emojiId}")
    public ResponseEntity<ApiResponse<Void>> deleteEmojiReaction(
            @PathVariable Long taskId,
            @PathVariable Long emojiId,
            @AuthenticationPrincipal String userId) {
        emojiService.deleteReaction(taskId, emojiId, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
