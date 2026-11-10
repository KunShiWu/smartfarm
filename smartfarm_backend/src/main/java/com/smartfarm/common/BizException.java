package com.smartfarm.common;

/**
 * 业务异常：上游接口报错或参数非法时抛出
 */
public class BizException extends RuntimeException {

    public BizException(String message) {
        super(message);
    }

    public BizException(String message, Throwable cause) {
        super(message, cause);
    }
}
