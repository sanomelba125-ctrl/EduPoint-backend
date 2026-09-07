# ===================================================================
# 学生活动打卡与积分兑换系统 - 后端服务 Dockerfile
# 基础镜像: 轻量级 Alpine Linux + Eclipse Temurin JRE 17
# ===================================================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 配置上海时区
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

# 拷贝 Spring Boot 可执行 Fat-JAR
ARG JAR_FILE=target/campus-activity-platform-1.0.0-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar

# 暴露应用端口
EXPOSE 8080

# 容器启动命令
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-Dfile.encoding=UTF-8", "-jar", "app.jar"]
