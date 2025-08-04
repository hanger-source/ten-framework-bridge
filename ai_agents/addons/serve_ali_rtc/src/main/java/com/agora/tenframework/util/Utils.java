package com.agora.tenframework.util;

import java.lang.reflect.Field;

/**
 * 工具类
 *
 * 该类提供了TEN Framework中常用的工具方法，包括：
 *
 * 核心功能：
 * 1. 反射工具方法 - 通过反射获取对象字段值
 * 2. 类型转换工具 - 将字段值转换为不同数据类型
 * 3. 安全访问工具 - 安全地访问对象的私有字段
 * 4. 空值处理工具 - 处理空值和类型转换异常
 *
 * 使用场景：
 * - 动态获取请求对象字段值
 * - 配置参数的动态读取
 * - 对象属性的安全访问
 * - 数据类型的自动转换
 *
 * 与Go版本的对应关系：
 * - 对应Go版本的utils包
 * - 使用Java反射替代Go的反射机制
 * - 保持相同的字段访问逻辑
 *
 * @author Agora IO
 * @version 1.0.0
 */
public class Utils {

    /**
     * 通过反射获取对象字段值
     *
     * 使用Java反射机制安全地获取对象的字段值
     * 对应Go版本的getFieldValue函数
     *
     * @param obj       目标对象
     * @param fieldName 字段名称
     * @return 字段值，如果获取失败则返回null
     */
    public static Object getFieldValue(Object obj, String fieldName) {
        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(obj);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取字段值并转换为字符串
     *
     * @param obj       目标对象
     * @param fieldName 字段名称
     * @return 字段值的字符串表示，如果获取失败则返回空字符串
     */
    public static String getFieldValueAsString(Object obj, String fieldName) {
        Object value = getFieldValue(obj, fieldName);
        return value != null ? value.toString() : "";
    }

    /**
     * 获取字段值并转换为整数
     *
     * @param obj       目标对象
     * @param fieldName 字段名称
     * @return 字段值的整数表示，如果获取失败或类型不匹配则返回null
     */
    public static Integer getFieldValueAsInteger(Object obj, String fieldName) {
        Object value = getFieldValue(obj, fieldName);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }

    /**
     * 获取字段值并转换为长整数
     *
     * @param obj       目标对象
     * @param fieldName 字段名称
     * @return 字段值的长整数表示，如果获取失败或类型不匹配则返回null
     */
    public static Long getFieldValueAsLong(Object obj, String fieldName) {
        Object value = getFieldValue(obj, fieldName);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }
}