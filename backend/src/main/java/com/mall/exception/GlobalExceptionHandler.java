package com.mall.exception;

import com.mall.exception.ErrorCode;
import com.mall.vo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result handleBusinessException(BusinessException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result handleValidationException(MethodArgumentNotValidException e) {
        StringBuilder msg = new StringBuilder();
        e.getBindingResult().getFieldErrors().forEach(err -> msg.append(err.getField()).append(":").append(err.getDefaultMessage()).append(";"));
        return Result.error(ErrorCode.PARAM_ERROR.getCode(), msg.toString());
    }

    @ExceptionHandler(BindException.class)
    public Result handleBindException(BindException e) {
        StringBuilder msg = new StringBuilder();
        e.getBindingResult().getFieldErrors().forEach(err -> msg.append(err.getField()).append(":").append(err.getDefaultMessage()).append(";"));
        return Result.error(ErrorCode.PARAM_ERROR.getCode(), msg.toString());
    }

    /**
     * 请求体读不出来（JSON 语法错误、编码不是 UTF-8、日期格式对不上等）属于客户端问题，
     * 报 400 而不是 500。没有这个 handler 时会掉进下面的兜底分支，把一个「你发错了」
     * 说成「服务器内部错误」，还陪一条 ERROR 级堆栈。
     *
     * <p>典型触发场景：日期字段传 {@code "2026-01-01 00:00:00"}（空格分隔）而不是
     * ISO 的 {@code "2026-01-01T00:00:00"}。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result handleUnreadableBody(HttpMessageNotReadableException e) {
        log.warn("Malformed request body: {}", e.getMessage());
        return Result.error(ErrorCode.PARAM_ERROR.getCode(), "请求体格式错误");
    }

    @ExceptionHandler(Exception.class)
    public Result handleException(Exception e) {
        log.error("Unhandled request exception", e);
        return Result.error(ErrorCode.INTERNAL_ERROR.getCode(), "服务器内部错误");
    }
}
