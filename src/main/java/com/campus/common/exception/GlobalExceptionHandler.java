package com.campus.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import com.campus.common.result.Result;
import com.campus.common.result.ResultCode;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 捕获自定义业务异�?     */
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        log.warn("业务异常拦截: code={}, message={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 捕获入参校验异常 (@Validated / @Valid)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String errorMsg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验异常: {}", errorMsg);
        return Result.error(ResultCode.BAD_REQUEST.getCode(), errorMsg);
    }

    /**
     * 捕获单个参数校验异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<?> handleConstraintViolationException(ConstraintViolationException e) {
        log.warn("约束校验异常: {}", e.getMessage());
        return Result.error(ResultCode.BAD_REQUEST.getCode(), e.getMessage());
    }

    /**
     * 捕获 Sa-Token 未登录异�?     */
    @ExceptionHandler(NotLoginException.class)
    public Result<?> handleNotLoginException(NotLoginException e) {
        log.warn("未登录或 Token 失效: {}", e.getMessage());
        return Result.error(ResultCode.UNAUTHORIZED.getCode(), "账号未登录或登录态已过期，请重新登录");
    }

    /**
     * 捕获 Sa-Token 无权限异�?     */
    @ExceptionHandler(NotPermissionException.class)
    public Result<?> handleNotPermissionException(NotPermissionException e) {
        log.warn("无操作权�? {}", e.getMessage());
        return Result.error(ResultCode.FORBIDDEN.getCode(), "无权执行该操�?);
    }

    /**
     * 捕获 HTTP 请求动词不支持异�?(�?GET 资源发了 POST)
     */
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public Result<?> handleMethodNotSupported(org.springframework.web.HttpRequestMethodNotSupportedException e) {
        log.warn("不支持的 HTTP 动词: {}", e.getMethod());
        return Result.error(ResultCode.BAD_REQUEST.getCode(), "请求�?HTTP 动词 [" + e.getMethod() + "] 不支�?);
    }

    /**
     * 捕获 RESTful 资源未找到异�?(404)
     */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public Result<?> handleNoResourceFound(org.springframework.web.servlet.resource.NoResourceFoundException e) {
        log.warn("RESTful 资源不存�? {}", e.getResourcePath());
        return Result.error(ResultCode.NOT_FOUND.getCode(), "请求�?RESTful 资源路径不存�? /" + e.getResourcePath());
    }

    /**
     * 兜底未知异常 (500)
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("系统未知异常: ", e);
        return Result.error(ResultCode.ERROR.getCode(), "系统繁忙，请稍后重试: " + e.getMessage());
    }
}
