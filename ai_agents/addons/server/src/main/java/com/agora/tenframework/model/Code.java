package com.agora.tenframework.model;

/**
 * Response Code Model
 *
 * @author Agora IO
 * @version 1.0.0
 */
public class Code {
    private int code;
    private String msg;

    public Code(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    // Getters and Setters
    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    // Predefined codes
    public static final Code CODE_OK = new Code(0, "success");
    public static final Code CODE_SUCCESS = new Code(0, "success");
    public static final Code CODE_ERR_PARAMS_INVALID = new Code(1001, "params invalid");
    public static final Code CODE_ERR_CHANNEL_EMPTY = new Code(1002, "channel empty");
    public static final Code CODE_ERR_CHANNEL_EXISTED = new Code(1003, "channel existed");
    public static final Code CODE_ERR_CHANNEL_NOT_EXISTED = new Code(1004, "channel not existed");
    public static final Code CODE_ERR_WORKERS_LIMIT = new Code(1005, "workers limit");
    public static final Code CODE_ERR_PROCESS_PROPERTY_FAILED = new Code(1006, "process property failed");
    public static final Code CODE_ERR_START_WORKER_FAILED = new Code(1007, "start worker failed");
    public static final Code CODE_ERR_STOP_WORKER_FAILED = new Code(1008, "stop worker failed");
    public static final Code CODE_ERR_GENERATE_TOKEN_FAILED = new Code(1009, "generate token failed");
    public static final Code CODE_ERR_UPDATE_WORKER_FAILED = new Code(1010, "update worker failed");
    public static final Code CODE_ERR_SAVE_FILE_FAILED = new Code(1011, "save file failed");
    public static final Code CODE_ERR_READ_FILE_FAILED = new Code(1012, "read file failed");
    public static final Code CODE_ERR_PARSE_JSON_FAILED = new Code(1013, "parse json failed");
    public static final Code CODE_ERR_READ_DIRECTORY_FAILED = new Code(1014, "read directory failed");
}