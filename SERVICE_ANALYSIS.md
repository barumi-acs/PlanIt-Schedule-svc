# PlanIt-Schedule-svc 서비스 분석

## 📋 서비스 개요

**PlanIt-Schedule-svc**는 사용자의 목표(Goals), 할 일(Tasks), 카테고리(Category) 관리를 담당하는 마이크로서비스입니다.

### 기본 정보
- **포트**: 8082 (HTTP), 9091 (gRPC Server)
- **데이터베이스**: MariaDB (`base_db`)
- **프레임워크**: Spring Boot 3.5.11, Java 17
- **아키텍처**: MSA (Microservice Architecture)

---

## 🗂️ 데이터베이스 스키마

### 1. category_list (카테고리 목록)
```sql
CREATE TABLE category_list (
  list_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL UNIQUE,  -- "운동", "업무", "학습" 등
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  deleted_at TIMESTAMP
);
```

**역할**: 카테고리 종류의 마스터 데이터
- 예시: "운동", "업무", "학습", "독서", "취미" 등

### 2. category (사용자별 카테고리)
```sql
CREATE TABLE category (
  category_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  list_id BIGINT NOT NULL,           -- FK → category_list.list_id
  user_id VARCHAR(255) NOT NULL,     -- User Service의 UUID
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  deleted_at TIMESTAMP,
  INDEX idx_user_id (user_id)
);
```

**역할**: 사용자와 카테고리를 연결하는 중간 테이블
- `user_id`: User Service에서 관리하는 사용자 UUID (MSA 독립 DB 구조)
- `list_id`: category_list의 카테고리 종류 참조

### 3. goals (목표)
```sql
CREATE TABLE goals (
  goals_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  category_id BIGINT NOT NULL,       -- FK → category.category_id
  title VARCHAR(255) NOT NULL,       -- 목표 제목
  start_date DATE NOT NULL,          -- 시작일
  end_date DATE NOT NULL,            -- 종료일
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  deleted_at TIMESTAMP
);
```

**역할**: 사용자의 목표 관리
- `category_id`를 통해 카테고리와 연결
- 카테고리 → 사용자 추적 가능

### 4. tasks (할 일)
```sql
CREATE TABLE tasks (
  task_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  goals_id BIGINT,                   -- FK → goals.goals_id (nullable)
  user_id VARCHAR(255) NOT NULL,     -- User Service의 UUID
  title VARCHAR(255) NOT NULL,
  due_date DATE,
  status VARCHAR(50),
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  deleted_at TIMESTAMP
);
```

**역할**: 사용자의 할 일 관리
- `goals_id`: 목표와 연결 (선택사항)
- `user_id`: 직접 사용자 참조

### 5. week_goals (주간 목표)
```sql
CREATE TABLE week_goals (
  week_goal_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id VARCHAR(255) NOT NULL,
  week_number INT NOT NULL,
  year INT NOT NULL,
  title VARCHAR(255) NOT NULL,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  deleted_at TIMESTAMP
);
```

**역할**: 주간 단위 목표 관리

---

## 🔗 데이터 관계도

```
category_list (마스터 데이터)
    ↓ (list_id)
category (사용자별 카테고리)
    ↓ (category_id)
goals (목표)
    ↓ (goals_id)
tasks (할 일)
```

### Topic Name 조회 경로 (Insight Service 요청)

```
goals_id (100)
    ↓
goals 테이블 조회
    ↓ category_id
category 테이블 조회
    ↓ list_id
category_list 테이블 조회
    ↓
name ("운동", "업무", "학습" 등)
```

**SQL 쿼리:**
```sql
SELECT cl.name 
FROM goals g
JOIN category c ON g.category_id = c.category_id
JOIN category_list cl ON c.list_id = cl.list_id
WHERE g.goals_id = ?
```

---

## 🏗️ 프로젝트 구조

```
src/main/java/com/planit/
├── category/
│   ├── CategoryData.java          # category 엔티티
│   ├── CategoryRepository.java
│   └── category_list/
│       └── CategoryList.java      # category_list 엔티티
├── goal/
│   ├── GoalData.java              # goals 엔티티
│   ├── GoalRepository.java
│   ├── GoalService.java
│   ├── GoalController.java
│   └── dto/
├── task/
│   ├── TaskData.java              # tasks 엔티티
│   ├── TaskRepository.java
│   ├── TaskService.java
│   ├── TaskController.java
│   └── dto/
├── weekgoal/
│   ├── WeekGoalData.java          # week_goals 엔티티
│   ├── WeekGoalRepository.java
│   ├── WeekGoalService.java
│   ├── WeekGoalController.java
│   └── dto/
├── grpc/
│   └── UserServiceGrpcClient.java # User Service gRPC 클라이언트
└── global/
    ├── ApiResponse.java           # 공통 응답 포맷
    ├── BaseTimeEntity.java        # 생성일/수정일 자동 관리
    ├── CustomException.java       # 커스텀 예외
    ├── ErrorCode.java             # 에러 코드 정의
    └── GlobalExceptionHandler.java # 전역 예외 처리
```

---

## 🔌 gRPC 설정

### gRPC Server (Schedule Service 자신)
- **포트**: 9091
- **역할**: 다른 서비스(Insight Service)에서 이 서비스를 호출할 때 사용

### gRPC Client (User Service 호출)
- **대상**: User Service (포트 9090)
- **역할**: 사용자 인증 및 검증
- **Stub Mode**: `true` (개발 중, User Service 미연동 시 항상 true 반환)

**application.yml 설정:**
```yaml
grpc:
  server:
    port: 9091  # Schedule Service gRPC 서버
  
  client:
    user-service:
      address: static://localhost:9090
      negotiation-type: plaintext
  
  stub-mode: true  # User Service 미연동 개발용
```

---

## 🚀 Insight Service 통합을 위한 구현 필요 사항

### 1. Proto 파일 추가

`src/main/proto/schedule_service.proto` 생성 필요:

```protobuf
syntax = "proto3";

option java_multiple_files = true;
option java_package = "com.planit.grpc.schedule";
option java_outer_classname = "ScheduleServiceProto";

package schedule;

service ScheduleService {
  rpc GetTopicNameByGoalsId(GetTopicNameRequest) returns (GetTopicNameResponse);
  rpc GetTopicNamesByGoalsIds(GetTopicNamesRequest) returns (GetTopicNamesResponse);
}

message GetTopicNameRequest {
  int64 goals_id = 1;
}

message GetTopicNameResponse {
  int64 goals_id = 1;
  string topic_name = 2;
  bool found = 3;
}

message GetTopicNamesRequest {
  repeated int64 goals_ids = 1;
}

message GetTopicNamesResponse {
  repeated TopicNameMapping mappings = 1;
}

message TopicNameMapping {
  int64 goals_id = 1;
  string topic_name = 2;
}
```

### 2. gRPC Server 구현

`src/main/java/com/planit/grpc/ScheduleServiceGrpcServer.java` 생성:

```java
package com.planit.grpc;

import com.planit.goal.GoalRepository;
import com.planit.grpc.schedule.*;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class ScheduleServiceGrpcServer extends ScheduleServiceGrpc.ScheduleServiceImplBase {
    
    private final GoalRepository goalRepository;
    
    @Override
    public void getTopicNameByGoalsId(GetTopicNameRequest request, 
                                      StreamObserver<GetTopicNameResponse> responseObserver) {
        Long goalsId = request.getGoalsId();
        log.info("gRPC GetTopicNameByGoalsId called: goalsId={}", goalsId);
        
        try {
            // SQL: SELECT cl.name FROM goals g
            //      JOIN category c ON g.category_id = c.category_id
            //      JOIN category_list cl ON c.list_id = cl.list_id
            //      WHERE g.goals_id = ?
            
            String topicName = goalRepository.findTopicNameByGoalsId(goalsId);
            
            GetTopicNameResponse response = GetTopicNameResponse.newBuilder()
                .setGoalsId(goalsId)
                .setTopicName(topicName != null ? topicName : "전체")
                .setFound(topicName != null)
                .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
            log.info("gRPC response: goalsId={}, topicName={}", goalsId, topicName);
            
        } catch (Exception e) {
            log.error("gRPC GetTopicNameByGoalsId failed: goalsId={}", goalsId, e);
            responseObserver.onError(e);
        }
    }
    
    @Override
    public void getTopicNamesByGoalsIds(GetTopicNamesRequest request,
                                        StreamObserver<GetTopicNamesResponse> responseObserver) {
        // 일괄 조회 구현
        // ...
    }
}
```

### 3. Repository 메서드 추가

`GoalRepository.java`에 추가:

```java
@Repository
public interface GoalRepository extends JpaRepository<GoalData, Long> {
    
    @Query("""
        SELECT cl.name 
        FROM GoalData g
        JOIN g.category c
        JOIN c.categoryList cl
        WHERE g.goalsId = :goalsId
        AND g.deletedAt IS NULL
        AND c.deletedAt IS NULL
        AND cl.deletedAt IS NULL
    """)
    String findTopicNameByGoalsId(@Param("goalsId") Long goalsId);
}
```

---

## 🔧 설정 변경 필요 사항

### 1. application.yml 수정

```yaml
server:
  port: 8082

spring:
  datasource:
    url: jdbc:mariadb://localhost:3306/schedule_db  # base_db → schedule_db
    username: root
    password: root

grpc:
  server:
    port: 9091  # Schedule Service gRPC 서버 (Insight가 호출)
  
  client:
    user-service:
      address: static://localhost:9090
      negotiation-type: plaintext
```

### 2. build.gradle 확인

gRPC 의존성이 이미 추가되어 있음:
```gradle
implementation 'net.devh:grpc-spring-boot-starter:2.15.0.RELEASE'
implementation 'io.grpc:grpc-stub:1.58.0'
implementation 'io.grpc:grpc-protobuf:1.58.0'
implementation 'io.grpc:grpc-services:1.58.0'
```

---

## 🧪 테스트 방법

### 1. Proto 컴파일
```bash
cd PlanIt-Schedule-svc
./gradlew generateProto
```

### 2. 서버 실행
```bash
./gradlew bootRun
```

### 3. gRPC 테스트 (grpcurl)
```bash
# 서비스 목록 확인
grpcurl -plaintext localhost:9091 list

# GetTopicNameByGoalsId 호출
grpcurl -plaintext -d '{"goals_id": 100}' \
  localhost:9091 schedule.ScheduleService/GetTopicNameByGoalsId
```

### 4. Insight Service와 통합 테스트
```bash
# Insight Service 실행 (포트 8085)
cd PlanIt-Insight-svc
./gradlew bootRun

# Schedule Service 실행 (포트 8082, gRPC 9091)
cd PlanIt-Schedule-svc
./gradlew bootRun

# 리포트 생성 API 호출
# Insight → Schedule gRPC 호출 → Topic Name 조회
```

---

## 📊 데이터 흐름 예시

### Insight Service가 Growth 리포트 생성 시

```
1. Insight Service (Java)
   └─> user_action_logs에서 가장 성장한 goals_id 추출
   └─> goals_id = 100

2. gRPC 호출 (Insight → Schedule)
   └─> GetTopicNameByGoalsId(goals_id=100)
   
3. Schedule Service (Java)
   └─> SQL 실행:
       SELECT cl.name 
       FROM goals g
       JOIN category c ON g.category_id = c.category_id
       JOIN category_list cl ON c.list_id = cl.list_id
       WHERE g.goals_id = 100
   └─> topic_name = "운동"

4. gRPC 응답
   └─> { goals_id: 100, topic_name: "운동", found: true }

5. Insight Service
   └─> Python AI Service로 전달
   └─> { "topic_name": "운동", "growth_rate": 24 }

6. AI 피드백 생성
   └─> "이전 3개월 보다 운동 분야에서 24% 성장했어요!"
```

---

## 🚨 주의사항

### 1. Soft Delete 적용
모든 엔티티에 `@SQLDelete`와 `@SQLRestriction` 적용됨:
- 삭제 시 `deleted_at`에 타임스탬프 기록
- 조회 시 `deleted_at IS NULL` 자동 필터링

### 2. MSA 독립 DB 구조
- `user_id`는 문자열(UUID)로 관리
- User Service와 외래키 제약 없음
- gRPC로 사용자 검증 (stub-mode로 개발 가능)

### 3. 포트 충돌 주의
- HTTP: 8082 (Schedule Service)
- gRPC: 9091 (Schedule Service Server)
- gRPC: 9090 (User Service Client)

---

## 📝 다음 단계

1. **Proto 파일 생성** (`schedule_service.proto`)
2. **gRPC Server 구현** (`ScheduleServiceGrpcServer.java`)
3. **Repository 메서드 추가** (`findTopicNameByGoalsId`)
4. **Proto 컴파일** (`./gradlew generateProto`)
5. **통합 테스트** (Insight ↔ Schedule gRPC 통신)

---

## 🔗 관련 문서

- Insight Service: `PlanIt-Insight-svc/GRPC_SCHEDULE_SERVICE_INTEGRATION.md`
- Proto 파일: `PlanIt-Insight-svc/src/main/proto/schedule_service.proto`
- gRPC 설정: `application.yml`
