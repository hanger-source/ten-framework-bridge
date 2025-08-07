package com.tenframework.core.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

/**
 * 位置定位系统 - 标识消息在TEN框架中的精确位置
 * 对应C语言中的ten_loc_t结构
 *
 * @param appUri        应用URI，标识应用实例
 * @param graphId       图ID，标识会话图实例，必须是UUID4字符串
 * @param extensionName 扩展名称，标识具体的Extension实例
 */
@Builder // 为 record 添加 @Builder
public record Location(
        @JsonProperty("app_uri") String appUri,
        @JsonProperty("graph_id") String graphId,
        @JsonProperty("extension_name") String extensionName) {

    /**
     * 创建Location实例，进行基本验证
     */
    @JsonCreator
    public Location {
        if (appUri == null || appUri.trim().isEmpty()) {
            throw new IllegalArgumentException("appUri 不能为空");
        }

        if (graphId == null || graphId.trim().isEmpty()) {
            throw new IllegalArgumentException("graphId 不能为空，除非appUri是系统应用");
        }
        if (extensionName == null || extensionName.trim().isEmpty()) {
            throw new IllegalArgumentException("extensionName 不能为空，除非appUri是系统应用");
        }
    }

    /**
     * 创建一个新的Location，替换extensionName
     */
    public Location withExtensionName(String newExtensionName) {
        return new Location(this.appUri, this.graphId, newExtensionName);
    }

    /**
     * 创建一个新的Location，替换graphId
     */
    public Location withGraphId(String newGraphId) {
        return new Location(this.appUri, newGraphId, this.extensionName);
    }

    /**
     * 检查是否是同一个图中的位置（相同的appUri和graphId）
     */
    public boolean isInSameGraph(Location other) {
        if (other == null)
            return false;
        return this.appUri.equals(other.appUri) && this.graphId.equals(other.graphId);
    }

    /**
     * 获取图级别的标识符（不包含extensionName）
     */
    @JsonIgnore
    public String getGraphScope() {
        return appUri + "/" + graphId;
    }

    /**
     * 格式化为字符串表示
     */
    @Override
    public String toString() {
        return String.format("%s/%s/%s", appUri, graphId, extensionName);
    }

    /**
     * 从字符串解析Location
     * 格式: app_uri/graph_id/extension_name
     */
    public static Location fromString(String locationStr) {
        if (locationStr == null || locationStr.trim().isEmpty()) {
            throw new IllegalArgumentException("location字符串不能为空");
        }

        // 处理URI格式，例如 app://test/graph-123/extension1
        // 需要正确分割app_uri（包含://）、graph_id和extension_name
        int firstSlash = locationStr.indexOf('/', locationStr.indexOf("://") + 3);
        if (firstSlash == -1) {
            throw new IllegalArgumentException(
                    "location字符串格式错误，应该是: app_uri/graph_id/extension_name，实际: " + locationStr);
        }

        int secondSlash = locationStr.indexOf('/', firstSlash + 1);
        if (secondSlash == -1) {
            throw new IllegalArgumentException(
                    "location字符串格式错误，应该是: app_uri/graph_id/extension_name，实际: " + locationStr);
        }

        String appUri = locationStr.substring(0, firstSlash);
        String graphId = locationStr.substring(firstSlash + 1, secondSlash);
        String extensionName = locationStr.substring(secondSlash + 1);

        return new Location(appUri, graphId, extensionName);
    }
}