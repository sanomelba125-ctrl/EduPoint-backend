package com.campus.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.campus.common.result.Result;
import com.campus.common.result.ResultCode;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局统一异常处理器
 * 统一拦截 Controller 层及框架层抛出的各类异常，转换为规范的 Result 响应结构
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ==========================================
    // 1. 业务自定义异常
    // ==========================================

    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    // ==========================================
    // 2. 参数校验异常 (JSR-303 / Hibernate Validator)
    // ==========================================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return Result.error(ResultCode.BAD_REQUEST.getCode(), message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<?> handleConstraintViolationException(ConstraintViolationException e) {
        log.warn("路径参数/单参数校验失败: {}", e.getMessage());
        return Result.error(ResultCode.BAD_REQUEST.getCode(), e.getMessage());
    }

    // ==========================================
    // 3. Sa-Token 认证与鉴权异常
    // ==========================================

    @ExceptionHandler(NotLoginException.class)
    public Result<?> handleNotLoginException(NotLoginException e) {
        String msg = "账号未登录或登录态已过期，请重新登录";
        if (NotLoginException.KICK_OUT.equals(e.getType())) {
            msg = "账号已被管理员强制下线或已被禁用，请重新登录";
        } else if (NotLoginException.TOKEN_TIMEOUT.equals(e.getType()) || NotLoginException.BE_REPLACED.equals(e.getType())) {
            msg = "登录态已失效或已在其他设备登录，请重新登录";
        }
        log.warn("用户认证会话失效拦截: type={}, msg={}", e.getType(), msg);
        return Result.error(ResultCode.UNAUTHORIZED.getCode(), msg);
    }

    @ExceptionHandler(NotPermissionException.class)
    public Result<?> handleNotPermissionException(NotPermissionException e) {
        log.warn("用户权限不足拦截: permission={}", e.getCode());
        return Result.error(ResultCode.FORBIDDEN.getCode(), "无权执行该操作");
    }

    @ExceptionHandler(NotRoleException.class)
    public Result<?> handleNotRoleException(NotRoleException e) {
        log.warn("用户角色不满足拦截: role={}", e.getRole());
        return Result.error(ResultCode.FORBIDDEN.getCode(), "无权访问此功能模块：角色权限不足");
    }

    // ==========================================
    // 4. Spring MVC Web 请求路由与方法异常
    // ==========================================

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<?> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("HTTP 请求动词不支持: method={}", e.getMethod());
        return Result.error(ResultCode.BAD_REQUEST.getCode(), "请求的HTTP动词 [" + e.getMethod() + "] 不支持");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Result<?> handleNoResourceFound(NoResourceFoundException e) {
        return Result.error(ResultCode.NOT_FOUND.getCode(), "请求的RESTful资源路径不存在 /" + e.getResourcePath());
    }

    // ==========================================
    // 5. 数据持久层异常 (P5-3 异常脱敏防 SQL 泄漏)
    // ==========================================

    @ExceptionHandler(DuplicateKeyException.class)
    public Result<?> handleDuplicateKeyException(DuplicateKeyException e) {
        String traceId = com.campus.common.util.TraceUtil.getOrCreateTraceId();
        log.warn("[TraceId: {}] 数据库唯一约束冲突: {}", traceId, e.getMessage());
        return Result.error(ResultCode.BAD_REQUEST.getCode(), "数据已存在或已被占用，请勿重复提交");
    }

    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public Result<?> handleDataAccessException(org.springframework.dao.DataAccessException e) {
        String traceId = com.campus.common.util.TraceUtil.getOrCreateTraceId();
        log.error("[TraceId: {}] 数据库底层操作异常: ", traceId, e);
        return Result.error(ResultCode.ERROR.getCode(), "数据处理异常，请核对参数后重试 [traceId: " + traceId + "]");
    }

    // ==========================================
    // 6. 系统未捕获未知异常兜底 (P5-3 全局脱敏)
    // ==========================================

    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        String traceId = com.campus.common.util.TraceUtil.getOrCreateTraceId();
        log.error("[TraceId: {}] 全局未捕获异常 [{}]: ", traceId, e.getClass().getName(), e);
        return Result.error(ResultCode.ERROR.getCode(), "系统繁忙，请稍后重试 [traceId: " + traceId + "]");
    }
}
