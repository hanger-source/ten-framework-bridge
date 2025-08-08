package com.tenframework.core.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 消息在TEN框架中的精确位置标识，等同于C/Python中的ten_loc_t。
 * 包含app_uri、graph_id和extension_name。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true) // 允许链式设置
public class Location {
    @JsonProperty("app_uri")
    private String appUri; // 对应C/Python的app_uri
    @JsonProperty("graph_id")
    private String graphId; // 对应C/Python的graph_id
    @JsonProperty("extension_name")
    private String extensionName; // 对应C/Python的extension_name

    // 便于调试输出
    public String toDebugString() {
        return String.format("%s/%s/%s",
                appUri != null && !appUri.isEmpty() ? appUri : "N/A_App",
                graphId != null && !graphId.isEmpty() ? graphId : "N/A_Graph",
                extensionName != null && !extensionName.isEmpty() ? extensionName : "N/A_Ext");
    }

    public boolean isEmpty() {
        return (appUri == null || appUri.isEmpty()) &&
                (graphId == null || graphId.isEmpty()) &&
                (extensionName == null || extensionName.isEmpty());
    }
}