package com.campus.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.campus.common.result.Result;
import com.campus.common.result.ResultCode;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream().map(FieldError::getDefaultMessage).collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return Result.error(ResultCode.BAD_REQUEST.getCode(), message);
    }
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<?> handleConstraintViolationException(ConstraintViolationException e) { return Result.error(ResultCode.BAD_REQUEST.getCode(), e.getMessage()); }
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
    public Result<?> handleNotPermissionException(NotPermissionException e) { return Result.error(ResultCode.FORBIDDEN.getCode(), "无权执行该操作"); }
    @ExceptionHandler(NotRoleException.class)
    public Result<?> handleNotRoleException(NotRoleException e) { return Result.error(ResultCode.FORBIDDEN.getCode(), "无权访问此功能模块：角色权限不足"); }
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public Result<?> handleMethodNotSupported(org.springframework.web.HttpRequestMethodNotSupportedException e) { return Result.error(ResultCode.BAD_REQUEST.getCode(), "请求的HTTP动词 [" + e.getMethod() + "] 不支持"); }
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public Result<?> handleNoResourceFound(org.springframework.web.servlet.resource.NoResourceFoundException e) { return Result.error(ResultCode.NOT_FOUND.getCode(), "请求的RESTful资源路径不存在 /" + e.getResourcePath()); }
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("全局未捕获异常 [{}]: ", e.getClass().getName(), e);
        return Result.error(ResultCode.ERROR.getCode(), "系统繁忙，请稍后重试: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
    }
}

