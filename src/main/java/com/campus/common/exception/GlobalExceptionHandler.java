package com.campus.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import com.campus.common.result.Result;
import com.campus.common.result.ResultCode;
import jakarta.validation.ConstraintViolationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) { return Result.error(e.getCode(), e.getMessage()); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream().map(FieldError::getDefaultMessage).collect(Collectors.joining("; "));
        return Result.error(ResultCode.BAD_REQUEST.getCode(), msg);
    }
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<?> handleConstraintViolationException(ConstraintViolationException e) { return Result.error(ResultCode.BAD_REQUEST.getCode(), e.getMessage()); }
    @ExceptionHandler(NotLoginException.class)
    public Result<?> handleNotLoginException(NotLoginException e) { return Result.error(ResultCode.UNAUTHORIZED.getCode(), "账号未登录或登录态已过期，请重新登录"); }
    @ExceptionHandler(NotPermissionException.class)
    public Result<?> handleNotPermissionException(NotPermissionException e) { return Result.error(ResultCode.FORBIDDEN.getCode(), "无权执行该操作"); }
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public Result<?> handleMethodNotSupported(org.springframework.web.HttpRequestMethodNotSupportedException e) { return Result.error(ResultCode.BAD_REQUEST.getCode(), "请求的HTTP动词 [" + e.getMethod() + "] 不支持"); }
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public Result<?> handleNoResourceFound(org.springframework.web.servlet.resource.NoResourceFoundException e) { return Result.error(ResultCode.NOT_FOUND.getCode(), "请求的RESTful资源路径不存在 /" + e.getResourcePath()); }
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) { return Result.error(ResultCode.ERROR.getCode(), "系统繁忙，请稍后重试: " + e.getMessage()); }
}
