package com.tenframework.core.util;

import lombok.extern.slf4j.Slf4j;

/**
 * JsonUtils是一个辅助工具类，用于处理JSON相关的操作。
 */
@Slf4j
public class JsonUtils {

    private JsonUtils() {
        // 私有构造函数，防止实例化
    }

    /**
     * 将点分路径转换为JSON Pointer路径。
     * 例如："properties.user.name" -> "/properties/user/name"
     * @param dotPath 点分路径字符串
     * @return JSON Pointer路径字符串
     */
    public static String convertDotPathToJsonPointer(String dotPath) {
        if (dotPath == null || dotPath.isEmpty()) {
            return "/"; // 根路径
        }
        // JSON Pointer中的特殊字符需要转义：/~ -> ~0, / -> ~1
        // 这里我们假设点路径不包含这些特殊字符，或者用户会确保其有效性。
        // 简单地将点替换为斜杠
        return "/" + dotPath.replace(".", "/");
    }
}