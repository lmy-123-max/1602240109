package com.dental.clinic.common;

/** 业务异常：违反业务规则时抛出（如号源冲突、过敏拦截） */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
