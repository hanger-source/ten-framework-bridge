package com.tenframework.core.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Objects;

@Data
@NoArgsConstructor
@AllArgsConstructor // 为所有字段生成一个构造函数，方便 Jackson 反序列化
@Accessors(chain = true)
public class Location {
    // 对应 C 的 ten_string_t app_uri;
    @JsonProperty("app_uri")
    private String appUri;
    // 对应 C 的 ten_string_t graph_id; (Engine 的唯一 ID，也是 Graph 实例的 ID)
    @JsonProperty("graph_id")
    private String graphId;
    // 对应 C 的 ten_string_t node_id; (Extension/Node 的 ID)
    @JsonProperty("node_id")
    private String nodeId; // 可选，用于精确到 Extension

    // 重新添加的构造函数，为了 Jackson 的兼容性，以及手动控制构造过程
    // 如果有 @NoArgsConstructor 和 @AllArgsConstructor，通常可以简化手动构造函数的维护
    // public Location(String appUri, String graphId, String nodeId) {
    //     this.appUri = appUri;
    //     this.graphId = graphId;
    //     this.nodeId = nodeId;
    // }

    // Getters 和 Setters 会由 Lombok 的 @Data 和 @Accessors(chain=true) 自动生成
    // 但为了明确，如果需要，可以手动添加。

    // 以下是 Lombok 自动生成后，手动保留的 equals, hashCode, toString 方法
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Location location = (Location) o;
        return Objects.equals(appUri, location.appUri) &&
                Objects.equals(graphId, location.graphId) &&
                Objects.equals(nodeId, location.nodeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(appUri, graphId, nodeId);
    }

    @Override
    public String toString() {
        return "Location{appUri='" + appUri + "', graphId='" + graphId + "', nodeId='" + nodeId + "'}";
    }
}