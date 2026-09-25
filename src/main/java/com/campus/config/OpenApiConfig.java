package com.campus.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j & OpenAPI 3 规范全局元信息与安全配置 (P4-3)
 * 接口文档在线访问地址: http://localhost:8080/api/doc.html
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "satoken";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("校园活动打卡与积分兑换系统 API 接口文档")
                        .version("v1.0.0")
                        .description("基于 Spring Boot 3 与微内核架构的校园活动平台后端接口文档。覆盖用户认证、活动报名打卡、积分对账、商城秒杀兑换、荣誉电子证书与大模型智能体问答全链路。")
                        .contact(new Contact().name("研发团队").email("developer@campus.com"))
                        .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .description("Sa-Token 鉴权令牌请求头 (可在登录接口成功后复制填入)")));
    }
}
