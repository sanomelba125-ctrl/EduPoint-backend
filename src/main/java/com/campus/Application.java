package com.campus;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.campus.mapper")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
        System.out.println("==================================================================");
        System.out.println("学生活动打卡与积分兑换管理系统后端服务启动成功!");
        System.out.println("API 文档地址 (Knife4j): http://localhost:8080/api/doc.html");
        System.out.println("健康探针接口:           http://localhost:8080/api/health");
        System.out.println("==================================================================");
    }
}
