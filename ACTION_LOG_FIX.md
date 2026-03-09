# ActionLog 전송 안 되는 문제 수정

## 문제 상황
`/api/v1/schedules/tasks/{taskId}/complete` API 호출 시 Insight Service로 ActionLog가 전송되지 않음 (DB INSERT 안 됨)

## 원인 분석

### 1. LAZY 로딩 문제
```java
// 기존 코드
TaskData task = taskRepository.findById(taskId)
    .orElseThrow(() -> new CustomException(ErrorCode.S4041));

// weekGoal이 LAZY 로딩되어 프록시 상태
// extractGoalsId()에서 task.getWeekGoal().getGoal().getGoalsId() 접근 시
// LazyInitializationException 또는 null 반환 가능
```

### 2. Null 체크 로직
```java
if (userId != null && goalsId != null) {
    // ActionLog 전송
}
```
- `goalsId`가 `null`이면 ActionLog 전송 안 함
- 목표 없음 할 일(`weekGoal == null`)은 의도적으로 로그 안 남김
- 하지만 목표 있음 할 일도 LAZY 로딩 실패로 `null` 반환 가능

### 3. 로그 부족
- 디버깅용 로그가 없어 어느 단계에서 실패하는지 파악 어려움

## 해결 방법

### 1. Fetch Join 추가
```java
// TaskRepository.java
@Query("SELECT t FROM TaskData t " +
        "LEFT JOIN FETCH t.weekGoal w " +
        "LEFT JOIN FETCH w.goal g " +
        "LEFT JOIN FETCH g.category c " +
        "WHERE t.taskId = :taskId")
java.util.Optional<TaskData> findByIdWithWeekGoal(@Param("taskId") Long taskId);
```

### 2. TaskService 수정
```java
// toggleComplete(), postponeTask(), deleteTask()에서 사용
TaskData task = taskRepository.findByIdWithWeekGoal(taskId)
    .orElseThrow(() -> new CustomException(ErrorCode.S4041));
```

### 3. 로그 추가
```java
log.info("[TaskService] toggleComplete: taskId={}, userId={}, goalsId={}, hasWeekGoal={}",
    taskId, userId, goalsId, task.getWeekGoal() != null);

if (userId != null && goalsId != null) {
    log.info("[TaskService] Sending ActionLog: userId={}, taskId={}, goalsId={}",
        userId, taskId, goalsId);
    actionLogGrpcClient.recordCompletedAction(...);
} else {
    log.warn("[TaskService] Skipping ActionLog: userId={}, goalsId={} (목표 없음 할 일 또는 데이터 누락)",
        userId, goalsId);
}
```

### 4. 안전한 추출 메서드
```java
private String extractUserId(TaskData task) {
    try {
        if (task.getWeekGoal() != null) {
            WeekGoalData weekGoal = task.getWeekGoal();
            if (weekGoal.getGoal() != null && 
                weekGoal.getGoal().getCategory() != null) {
                return weekGoal.getGoal().getCategory().getUserId();
            }
            log.warn("[TaskService] extractUserId: weekGoal exists but goal/category is null, taskId={}",
                task.getTaskId());
            return null;
        } else {
            return task.getUserId();
        }
    } catch (Exception e) {
        log.error("[TaskService] extractUserId failed: taskId={}", task.getTaskId(), e);
        return null;
    }
}
```

## 테스트 방법

### 1. Schedule-svc 재시작
```bash
cd PlanIt-Schedule-svc
./gradlew bootRun
```

### 2. Task 완료 API 호출
```bash
curl -X PATCH http://localhost:8083/api/v1/schedules/tasks/3/complete
```

### 3. 로그 확인
```
[TaskService] toggleComplete: taskId=3, userId=user123, goalsId=1, hasWeekGoal=true
[TaskService] Sending ActionLog: userId=user123, taskId=3, goalsId=1
[ActionLog] Successfully recorded COMPLETED: user=user123, task=3, goals=1, logId=xxx
```

### 4. DB 확인
```sql
-- Insight Service DB
SELECT * FROM user_action_logs 
WHERE user_id = 'user123' 
  AND task_id = 3 
  AND action_type = 'COMPLETED'
ORDER BY action_time DESC 
LIMIT 1;
```

## 수정 파일
- `PlanIt-Schedule-svc/src/main/java/com/planit/task/TaskRepository.java`
  - `findByIdWithWeekGoal()` 메서드 추가 (fetch join)
- `PlanIt-Schedule-svc/src/main/java/com/planit/task/TaskService.java`
  - `@Slf4j` 추가
  - `toggleComplete()`, `postponeTask()`, `deleteTask()`에서 `findByIdWithWeekGoal()` 사용
  - 디버깅 로그 추가
  - `extractUserId()`, `extractGoalsId()`에 예외 처리 및 로그 추가

## 주의사항
- **목표 없음 할 일**(`weekGoal == null`)은 의도적으로 ActionLog를 전송하지 않음
- **목표 있음 할 일**만 ActionLog 전송 대상
- LAZY 로딩 문제는 `@Transactional` 범위 내에서만 발생하므로 fetch join으로 해결

---
**작성일**: 2026-03-09
**상태**: ✅ 수정 완료
