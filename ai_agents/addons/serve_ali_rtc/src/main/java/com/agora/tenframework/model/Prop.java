package com.agora.tenframework.model;

/**
 * 属性映射模型类
 *
 * 该类用于定义HTTP请求参数到property.json配置的映射关系，包括：
 *
 * 核心功能：
 * 1. 扩展模块名称映射 - 指定参数映射到哪个扩展模块
 * 2. 属性名称映射 - 指定参数映射到扩展模块的哪个属性
 * 3. 配置映射管理 - 统一管理请求参数到配置的映射关系
 *
 * 使用场景：
 * - 在Constants.START_PROP_MAP中定义映射关系
 * - 将HTTP请求中的channelName映射到RTC/RTM的channel属性
 * - 将HTTP请求中的token映射到RTC/RTM的token属性
 *
 * 与Go版本的对应关系：
 * - 对应Go版本的Prop结构体
 * - 保持相同的字段名称和映射逻辑
 *
 * @author Agora IO
 * @version 1.0.0
 */
public class Prop {
    /**
     * 扩展模块名称
     * 指定参数映射到哪个扩展模块（如agora_rtc、agora_rtm等）
     */
    private String extensionName;

    /**
     * 属性名称
     * 指定参数映射到扩展模块的哪个属性（如channel、token等）
     */
    private String property;

    /**
     * 构造函数
     *
     * @param extensionName 扩展模块名称
     * @param property      属性名称
     */
    public Prop(String extensionName, String property) {
        this.extensionName = extensionName;
        this.property = property;
    }

    /**
     * 默认构造函数
     */
    public Prop() {
    }

    // ==================== Getter和Setter方法 ====================
    public String getExtensionName() {
        return extensionName;
    }

    public void setExtensionName(String extensionName) {
        this.extensionName = extensionName;
    }

    public String getProperty() {
        return property;
    }

    public void setProperty(String property) {
        this.property = property;
    }
}