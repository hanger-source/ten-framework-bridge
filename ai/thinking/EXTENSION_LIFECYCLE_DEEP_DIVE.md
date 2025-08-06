# Extension 生命周期深度剖析 (C 语言实现)

本文档深入探究 `ten-framework` 中 `Extension` 组件的五阶段生命周期在 C 语言层面的实现细节，补充了之前思考文档中未详细阐述的底层机制。

## 1. Extension 生命周期钩子概述

在 `core/src/ten_runtime/extension/extension.c` 文件中，`ten_extension_create` 函数明确接收了所有生命周期钩子的函数指针，这些函数指针被存储在 `ten_extension_t` 结构体中。当 `Extension` 进入不同的生命周期阶段时，框架会通过这些函数指针调用相应的回调函数。

核心函数指针定义：
```c
// core/src/ten_runtime/extension/extension.c
ten_extension_t *ten_extension_create(
    const char *name, ten_extension_on_configure_func_t on_configure,
    ten_extension_on_init_func_t on_init,
    ten_extension_on_start_func_t on_start,
    ten_extension_on_stop_func_t on_stop,
    ten_extension_on_deinit_func_t on_deinit,
    ten_extension_on_cmd_func_t on_cmd, ten_extension_on_data_func_t on_data,
    ten_extension_on_audio_frame_func_t on_audio_frame,
    ten_extension_on_video_frame_func_t on_video_frame,
    TEN_UNUSED void *user_data) {
  // ...
  self->on_configure = on_configure;
  self->on_init = on_init;
  self->on_start = on_start;
  self->on_stop = on_stop;
  self->on_deinit = on_deinit;
  // ...
}
```

这些函数指针的调用点，通常会伴随着 `Extension` 状态机 (`self->state`) 的转换。

## 2. 五阶段生命周期详解

### 2.1 `on_configure` (配置阶段)

*   **职责**: 用于读取和验证 `Extension` 的配置 (`manifest.json` 和 `property.json`)。
*   **触发点**: `ten_extension_load_metadata` 函数会调用 `ten_extension_on_configure`。
*   **实现细节**:
    *   在 `ten_extension_on_configure` (`609:651:core/src/ten_runtime/extension/extension.c`) 函数中，`self->manifest_info` 和 `self->property_info` 通过 `ten_metadata_info_create` 创建并与 `ten_env` 相关联，用于加载配置元数据。这与 `EXTENSION_LIFECYCLE_AND_CONFIG.md` 中提到的配置自动注入机制相吻合。
    *   状态机转换：`self->state` 变为 `TEN_EXTENSION_STATE_ON_CONFIGURE`。
    *   如果未提供自定义 `on_configure` 回调，框架会自动调用 `ten_extension_on_configure_done` 推进生命周期。
    *   包含执行时间超时警告机制，用于监控 `on_configure` 的性能。

### 2.2 `on_init` (初始化阶段)

*   **职责**: 用于分配 `Extension` 运行所需的各类资源，如内存、线程、文件句柄等。
*   **触发点**: 在 `on_configure` 阶段完成后，通常会触发 `on_init`。
*   **实现细节**:
    *   在 `ten_extension_on_init` (`653:683:core/src/ten_runtime/extension/extension.c`) 函数中被调用。
    *   状态机转换：`self->state` 变为 `TEN_EXTENSION_STATE_ON_INIT`。
    *   与 `on_configure` 类似，如果未提供 `on_init` 回调，将自动调用 `ten_extension_on_init_done`。
    *   同样有执行时间超时警告。

### 2.3 `on_start` (启动阶段)

*   **职责**: `Extension` 的核心工作开始执行。例如，启动内部工作线程、连接到外部服务（如 LLM、ASR 服务）、开始处理数据流等。
*   **触发点**: 在 `on_init` 阶段完成后触发。
*   **实现细节**:
    *   在 `ten_extension_on_start` (`685:715:core/src/ten_runtime/extension/extension.c`) 函数中被调用。
    *   状态机转换：`self->state` 变为 `TEN_EXTENSION_STATE_ON_START`。
    *   若无自定义 `on_start` 回调，将自动调用 `ten_extension_on_start_done`。
    *   有执行时间超时警告。

### 2.4 `on_stop` (停止阶段)

*   **职责**: 优雅地停止 `Extension` 正在进行的工作。这可能包括停止数据处理、关闭网络连接、发送清理消息等。
*   **触发点**:
    *   在 `ten_extension_thread_stop_life_cycle_of_all_extensions` 函数中被触发，用于停止所有 `Extension` 的生命周期。
    *   在 `on_configure_done`、`on_init_done` 或 `on_start_done` 过程中，如果出现错误或需要提前关闭，也可能触发 `on_stop`。
*   **实现细节**:
    *   在 `ten_extension_on_stop` (`717:752:core/src/ten_runtime/extension/extension.c`) 函数中被调用。
    *   状态机转换：`self->state` 变为 `TEN_EXTENSION_STATE_ON_STOP`。
    *   框架会检查 `on_stop` 流程是否已启动，防止重复进入。
    *   若无自定义 `on_stop` 回调，将自动调用 `ten_extension_on_stop_done`。
    *   有执行时间超时警告。

### 2.5 `on_deinit` (清理阶段)

*   **职责**: 释放 `Extension` 在 `on_init` 阶段分配的所有资源，确保没有内存泄漏或资源句柄泄露。这是生命周期的最后阶段。
*   **触发点**: 在 `on_stop` 阶段完成后触发。
*   **实现细节**:
    *   在 `ten_extension_on_deinit` (`754:776:core/src/ten_runtime/extension/extension.c`) 函数中被调用。
    *   状态机转换：`self->state` 变为 `TEN_EXTENSION_STATE_ON_DEINIT`。
    *   若无自定义 `on_deinit` 回调，将自动调用 `ten_extension_on_deinit_done`。
    *   有执行时间超时警告。

## 3. Extension 状态机与错误处理

`Extension` 的生命周期通过严格的状态机 (`TEN_EXTENSION_STATE`) 管理，确保各阶段的顺序性和完整性。在进入 `on_stop` 阶段后，如果 `Extension` 尝试继续执行启动流程中的钩子（如 `on_configure`, `on_init`, `on_start`），框架会记录日志并跳过这些操作，防止状态混乱。

---