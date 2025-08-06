# Extension 的生命周期与配置管理

本文档旨在深入剖析 `ten-framework` 中一个 `Extension` 从“出生”到“消亡”的完整生命周期，以及框架是如何将其配置 (`property.json`) 精确注入的。这将揭示框架在资源管理和初始化流程上的设计哲学。

---

## 第一部分：`Extension` 的“出生” —— 一个三阶段的生命周期

`Extension` 的实例化并非一步到位，而是经历了一个精巧的、分阶段的过程：**发现 (Discovery)**、**注册 (Registration)** 和 **实例化 (Instantiation)**。这个过程由 `addon_manager.py` 精心编排。

### 阶段一：发现 (Discovery) - `load_all_addons()`

1.  **触发点**: `_AddonManager.load_all_addons()` 被 `python_addon_loader` 调用。
2.  **寻找应用根目录**: 通过 `_find_app_base_dir()` 向上遍历，找到包含 `manifest.json` (且 `type` 为 `app`) 的应用根目录。
3.  **解析依赖**: 读取 `manifest.json`，获取一个包含所有 `type` 为 `extension` 的依赖名称的列表。
4.  **动态导入**: 框架扫描 `ten_packages/extension/` 目录下的所有子目录。如果一个子目录的名称存在于刚才的依赖列表中，`importlib.import_module()` 会将其作为一个 Python 模块动态导入。

### 阶段二：注册 (Registration) - `@register_addon_as_extension`

这是最关键的魔法发生的地方。当一个 `addon.py` 文件在“发现”阶段被 `import` 时，它顶部的 `@register_addon_as_extension` 装饰器会立即执行。

1.  **创建 `register_handler`**: 装饰器会动态创建一个名为 `register_handler` 的闭包函数。这个函数封装了两个核心动作：
    a. 实例化 `Addon` 类 (e.g., `OpenAIChatGPTExtensionAddon()`)。
    b. 调用底层的 C++ 函数 `_register_addon_as_extension`，将 `Addon` 实例和元数据传递给 C++ 世界。
2.  **存入 Python 注册表**: 刚创建的 `register_handler` 函数，连同 `addon` 的名字，被存入一个全局的 Python 字典 `_AddonManager._registry` 中。
3.  **通知原生层**: `_add_extension_addon_to_addon_manager(name)` 函数被调用，这是一个 C++ 函数，它通知底层的 C++ `addon_manager`：“Python 世界有一个名为 `name` 的 `addon` 已经准备好了，随时可以注册”。

### 阶段三：实例化 (Instantiation) - `Engine` 发出指令

这是生命周期的最后一步，它发生在 `Engine` 解析 `start_graph` 命令并真正需要一个 `Extension` 实例时。

1.  **`Engine` 发出指令**: `Engine` 发现需要一个 `openai_chatgpt_python` 的实例。
2.  **C++ -> Python**: `python_addon_loader` 在 C++ 侧接收到指令，它会找到之前注册的 `addon`，并最终回调到 Python 世界，执行 `_AddonManager` 中对应的 `register_handler` 函数。
3.  **工厂启动**: `register_handler` 被执行，`OpenAIChatGPTExtensionAddon` 这个**工厂**被实例化。
4.  **最终 C++ 注册**: `_register_addon_as_extension` 这个 C++ 函数被调用。它接收到 Python 传来的 `addon_instance` 对象（即那个工厂），并将其**指针**和一个指向 `Addon.on_create_instance` 方法的回调函数，存储在 C++ `addon_manager` 的最终注册表中。

至此，`Engine` 完全知道了：“当我需要 `openai_chatgpt_python` 的一个具体 `Extension` 实例时，我应该调用这个 C++ 函数，它会触发 Python 世界中 `OpenAIChatGPTExtensionAddon` 工厂的 `on_create_instance` 方法，这个方法会创建并返回一个真正的 `OpenAIChatGPTExtension` 业务实例给我。”

这个三阶段的设计，完美地解耦了**模块的加载**、**能力的注册**和**实例的创建**，使得整个过程既灵活又高效。

---

## 第二部分：配置的注入 —— 基于反射的自动填充

`ten-framework` 的配置管理机制，充分利用了 Python 的动态特性，实现了一种优雅的、非侵入式的自动注入模式。

### 核心机制：`BaseConfig` 与反射

1.  **Schema 定义 (`@dataclass`)**: `Extension` 开发者通过继承 `ten_ai_base.config.BaseConfig` 并使用 `@dataclass` 装饰器，来**声明**其配置的数据结构 (Schema)。
    ```python
    @dataclass
    class WeatherToolConfig(BaseConfig):
        api_key: str = ""
    ```
2.  **反射遍历 (`fields()`)**: `BaseConfig` 的 `_init_async` 方法是魔法的核心。它使用 `dataclasses.fields()` 这个反射函数，在运行时**遍历**配置子类的所有已声明字段，并获取每个字段的名称 (`field.name`) 和类型 (`field.type`)。
3.  **类型匹配与 API 调用**: `_init_async` 方法根据反射获取的字段类型，**智能地决定**调用 `ten_env` 的哪个 `get_property_...` 方法。例如，`str` 类型对应 `get_property_string()`，`int` 类型对应 `get_property_int()`。
4.  **动态赋值 (`setattr()`)**: 从 `ten_env` 异步获取到配置值后，`setattr(self, field.name, val)` 函数将该值**动态地设置**到配置对象的同名字段上。

### 完整流程

1.  **`Engine` 加载配置**: 在 `start_graph` 阶段，`Engine` 读取与 `Extension` 关联的 `property.json` 文件，并将其内容加载到 `Engine` 内部的一个键值对存储区中。
2.  **`Extension` 请求配置**: 在 `on_start` 钩子中，`Extension` 调用 `await ConfigClass.create_async(ten_env)`。
3.  **自动注入**: `BaseConfig` 的 `_init_async` 方法执行上述的“反射遍历 -> 类型匹配 -> API 调用 -> 动态赋值”的流程。
4.  **完成**: `create_async` 返回一个被 `property.json` 内容完全填充的、类型安全的 `config` 对象。

### 对 Java 设计的启示

这种基于反射的自动注入模式非常强大，我们必须在 Java 中实现一个对等的机制。

1.  **使用注解作为 Schema**: Java 可以定义一个 `@ConfigProperty` 注解来标记配置字段，并指定其名称、默认值等元数据。
    ```java
    public class WeatherToolConfig {
        @ConfigProperty(name = "api_key", required = true)
        private String apiKey;
    }
    ```
2.  **通过反射实现注入器**: 我们可以创建一个 `ConfigInjector` 服务。它的核心方法 `inject(Object config, EngineContext context)` 会：
    a. 使用 Java 反射 (`java.lang.reflect`) 遍历 `config` 对象的所有字段。
    b. 检查字段是否被 `@ConfigProperty` 注解标记。
    c. 从注解中获取 `name` 等信息，调用 `context.getPropertyAsync(name)`。
    d. 使用 `field.set(config, value)` 将获取到的值异步地注入到字段中。

这个机制将使得 Java `Extension` 的配置管理也同样简洁、类型安全且高度自动化。

---

_（此文档将逐步完善）_
