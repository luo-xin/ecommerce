package com.ecommerce.common;

import lombok.Data;

@Data
public class Result<T> {
    private Integer code;
    private String msg;
    private T data;

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.code = 0; r.msg = "success"; r.data = data;
        return r;
    }

    public static <T> Result<T> success() { return success(null); }

    public static <T> Result<T> error(int code, String msg) {
        Result<T> r = new Result<>();
        r.code = code; r.msg = msg; r.data = null;
        return r;
    }
}
