package com.planit.grpc;

import com.planit.grpc.user.CheckFriendshipRequest;
import com.planit.grpc.user.GetCategoriesRequest;
import com.planit.grpc.user.GetCategoriesResponse;
import com.planit.grpc.user.CategoryInfo;
import com.planit.grpc.user.GetUserNamesRequest;
import com.planit.grpc.user.GetUserNamesResponse;
import com.planit.grpc.user.UserServiceGrpc;

import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * User Service와의 gRPC 통신 클라이언트
 *
 * <p>
 * <b>동작 방식:</b>
 * 
 * <pre>
 * Schedule Service (gRPC 클라이언트)         User Service (gRPC 서버)
 *         │                                         │
 *         │  CheckFriendshipRequest                 │
 *         │  { userId: "나", friendUserId: "친구" } │
 *         ├────────────────────────────────────────>│
 *         │                                         │  DB 조회
 *         │  CheckFriendshipResponse                │
 *         │  { isFriend: true/false }               │
 *         │<────────────────────────────────────────│
 * </pre>
 *
 * <p>
 * <b>채널 관리:</b>
 * {@code @GrpcClient("user-service")} 어노테이션이 자동으로:
 * - application.yml의 grpc.client.user-service.address 주소로 채널 생성
 * - 연결 풀링/재시도 등 라이프사이클 관리
 *
 * <p>
 * <b>proto 파일 위치:</b> {@code src/main/proto/user_service.proto}
 * <br>
 * 빌드 시 Gradle protobuf 플러그인이 아래 파일들을 자동 생성:
 * <ul>
 * <li>{@code UserServiceGrpc} - 클라이언트/서버 stub</li>
 * <li>{@code CheckFriendshipRequest} - 요청 메시지</li>
 * <li>{@code CheckFriendshipResponse} - 응답 메시지</li>
 * </ul>
 *
 * <p>
 * <b>User Service 미연동 시:</b>
 * {@code grpc.stub-mode=true} 환경변수 설정 시 항상 true 반환 (개발용)
 */
@Component
public class UserServiceGrpcClient {

    /**
     * grpc-spring-boot-starter가 application.yml의 설정을 읽어 자동으로 채널 주입
     * application.yml: grpc.client.user-service.address
     */
    @GrpcClient("user-service")
    private UserServiceGrpc.UserServiceBlockingStub userServiceStub;

    /**
     * User Service gRPC 연동 여부 (기본값: false = stub 모드)
     * User Service 준비되면 환경변수 GRPC_STUB_MODE=false 로 설정
     */
    @Value("${grpc.stub-mode:true}")
    private boolean stubMode;

    /**
     * 두 사용자가 친구 관계인지 확인합니다.
     *
     * @param myUserId     요청자 userId
     * @param friendUserId 대상 userId
     * @return 친구 관계이면 true
     */
    public boolean checkFriendship(String myUserId, String friendUserId) {
        if (stubMode) {
            // 개발용 stub: User Service 연동 전까지 항상 친구로 처리
            // 실제 연동 시 application.yml에서 grpc.stub-mode: false 설정
            return true;
        }

        // User Service gRPC 실제 호출
        // proto 컴파일 후 build/generated/source/proto/main/grpc/ 에 생성된 stub 사용
        CheckFriendshipRequest request = CheckFriendshipRequest.newBuilder()
                .setUserId(myUserId)
                .setFriendUserId(friendUserId)
                .build();

        return userServiceStub.checkFriendship(request).getIsFriend();
    }

    /**
     * 여러 사용자의 닉네임을 조회합니다.
     *
     * @param userIds 조회할 userId 목록
     * @return userId -> nickname 맵
     */
    public Map<String, String> getUserNames(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        if (stubMode) {
            // 개발용 stub: userId를 닉네임처럼 반환 (User-00000...)
            return userIds.stream()
                    .collect(Collectors.toMap(
                        id -> id, 
                        id -> id.length() > 6 ? "User-" + id.substring(0, 5) : "User-" + id
                    ));
        }

        try {
            GetUserNamesRequest request = GetUserNamesRequest.newBuilder()
                    .addAllUserIds(userIds)
                    .build();

            GetUserNamesResponse response = userServiceStub.getUserNames(request);
            return response.getUserNamesMap();
        } catch (Exception e) {
            System.err.println("[UserServiceGrpcClient] gRPC 호출 오류: " + e.getMessage());
            throw e; // EmojiService에서 잡아서 처리하도록 던짐
        }
    }

    /**
     * User Service에서 전체 카테고리 목록을 조회합니다.
     * CategorySyncService에서 동기화 시 호출됩니다.
     *
     * @return 카테고리 정보 목록
     */
    public List<CategoryInfo> getCategories() {
        try {
            GetCategoriesRequest request = GetCategoriesRequest.newBuilder().build();
            GetCategoriesResponse response = userServiceStub.getCategories(request);
            return response.getCategoriesList();
        } catch (Exception e) {
            System.err.println("[UserServiceGrpcClient] 카테고리 조회 gRPC 오류: " + e.getMessage());
            throw e;
        }
    }
}
