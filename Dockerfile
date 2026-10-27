# ===================================================================
# 学生活动打卡与积分管理系统 - 后端服务多阶段生产瘦身 Dockerfile
# Stage 1: 构建阶段 (Maven + Eclipse Temurin JDK 17)
# Stage 2: 运行时阶段 (轻量级 Alpine JRE 17，镜像瘦身至 ~150MB)
# ===================================================================

# ----------------- Stage 1: Build -----------------
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /build

# 利用 Docker 层缓存优先下载依赖
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN ./mvnw dependency:go-offline -B || true

# 拷贝源码并进行离线打包
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# ----------------- Stage 2: Runtime -----------------
FROM eclipse-temurin:17-jre-alpine AS runner

WORKDIR /app

# 配置中国时区 (Asia/Shanghai) 并安装轻量 curl 用于健康检查
RUN apk add --no-cache tzdata curl && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

# 创建非 root 生产服务专属运行账号 (安全合规加固)
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# 从 Stage 1 拷贝构建产物
COPY --from=builder /build/target/campus-activity-platform-1.0.0-SNAPSHOT.jar /app/app.jar

# 赋予运行账号权限并切换非 root 用户
RUN chown -R appuser:appgroup /app
USER appuser

EXPOSE 8080

# 生产级 JVM 容器资源感知与优化参数配置
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+ExitOnOutOfMemoryError \
               -Djava.security.egd=file:/dev/./urandom \
               -Dfile.encoding=UTF-8 \
               -Duser.timezone=Asia/Shanghai"

# 容器健康检查 (对接 Spring Boot Actuator；server.servlet.context-path=/api，必须带前缀)
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -fsS http://localhost:8080/api/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
