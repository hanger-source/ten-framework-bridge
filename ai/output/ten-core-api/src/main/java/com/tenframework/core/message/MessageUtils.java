package com.tenframework.core.message;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 消息系统工具类
 * 使用现代Java特性提供通用的消息处理功能
 */
@UtilityClass
@Slf4j
public class MessageUtils {

    /**
     * TEN框架自定义MsgPack扩展类型，用于封装内部Message对象
     * 对应C语言中的TEN_MSGPACK_EXT_TYPE_MSG
     */
    public static final byte TEN_MSGPACK_EXT_TYPE_MSG = (byte) -1;

    /**
     * 验证字符串字段是否有效（非null且非空）
     */
    public static boolean validateStringField(String field, String fieldName) {
        return Optional.ofNullable(field)
                .filter(Predicate.not(String::isBlank))
                .map(s -> true)
                .orElseGet(() -> {
                    log.warn("{}为空或无效", fieldName);
                    return false;
                });
    }

    /**
     * 验证数值字段是否有效（大于0）
     */
    public static boolean validatePositiveNumber(Number number, String fieldName) {
        return Optional.ofNullable(number)
                .filter(n -> n.doubleValue() > 0)
                .map(n -> true)
                .orElseGet(() -> {
                    log.warn("{}无效: {}", fieldName, number);
                    return false;
                });
    }

    /**
     * 验证数值字段是否有效（大于等于0）
     */
    public static boolean validateNonNegativeNumber(Number number, String fieldName) {
        return Optional.ofNullable(number)
                .filter(n -> n.doubleValue() >= 0)
                .map(n -> true)
                .orElseGet(() -> {
                    log.warn("{}无效: {}", fieldName, number);
                    return false;
                });
    }

    /**
     * 安全的类型转换，返回Optional
     */
    @SuppressWarnings("unchecked")
    public static <T> Optional<T> safeCast(Object value, Class<T> type) {
        return Optional.ofNullable(value)
                .filter(type::isInstance)
                .map(v -> (T) v);
    }

    /**
     * 安全的类型转换，带默认值
     */
    public static <T> T safeCast(Object value, Class<T> type, T defaultValue) {
        return safeCast(value, type).orElse(defaultValue);
    }

    /**
     * 深拷贝值，递归处理Map和List，并尝试克隆Cloneable对象
     */
    @SuppressWarnings("unchecked")
    public static Object deepCopyValue(Object value) {
        if (value == null) {
            return null;
        } else if (value instanceof Map<?, ?> map) {
            return deepCopyMap((Map<String, Object>) map); // Assume String keys for deepCopyMap
        } else if (value instanceof List<?> list) {
            return deepCopyList((List<Object>) list); // Assume Object values for deepCopyList
        } else if (value instanceof Message message) {
            try {
                return message.clone();
            } catch (CloneNotSupportedException e) {
                log.warn("Message对象无法克隆，返回原始引用: {}", e.getMessage());
                return value;
            }
        } else if (value instanceof Cloneable) {
            return tryClone(value).orElse(value);
        } else {
            return value; // 基本类型、String、不可变对象直接返回
        }
    }

    /**
     * 尝试克隆对象，返回Optional
     */
    private static Optional<Object> tryClone(Object cloneable) {
        try {
            // 使用反射调用公共的clone方法
            java.lang.reflect.Method cloneMethod = cloneable.getClass().getMethod("clone");
            // 确保clone方法是public的，否则会抛出IllegalAccessException
            if (!cloneMethod.trySetAccessible()) {
                log.warn("无法访问对象的clone方法，请确保它是public的: {}", cloneable.getClass().getName());
                return Optional.empty();
            }
            return Optional.of(cloneMethod.invoke(cloneable));
        } catch (NoSuchMethodException e) {
            log.debug("对象没有公共的clone方法: {}", cloneable.getClass().getName());
            return Optional.empty(); // 没有公共的clone方法
        } catch (Exception e) {
            log.warn("无法克隆对象: {} - {}", cloneable.getClass().getName(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 深拷贝Map - 使用Stream API
     */
    public static Map<String, Object> deepCopyMap(Map<String, Object> original) {
        return original.entrySet().stream()
                .collect(HashMap::new,
                        (map, entry) -> map.put(entry.getKey(), deepCopyValue(entry.getValue())),
                        HashMap::putAll);
    }

    /**
     * 深拷贝List - 使用Stream API
     */
    public static List<Object> deepCopyList(List<Object> original) {
        return original.stream()
                .map(MessageUtils::deepCopyValue)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    /**
     * 创建不可变的Map副本
     */
    public static <K, V> Map<K, V> immutableCopy(Map<K, V> original) {
        return Optional.ofNullable(original)
                .map(Map::copyOf) // Java 10+的不可变副本
                .orElse(Map.of());
    }

    /**
     * 创建不可变的List副本
     */
    public static <T> List<T> immutableCopy(List<T> original) {
        return Optional.ofNullable(original)
                .map(List::copyOf) // Java 10+的不可变副本
                .orElse(List.of());
    }

    /**
     * 安全地获取Map中的值，带类型转换
     */
    public static <K, V> Optional<V> getTypedValue(Map<K, Object> map, K key, Class<V> type) {
        return Optional.ofNullable(map)
                .map(m -> m.get(key))
                .flatMap(value -> safeCast(value, type));
    }

    /**
     * 安全地获取Map中的值，带类型转换和默认值
     */
    public static <K, V> V getTypedValue(Map<K, Object> map, K key, Class<V> type, V defaultValue) {
        return getTypedValue(map, key, type).orElse(defaultValue);
    }

    /**
     * 检查字符串是否为有效的UUID格式
     */
    public static boolean isValidUUID(String uuid) {
        return Optional.ofNullable(uuid)
                .filter(Predicate.not(String::isBlank))
                .map(s -> {
                    try {
                        UUID.fromString(s);
                        return true;
                    } catch (IllegalArgumentException e) {
                        return false;
                    }
                })
                .orElse(false);
    }

    /**
     * 格式化消息调试信息
     */
    public static String formatDebugInfo(String type, Object... keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("键值对参数必须成对出现");
        }

        StringBuilder sb = new StringBuilder(type).append("[");
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            if (i > 0)
                sb.append(", ");
            sb.append(keyValuePairs[i]).append("=").append(keyValuePairs[i + 1]);
        }
        return sb.append("]").toString();
    }

    /**
     * 批量验证条件，全部通过才返回true
     */
    public static boolean validateAll(Predicate<?>... conditions) {
        return Arrays.stream(conditions)
                .allMatch(condition -> {
                    try {
                        return ((Predicate<Object>) condition).test(null);
                    } catch (Exception e) {
                        return false;
                    }
                });
    }
}