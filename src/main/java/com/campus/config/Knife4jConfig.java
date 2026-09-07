package com.campus.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("学生活动打卡与积分兑换管理系统 - 接口文档")
                        .version("1.0.0")
                        .description("基于 Spring Boot 3 与 Knife4j 构建的完整后端 API 规范")
                        .contact(new Contact().name("实训项目研发组")));
    }
}
