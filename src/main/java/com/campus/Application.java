package com.campus;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
@MapperScan("com.campus.modules.*.mapper")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
        log.info("==================================================================");
        log.info("学生活动打卡与积分兑换管理系统后端服务启动成功！");
        log.info("服务接口基地址:         http://localhost:8080/api");
        log.info("健康探针接口:           http://localhost:8080/api/actuator/health");
        log.info("==================================================================");
    }
}
