package com.agora.tenframework.model;

/**
 * 响应码模型类
 *
 * 该类定义了TEN Framework API的统一响应码，包括：
 *
 * 核心功能：
 * 1. 成功响应码 - 表示操作成功
 * 2. 参数错误码 - 表示请求参数无效
 * 3. 频道错误码 - 表示频道相关操作错误
 * 4. Worker错误码 - 表示Worker进程操作错误
 * 5. 文件操作错误码 - 表示文件读写操作错误
 * 6. Token错误码 - 表示Token生成相关错误
 *
 * 错误码设计：
 * - 0: 成功
 * - 1001-1999: 参数和业务逻辑错误
 * - 2000-2999: 系统错误
 *
 * 与Go版本的对应关系：
 * - 对应Go版本的code.go中的错误码定义
 * - 保持相同的错误码编号和消息格式
 *
 * @author Agora IO
 * @version 1.0.0
 */
public class Code {
    /**
     * 错误码
     */
    private int code;

    /**
     * 错误消息
     */
    private String msg;

    /**
     * 构造函数
     *
     * @param code 错误码
     * @param msg  错误消息
     */
    public Code(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    // ==================== Getter和Setter方法 ====================
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

    // ==================== 预定义错误码 ====================
    /**
     * 成功响应码
     */
    public static final Code CODE_OK = new Code(0, "success");

    /**
     * 成功响应码（别名）
     */
    public static final Code CODE_SUCCESS = new Code(0, "success");

    /**
     * 参数无效错误码
     */
    public static final Code CODE_ERR_PARAMS_INVALID = new Code(1001, "params invalid");

    /**
     * 频道为空错误码
     */
    public static final Code CODE_ERR_CHANNEL_EMPTY = new Code(1002, "channel empty");

    /**
     * 频道已存在错误码
     */
    public static final Code CODE_ERR_CHANNEL_EXISTED = new Code(1003, "channel existed");

    /**
     * 频道不存在错误码
     */
    public static final Code CODE_ERR_CHANNEL_NOT_EXISTED = new Code(1004, "channel not existed");

    /**
     * Worker数量超限错误码
     */
    public static final Code CODE_ERR_WORKERS_LIMIT = new Code(1005, "workers limit");

    /**
     * 处理配置文件失败错误码
     */
    public static final Code CODE_ERR_PROCESS_PROPERTY_FAILED = new Code(1006, "process property failed");

    /**
     * 启动Worker失败错误码
     */
    public static final Code CODE_ERR_START_WORKER_FAILED = new Code(1007, "start worker failed");

    /**
     * 停止Worker失败错误码
     */
    public static final Code CODE_ERR_STOP_WORKER_FAILED = new Code(1008, "stop worker failed");

    /**
     * 生成Token失败错误码
     */
    public static final Code CODE_ERR_GENERATE_TOKEN_FAILED = new Code(1009, "generate token failed");

    /**
     * 更新Worker失败错误码
     */
    public static final Code CODE_ERR_UPDATE_WORKER_FAILED = new Code(1010, "update worker failed");

    /**
     * 保存文件失败错误码
     */
    public static final Code CODE_ERR_SAVE_FILE_FAILED = new Code(1011, "save file failed");

    /**
     * 读取文件失败错误码
     */
    public static final Code CODE_ERR_READ_FILE_FAILED = new Code(1012, "read file failed");

    /**
     * 解析JSON失败错误码
     */
    public static final Code CODE_ERR_PARSE_JSON_FAILED = new Code(1013, "parse json failed");

    /**
     * 读取目录失败错误码
     */
    public static final Code CODE_ERR_READ_DIRECTORY_FAILED = new Code(1014, "read directory failed");
}