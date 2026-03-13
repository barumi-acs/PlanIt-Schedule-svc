FROM --platform=linux/amd64 gradle:8.5-jdk17-alpine AS builder
WORKDIR /app

COPY . .

RUN ./gradlew clean build -x test

RUN jlink \
    --add-modules java.base,java.sql,java.naming,java.desktop,java.management,java.security.jgss,java.instrument,jdk.unsupported,java.prefs,java.xml \
    --compress=2 \
    --no-header-files \
    --no-man-pages \
    --output /custom-jre

# ---------------------------------------------------

FROM --platform=linux/amd64 alpine:latest
WORKDIR /app

COPY --from=builder /custom-jre /custom-jre
ENV PATH="/custom-jre/bin:$PATH"

COPY --from=builder /app/build/libs/*-SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]