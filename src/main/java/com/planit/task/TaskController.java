package com.planit.task;

import com.planit.global.ApiResponse;
import com.planit.task.dto.CreateTaskRequest;
import com.planit.task.dto.DailyTaskResponse;
import com.planit.task.dto.FriendTaskResponse;
import com.planit.task.dto.TaskResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/schedules/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    // POST /api/v1/schedules/tasks - 할 일 등록
    @PostMapping
    public ResponseEntity<ApiResponse<TaskResponse>> createTask(@RequestBody CreateTaskRequest req) {
        return ResponseEntity.ok(ApiResponse.success(taskService.createTask(req)));
    }

    // GET /api/v1/schedules/tasks/daily - 일간 할 일 조회
    // 임시: JWT 연동 전까지 myUserId를 Query Param으로 전달 (없으면 dev-user-001 기본값)
    @GetMapping("/daily")
    public ResponseEntity<ApiResponse<DailyTaskResponse>> getDailyTasks(
            @RequestParam(required = false, defaultValue = "dev-user-001") String myUserId,
            @RequestParam(required = false) String targetDate) {
        return ResponseEntity.ok(ApiResponse.success(taskService.getDailyTasks(myUserId, targetDate)));
    }

    // GET /api/v1/schedules/tasks/friend/{friendUserId} - 친구의 할 일 조회
    // 임시: JWT 연동 전까지 myUserId를 Query Param으로 전달 (없으면 dev-user-001 기본값)
    @GetMapping("/friend/{friendUserId}")
    public ResponseEntity<ApiResponse<FriendTaskResponse>> getFriendTasks(
            @PathVariable String friendUserId,
            @RequestParam(required = false, defaultValue = "dev-user-001") String myUserId,
            @RequestParam(required = false) String targetDate) {
        return ResponseEntity.ok(ApiResponse.success(
                taskService.getFriendTasks(myUserId, friendUserId, targetDate)));
    }
}
