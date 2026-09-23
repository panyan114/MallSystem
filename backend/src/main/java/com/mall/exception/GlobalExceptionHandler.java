package com.mall.exception;

import com.mall.exception.ErrorCode;
import com.mall.vo.Result;
import lombok.extern.slf4j.Slf4j;
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

    @ExceptionHandler(Exception.class)
    public Result handleException(Exception e) {
        log.error("Unhandled request exception", e);
        return Result.error(ErrorCode.INTERNAL_ERROR.getCode(), "服务器内部错误");
    }
}
