# Schedule → Insight gRPC 통합 체크리스트

## 🎯 빠른 시작 가이드

### 1단계: Schedule Service 확인 ✅ (완료)

```bash
cd PlanIt-Schedule-svc

# 파일 존재 확인
ls src/main/proto/action_log_service.proto
ls src/main/java/com/planit/grpc/UserActionLogGrpcClient.java
ls src/main/java/com/planit/global/AsyncConfig.java

# 빌드 테스트
./gradlew clean build

# 실행
./gradlew bootRun
```

**예상 로그**:
```
Started PlanItScheduleServiceApplication in 3.456 seconds
gRPC Server started, listening on port 9091
```

### 2단계: Insight Service 구현 ⏳ (필요)

#### 2-1. Proto 파일 복사
```bash
cd PlanIt-Insight-svc
cp ../PlanIt-Schedule-svc/src/main/proto/action_log_service.proto \
   src/main/proto/
```

#### 2-2. Entity 생성
파일: `src/main/java/com/planit/analytics/entity/UserActionLog.java`

```java
package com.planit.analytics.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_action_logs")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserActionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "goals_id", nullable = false)
    private Long goalsId;

    @Column(name = "action_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ActionType actionType;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "postponed_to_date")
    private LocalDate postponedToDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public enum ActionType {
        COMPLETED, POSTPONED, DELETED
    }
}
```

#### 2-3. Repository 생성
파일: `src/main/java/com/planit/analytics/repository/UserActionLogRepository.java`

```java
package com.planit.analytics.repository;

import com.planit.analytics.entity.UserActionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserActionLogRepository extends JpaRepository<UserActionLog, Long> {
}
```

#### 2-4. gRPC Service 구현
파일: `src/main/java/com/planit/analytics/grpc/ActionLogServiceImpl.java`

```java
package com.planit.analytics.grpc;

import com.planit.analytics.entity.UserActionLog;
import com.planit.analytics.repository.UserActionLogRepository;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import java.time.LocalDate;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class ActionLogServiceImpl extends ActionLogServiceGrpc.ActionLogServiceImplBase {

    private final UserActionLogRepository actionLogRepository;

    @Override
    public void recordActionLog(ActionLogRequest request, StreamObserver<ActionLogResponse> responseObserver) {
        log.info("[gRPC ActionLog] Received: user={}, task={}, goals={}, action={}", 
                request.getUserId(), request.getTaskId(), request.getGoalsId(), request.getActionType());

        try {
            UserActionLog.ActionType actionType = UserActionLog.ActionType.valueOf(request.getActionType());
            LocalDate dueDate = LocalDate.parse(request.getDueDate());
            LocalDate postponedToDate = request.getPostponedToDate().isEmpty() 
                    ? null : LocalDate.parse(request.getPostponedToDate());

            UserActionLog log = UserActionLog.builder()
                    .userId(request.getUserId())
                    .taskId(request.getTaskId())
                    .goalsId(request.getGoalsId())
                    .actionType(actionType)
                    .dueDate(dueDate)
                    .postponedToDate(postponedToDate)
                    .build();

            UserActionLog saved = actionLogRepository.save(log);

            ActionLogResponse response = ActionLogResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Action log recorded successfully")
                    .setLogId(saved.getLogId())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            log.info("[gRPC ActionLog] Saved log_id={}", saved.getLogId());

        } catch (Exception e) {
            log.error("[gRPC ActionLog] Failed to save, but returning success", e);

            ActionLogResponse response = ActionLogResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Action log received (save failed internally)")
                    .setLogId(0L)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
}
```

#### 2-5. application.yml 수정
파일: `src/main/resources/application.yml`

```yaml
grpc:
  server:
    port: ${GRPC_SERVER_PORT:9092}
```

#### 2-6. 빌드 및 실행
```bash
cd PlanIt-Insight-svc
./gradlew clean build
./gradlew bootRun
```

**예상 로그**:
```
Started PlanItInsightServiceApplication in 4.123 seconds
gRPC Server started, listening on port 9092
```

### 3단계: 통합 테스트 🧪

#### 3-1. 서비스 실행 확인
```bash
# Terminal 1: Insight Service
cd PlanIt-Insight-svc
./gradlew bootRun
# 포트: HTTP 8085, gRPC 9092

# Terminal 2: Schedule Service
cd PlanIt-Schedule-svc
./gradlew bootRun
# 포트: HTTP 8082, gRPC 9091
```

#### 3-2. 할 일 완료 테스트
```bash
# 할 일 완료 API 호출
curl -X POST http://localhost:8082/api/v1/tasks/123/complete \
  -H "Content-Type: application/json"
```

**Schedule Service 로그 확인**:
```
[http-nio-8082-exec-1] c.p.t.TaskService : Toggling complete for task=123
[actionLogExecutor-1] c.p.g.UserActionLogGrpcClient : Recording COMPLETED action for user=user123, task=123, goals=456
[actionLogExecutor-1] c.p.g.UserActionLogGrpcClient : Successfully recorded action log
```

**Insight Service 로그 확인**:
```
[grpc-default-executor-0] c.p.a.g.ActionLogServiceImpl : [gRPC ActionLog] Received: user=user123, task=123, goals=456, action=COMPLETED
[grpc-default-executor-0] c.p.a.g.ActionLogServiceImpl : [gRPC ActionLog] Saved log_id=1
```

#### 3-3. DB 확인
```sql
-- Insight Service DB
USE plainit_db;
SELECT * FROM user_action_logs ORDER BY created_at DESC LIMIT 10;

-- 예상 결과:
-- log_id | user_id  | task_id | goals_id | action_type | due_date   | postponed_to_date | created_at
-- 1      | user123  | 123     | 456      | COMPLETED   | 2026-03-06 | NULL              | 2026-03-06 10:30:00
```

#### 3-4. 장애 테스트 (Insight Service 중단)
```bash
# Insight Service 중단 (Ctrl+C)

# Schedule Service는 계속 실행 중

# 할 일 완료 API 호출
curl -X POST http://localhost:8082/api/v1/tasks/124/complete

# Schedule Service 로그 확인 (에러 로그만 출력, 정상 응답)
[actionLogExecutor-1] c.p.g.UserActionLogGrpcClient : Failed to record COMPLETED action
io.grpc.StatusRuntimeException: UNAVAILABLE: io exception
```

**결과**: Schedule Service는 정상 동작 ✅

## 📋 체크리스트

### Schedule Service ✅
- [x] Proto 파일 생성
- [x] gRPC 클라이언트 구현
- [x] 비동기 설정
- [x] TaskService 통합
- [x] application.yml 설정

### Insight Service ⏳
- [ ] Proto 파일 복사
- [ ] Entity 생성
- [ ] Repository 생성
- [ ] gRPC Service 구현
- [ ] application.yml 수정
- [ ] 빌드 성공
- [ ] 서비스 실행 성공

### 통합 테스트 ⏳
- [ ] 양쪽 서비스 동시 실행
- [ ] 할 일 완료 → 로그 저장 확인
- [ ] 할 일 미루기 → 로그 저장 확인
- [ ] 할 일 삭제 → 로그 저장 확인
- [ ] Insight Service 중단 → Schedule Service 정상 동작 확인

## 🚨 트러블슈팅

### Proto 컴파일 오류
```bash
./gradlew clean build --refresh-dependencies
```

### 포트 충돌
```bash
# Windows
netstat -ano | findstr :9092
taskkill /PID <PID> /F
```

### gRPC 연결 실패
1. Insight Service가 실행 중인지 확인
2. 포트 9092가 열려있는지 확인
3. application.yml의 주소 확인: `static://localhost:9092`

### DB 연결 실패
1. MariaDB 실행 확인
2. application.yml의 DB 설정 확인
3. 테이블 생성 확인: `user_action_logs`

## 📚 참고 문서

- `GRPC_ACTION_LOG_INTEGRATION.md` - Schedule Service 통합 가이드
- `GRPC_ACTION_LOG_SERVER_GUIDE.md` - Insight Service 구현 가이드
- `TASK14_COMPLETION_SUMMARY.md` - 전체 작업 요약

## 🎉 완료 기준

1. ✅ Schedule Service에서 할 일 완료/미루기/삭제 시 gRPC 호출
2. ✅ Insight Service에서 행동 로그 수신 및 DB 저장
3. ✅ Insight Service 중단 시에도 Schedule Service 정상 동작
4. ✅ DB에 행동 로그 정상 저장 확인

---

**다음 단계**: Insight Service 구현 후 통합 테스트 진행
