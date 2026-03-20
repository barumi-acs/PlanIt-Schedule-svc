# =========================
# 1️⃣ Build Stage
# =========================
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /app

# gradle wrapper & 설정 복사
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# 소스 복사
COPY src src

# 실행 권한 부여
RUN chmod +x gradlew

# 빌드 (테스트 제외)
RUN ./gradlew clean bootJar -x test --no-daemon

# =========================
# 2️⃣ Runtime Stage
# =========================
FROM eclipse-temurin:17-jre

WORKDIR /app

# 타임존 설정 (로그 시간 맞추기)
RUN ln -snf /usr/share/zoneinfo/Asia/Seoul /etc/localtime && \
    echo "Asia/Seoul" > /etc/timezone

# curl 설치 (디버깅용)
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# non-root user 생성 (보안)
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# jar 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# 포트 (HTTP + gRPC)
EXPOSE 8083 9093

# 헬스체크 (Spring Actuator 기준)
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# 실행
ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", \
  "app.jar"]