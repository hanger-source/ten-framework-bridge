package com.tenframework.core.graph;

import com.tenframework.core.extension.EngineExtensionContext;
import com.tenframework.core.extension.Extension;
import com.tenframework.core.extension.ExtensionMetrics;
import com.tenframework.core.engine.MessageSubmitter;
import com.tenframework.core.Location;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import com.tenframework.core.message.Message; // 新增导入
import com.tenframework.core.message.MessageConstants; // 新增导入
import org.mvel2.MVEL;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.integration.impl.MapVariableResolverFactory;
import com.tenframework.core.message.Command; // 新增导入
import com.tenframework.core.message.CommandResult; // 新增导入

/**
 * 表示一个运行时消息处理图的实例。
 * 每个GraphInstance管理自己独立的Extension集合。
 */
@Slf4j
@Getter
public class GraphInstance {
    private final String graphId;
    private final String appUri;
    private final MessageSubmitter messageSubmitter; // 替换为MessageSubmitter接口

    /**
     * 该图实例下的Extension注册表，使用extensionName作为键
     */
    private final Map<String, Extension> extensionRegistry;

    /**
     * 该图实例下的ExtensionContext实例注册表，与Extension一一对应
     */
    private final Map<String, EngineExtensionContext> extensionContextRegistry;

    /**
     * 该图实例下的ExtensionMetrics实例注册表，与Extension一一对应
     */
    private final Map<String, ExtensionMetrics> extensionMetricsRegistry;

    /**
     * 连接路由表，键为源Extension名称，值为该Extension发出的连接列表
     */
    private final Map<String, List<ConnectionConfig>> connectionRoutes;

    public GraphInstance(String graphId, String appUri, MessageSubmitter messageSubmitter, GraphConfig graphConfig) {
        this.graphId = graphId;
        this.appUri = appUri;
        this.messageSubmitter = messageSubmitter;
        this.extensionRegistry = new ConcurrentHashMap<>();
        this.extensionContextRegistry = new ConcurrentHashMap<>();
        this.extensionMetricsRegistry = new ConcurrentHashMap<>();

        // 根据graphConfig构建连接路由表
        this.connectionRoutes = graphConfig.getConnections() != null ? graphConfig.getConnections().stream()
                .collect(Collectors.groupingBy(ConnectionConfig::getSource)) : Collections.emptyMap();

        log.info("GraphInstance创建: graphId={}, appUri={}, 连接数={}", graphId, appUri, connectionRoutes.size());
    }

    /**
     * 注册Extension实例到当前图实例中
     *
     * @param extensionName Extension名称
     * @param extension     Extension实例
     * @param properties    Extension配置属性
     * @return true如果注册成功，false如果Extension名称已存在
     */
    public boolean registerExtension(String extensionName, Extension extension, Map<String, Object> properties) {
        if (extensionName == null || extensionName.isEmpty()) {
            log.warn("Extension名称不能为空: graphId={}", graphId);
            return false;
        }
        if (extension == null) {
            log.warn("Extension实例不能为空: graphId={}, extensionName={}", graphId, extensionName);
            return false;
        }

        if (extensionRegistry.containsKey(extensionName)) {
            log.warn("Extension名称已存在于当前图: graphId={}, extensionName={}", graphId, extensionName);
            return false;
        }

        // 创建ExtensionContext，关联到当前图实例的graphId和appUri
        EngineExtensionContext context = new EngineExtensionContext(extensionName, graphId, appUri, messageSubmitter,
                properties, extension);

        // 创建Extension性能指标
        ExtensionMetrics metrics = new ExtensionMetrics(extensionName);

        // 注册Extension、Context和Metrics
        extensionRegistry.put(extensionName, extension);
        extensionContextRegistry.put(extensionName, context);
        extensionMetricsRegistry.put(extensionName, metrics);

        // 调用Extension生命周期方法
        try {
            // 1. 配置阶段
            long startTime = System.currentTimeMillis();
            extension.onConfigure(context);
            long configureTime = System.currentTimeMillis() - startTime;
            log.debug("Extension配置完成: graphId={}, extensionName={}, 耗时={}ms",
                    graphId, extensionName, configureTime);

            // 配置阶段超时检查（5秒）
            if (configureTime > 5000) {
                log.warn("Extension配置阶段耗时过长: graphId={}, extensionName={}, 耗时={}ms",
                        graphId, extensionName, configureTime);
            }

            // 2. 初始化阶段
            startTime = System.currentTimeMillis();
            extension.onInit(context);
            long initTime = System.currentTimeMillis() - startTime;
            log.debug("Extension初始化完成: graphId={}, extensionName={}, 耗时={}ms",
                    graphId, extensionName, initTime);

            // 初始化阶段超时检查（10秒）
            if (initTime > 10000) {
                log.warn("Extension初始化阶段耗时过长: graphId={}, extensionName={}, 耗时={}ms",
                        graphId, extensionName, initTime);
            }

            // 3. 启动阶段
            startTime = System.currentTimeMillis();
            extension.onStart(context);
            long startTimeElapsed = System.currentTimeMillis() - startTime;
            log.debug("Extension启动完成: graphId={}, extensionName={}, 耗时={}ms",
                    graphId, extensionName, startTimeElapsed);

            // 启动阶段超时检查（15秒）
            if (startTimeElapsed > 15000) {
                log.warn("Extension启动阶段耗时过长: graphId={}, extensionName={}, 耗时={}ms",
                        graphId, extensionName, startTimeElapsed);
            }

        } catch (Exception e) {
            log.error("Extension生命周期调用失败: graphId={}, extensionName={}, 阶段={}",
                    graphId, extensionName, "onConfigure/onInit/onStart", e);
            // 清理已注册的资源
            extensionRegistry.remove(extensionName);
            extensionContextRegistry.remove(extensionName);
            if (context != null) {
                context.close();
            }
            return false;
        }

        log.info("Extension注册成功: graphId={}, extensionName={}", graphId, extensionName);
        return true;
    }

    /**
     * 注销Extension实例
     *
     * @param extensionName Extension名称
     * @return true如果注销成功，false如果Extension不存在
     */
    public boolean unregisterExtension(String extensionName) {
        if (extensionName == null || extensionName.isEmpty()) {
            log.warn("Extension名称不能为空: graphId={}", graphId);
            return false;
        }

        Extension extension = extensionRegistry.remove(extensionName);
        EngineExtensionContext context = extensionContextRegistry.remove(extensionName);
        ExtensionMetrics metrics = extensionMetricsRegistry.remove(extensionName);

        if (extension == null) {
            log.warn("Extension不存在于当前图: graphId={}, extensionName={}", graphId, extensionName);
            return false;
        }

        // 调用Extension生命周期清理方法
        try {
            // 1. 停止阶段
            long startTime = System.currentTimeMillis();
            extension.onStop(context);
            long stopTime = System.currentTimeMillis() - startTime;
            log.debug("Extension停止完成: graphId={}, extensionName={}, 耗时={}ms",
                    graphId, extensionName, stopTime);

            // 停止阶段超时检查（10秒）
            if (stopTime > 10000) {
                log.warn("Extension停止阶段耗时过长: graphId={}, extensionName={}, 耗时={}ms",
                        graphId, extensionName, stopTime);
            }

            // 2. 清理阶段
            startTime = System.currentTimeMillis();
            extension.onDeinit(context);
            long deinitTime = System.currentTimeMillis() - startTime;
            log.debug("Extension清理完成: graphId={}, extensionName={}, 耗时={}ms",
                    graphId, extensionName, deinitTime);

            // 清理阶段超时检查（5秒）
            if (deinitTime > 5000) {
                log.warn("Extension清理阶段耗时过长: graphId={}, extensionName={}, 耗时={}ms",
                        graphId, extensionName, deinitTime);
            }

        } catch (Exception e) {
            log.error("Extension生命周期清理失败: graphId={}, extensionName={}, 阶段={}",
                    graphId, extensionName, "onStop/onDeinit", e);
            // 即使清理失败，也要继续关闭Context
        }

        // 关闭ExtensionContext资源
        if (context != null) {
            context.close();
        }

        log.info("Extension注销成功: graphId={}, extensionName={}", graphId, extensionName);
        return true;
    }

    /**
     * 移除Extension实例 (用于动态移除)
     *
     * @param extensionName Extension名称
     * @return true如果移除成功，false如果Extension不存在
     */
    public boolean removeExtension(String extensionName) {
        if (extensionName == null || extensionName.isEmpty()) {
            log.warn("Extension名称不能为空: graphId={}", graphId);
            return false;
        }

        Extension extension = extensionRegistry.remove(extensionName);
        EngineExtensionContext context = extensionContextRegistry.remove(extensionName);
        ExtensionMetrics metrics = extensionMetricsRegistry.remove(extensionName);

        if (extension == null) {
            log.warn("Extension不存在于当前图，无法移除: graphId={}, extensionName={}", graphId, extensionName);
            return false;
        }

        // 调用Extension生命周期清理方法
        try {
            // 1. 停止阶段
            long startTime = System.currentTimeMillis();
            extension.onStop(context);
            long stopTime = System.currentTimeMillis() - startTime;
            log.debug("Extension停止完成: graphId={}, extensionName={}, 耗时={}ms (移除操作)",
                    graphId, extensionName, stopTime);

            if (stopTime > 10000) {
                log.warn("Extension停止阶段耗时过长(移除操作): graphId={}, extensionName={}, 耗时={}ms",
                        graphId, extensionName, stopTime);
            }

            // 2. 清理阶段
            startTime = System.currentTimeMillis();
            extension.onDeinit(context);
            long deinitTime = System.currentTimeMillis() - startTime;
            log.debug("Extension清理完成: graphId={}, extensionName={}, 耗时={}ms (移除操作)",
                    graphId, extensionName, deinitTime);

            if (deinitTime > 5000) {
                log.warn("Extension清理阶段耗时过长(移除操作): graphId={}, extensionName={}, 耗时={}ms",
                        graphId, extensionName, deinitTime);
            }

        } catch (Exception e) {
            log.error("Extension生命周期清理失败(移除操作): graphId={}, extensionName={}, 阶段={}",
                    graphId, extensionName, "onStop/onDeinit", e);
        } finally {
            // 即使清理失败，也要继续关闭Context
            if (context != null) {
                context.close();
            }
        }

        log.info("Extension成功从图实例中移除: graphId={}, extensionName={}", graphId, extensionName);
        return true;
    }

    /**
     * 获取Extension实例
     *
     * @param extensionName Extension名称
     * @return Extension实例的Optional，如果不存在则为空
     */
    public Optional<Extension> getExtension(String extensionName) {
        return Optional.ofNullable(extensionRegistry.get(extensionName));
    }

    /**
     * 获取ExtensionContext实例
     *
     * @param extensionName Extension名称
     * @return ExtensionContext实例的Optional，如果不存在则为空
     */
    public Optional<EngineExtensionContext> getExtensionContext(String extensionName) {
        return Optional.ofNullable(extensionContextRegistry.get(extensionName));
    }

    /**
     * 清理所有Extension资源（私有方法，在GraphInstance停止时调用）
     */
    public void cleanupAllExtensions() {
        log.info("开始清理GraphInstance下的所有Extension资源: graphId={}, extensionCount={}", graphId, extensionRegistry.size());

        // 关闭所有ExtensionContext
        extensionContextRegistry.values().forEach(context -> {
            try {
                context.close();
            } catch (Exception e) {
                log.error("关闭ExtensionContext时发生异常: extensionName={}", context.getExtensionName(), e);
            }
        });

        // 清空注册表
        extensionRegistry.clear();
        extensionContextRegistry.clear();
        extensionMetricsRegistry.clear();

        log.info("GraphInstance下所有Extension资源清理完成: graphId={}", graphId);
    }

    /**
     * 根据消息的sourceLocation和GraphInstance的connections配置，解析消息的实际目的地。
     * 支持条件路由、基于内容的路由和广播。
     *
     * @param message 要路由的消息
     * @return 实际的目标Extension名称列表
     */
    public List<String> resolveDestinations(Message message) {
        if (message == null || message.getSourceLocation() == null) {
            return Collections.emptyList();
        }

        String sourceExtensionName = message.getSourceLocation().extensionName();
        List<ConnectionConfig> connectionsFromSource = connectionRoutes.get(sourceExtensionName);

        if (connectionsFromSource == null || connectionsFromSource.isEmpty()) {
            return Collections.emptyList(); // 没有定义从该源Extension发出的连接
        }

        List<ConnectionConfig> potentialConnections = new java.util.ArrayList<>();
        Integer messagePriority = message.hasProperty(MessageConstants.PROPERTY_MESSAGE_PRIORITY)
                ? message.getProperty(MessageConstants.PROPERTY_MESSAGE_PRIORITY, Integer.class)
                : 0;

        if (messagePriority != null) {
            String messageIdentifier = null;
            if (message instanceof Command command) {
                messageIdentifier = command.getCommandId();
            } else if (message instanceof CommandResult commandResult) {
                messageIdentifier = commandResult.getCommandId();
            } else {
                messageIdentifier = message.getName(); // 对于Data、AudioFrame等，使用name或自定义逻辑
            }
            log.debug("消息带有优先级属性: messageIdentifier={}, priority={}", messageIdentifier, messagePriority);
        }

        // 1. 筛选出所有潜在的匹配连接
        for (ConnectionConfig connection : connectionsFromSource) {
            // 检查消息优先级是否满足连接的最低优先级要求
            if (messagePriority < connection.getMinPriority()) {
                log.debug(
                        "消息优先级不满足连接要求，跳过连接: graphId={}, source={}, destination={}, messagePriority={}, minPriority={}",
                        graphId, sourceExtensionName, connection.getDestinations(), messagePriority,
                        connection.getMinPriority());
                continue; // 跳过此连接
            }

            if (connection.isBroadcast()) {
                potentialConnections.add(connection); // 广播连接总是被考虑
            } else if (connection.getRoutingRules() != null && !connection.getRoutingRules().isEmpty()) {
                for (RoutingRule rule : connection.getRoutingRules()) {
                    if (evaluateRoutingRule(message, rule)) {
                        potentialConnections.add(connection);
                        break; // 规则匹配，考虑该连接
                    }
                }
            } else if (connection.getCondition() != null && !connection.getCondition().isEmpty()) {
                if (evaluateCondition(message, connection.getCondition())) {
                    potentialConnections.add(connection);
                }
            } else {
                // 没有特殊规则的默认连接
                potentialConnections.add(connection);
            }
        }

        if (potentialConnections.isEmpty()) {
            return Collections.emptyList(); // 没有匹配的连接
        }

        // 2. 根据优先级选择连接 (如果不是广播模式)
        // 如果有广播连接，将所有广播连接的目标都添加到目标列表
        boolean hasBroadcastConnection = potentialConnections.stream().anyMatch(ConnectionConfig::isBroadcast);

        List<String> resolvedTargets = new java.util.ArrayList<>();

        if (hasBroadcastConnection) {
            // 如果存在广播连接，将所有广播连接的目标以及其他匹配连接的目标都添加
            for (ConnectionConfig connection : potentialConnections) {
                resolvedTargets.addAll(connection.getDestinations());
            }
        } else {
            // 如果没有广播连接，则根据优先级选择最高优先级的连接
            Optional<ConnectionConfig> highestPriorityConnection = potentialConnections.stream()
                    .max(java.util.Comparator.comparingInt(ConnectionConfig::getPriority));

            if (highestPriorityConnection.isPresent()) {
                // 如果最高优先级的连接有路由规则且规则匹配，则使用规则的targets，否则使用连接的destinations
                if (highestPriorityConnection.get().getRoutingRules() != null
                        && !highestPriorityConnection.get().getRoutingRules().isEmpty()) {
                    for (RoutingRule rule : highestPriorityConnection.get().getRoutingRules()) {
                        if (evaluateRoutingRule(message, rule)) {
                            if (rule.getTargets() != null && !rule.getTargets().isEmpty()) {
                                resolvedTargets.addAll(rule.getTargets());
                            } else {
                                resolvedTargets.addAll(highestPriorityConnection.get().getDestinations());
                            }
                            break;
                        }
                    }
                } else {
                    resolvedTargets.addAll(highestPriorityConnection.get().getDestinations());
                }
            }
        }

        // 3. 移除消息本身的优先级处理，因为已经在connection筛选中处理
        // TODO: 根据消息优先级进一步调整 resolvedTargets，例如：
        // 如果消息优先级高于某个阈值，可以强制路由到某个“紧急处理”Extension
        // 或者在 resolvedTargets 中移除低优先级Extension

        // 去重并返回
        return resolvedTargets.stream().distinct().collect(Collectors.toList());
    }

    /**
     * 评估路由规则是否匹配消息
     */
    private boolean evaluateRoutingRule(Message message, RoutingRule rule) {
        if (rule == null) {
            log.warn("无效的路由规则: 规则对象为空");
            return false;
        }

        // 1. 消息内容属性匹配
        boolean contentMatch = true;
        if (rule.getPropertyName() != null && !rule.getPropertyName().isEmpty()) {
            // 现有消息内容匹配逻辑
            Object messagePropertyValue = null;
            try {
                if ("name".equals(rule.getPropertyName())) {
                    messagePropertyValue = message.getName();
                } else if ("type".equals(rule.getPropertyName())) {
                    messagePropertyValue = message.getType().toString();
                } else if (rule.getPropertyName().startsWith("properties.")) {
                    String propKey = rule.getPropertyName().substring("properties.".length());
                    if (message.getProperties() != null) {
                        messagePropertyValue = message.getProperties().get(propKey);
                    }
                } else {
                    try {
                        java.lang.reflect.Method getter = message.getClass()
                                .getMethod("get" + capitalize(rule.getPropertyName()));
                        messagePropertyValue = getter.invoke(message);
                    } catch (NoSuchMethodException e) {
                        log.warn("消息中未找到属性的getter方法: {}", rule.getPropertyName());
                    }
                }
            } catch (Exception e) {
                log.error("获取消息属性失败: propertyName={}", rule.getPropertyName(), e);
                return false;
            }

            if (messagePropertyValue == null) {
                contentMatch = false;
            } else {
                String operator = rule.getOperator();
                Object rulePropertyValue = rule.getPropertyValue();

                if (operator == null || operator.isEmpty() || rulePropertyValue == null) {
                    log.warn("路由规则操作符或匹配值为空: operator={}, propertyValue={}", operator, rulePropertyValue);
                    contentMatch = false;
                } else {
                    switch (operator.toLowerCase()) {
                        case "equals":
                            contentMatch = messagePropertyValue.equals(rulePropertyValue);
                            break;
                        case "contains":
                            if (messagePropertyValue instanceof String && rulePropertyValue instanceof String) {
                                contentMatch = ((String) messagePropertyValue).contains((String) rulePropertyValue);
                            } else {
                                contentMatch = false;
                            }
                            break;
                        case "regex":
                            if (messagePropertyValue instanceof String && rulePropertyValue instanceof String) {
                                try {
                                    contentMatch = ((String) messagePropertyValue).matches((String) rulePropertyValue);
                                } catch (java.util.regex.PatternSyntaxException e) {
                                    log.error("无效的正则表达式: {}", rulePropertyValue, e);
                                    contentMatch = false;
                                }
                            } else {
                                contentMatch = false;
                            }
                            break;
                        default:
                            log.warn("不支持的路由规则操作符: {}", operator);
                            contentMatch = false;
                            break;
                    }
                }
            }
        }

        // 2. 消息来源属性匹配
        boolean sourceMatch = true;
        if (rule.getSourcePropertyName() != null && !rule.getSourcePropertyName().isEmpty()) {
            if (message.getSourceLocation() == null) {
                sourceMatch = false;
            } else {
                Object sourcePropertyValue = null;
                switch (rule.getSourcePropertyName().toLowerCase()) {
                    case "source_app_uri":
                        sourcePropertyValue = message.getSourceLocation().appUri();
                        break;
                    case "source_graph_id":
                        sourcePropertyValue = message.getSourceLocation().graphId();
                        break;
                    case "source_extension_name":
                        sourcePropertyValue = message.getSourceLocation().extensionName();
                        break;
                    default:
                        log.warn("不支持的源属性名: {}", rule.getSourcePropertyName());
                        sourceMatch = false;
                        break;
                }

                if (sourcePropertyValue == null) {
                    sourceMatch = false;
                } else {
                    String operator = rule.getSourceOperator();
                    Object ruleSourcePropertyValue = rule.getSourcePropertyValue();

                    if (operator == null || operator.isEmpty() || ruleSourcePropertyValue == null) {
                        log.warn("源路由规则操作符或匹配值为空: operator={}, propertyValue={}", operator, ruleSourcePropertyValue);
                        sourceMatch = false;
                    } else {
                        switch (operator.toLowerCase()) {
                            case "equals":
                                sourceMatch = sourcePropertyValue.equals(ruleSourcePropertyValue);
                                break;
                            case "contains":
                                if (sourcePropertyValue instanceof String
                                        && ruleSourcePropertyValue instanceof String) {
                                    sourceMatch = ((String) sourcePropertyValue)
                                            .contains((String) ruleSourcePropertyValue);
                                } else {
                                    sourceMatch = false;
                                }
                                break;
                            case "regex":
                                if (sourcePropertyValue instanceof String
                                        && ruleSourcePropertyValue instanceof String) {
                                    try {
                                        sourceMatch = ((String) sourcePropertyValue)
                                                .matches((String) ruleSourcePropertyValue);
                                    } catch (java.util.regex.PatternSyntaxException e) {
                                        log.error("无效的正则表达式: {}", ruleSourcePropertyValue, e);
                                        sourceMatch = false;
                                    }
                                } else {
                                    sourceMatch = false;
                                }
                                break;
                            default:
                                log.warn("不支持的源路由规则操作符: {}", operator);
                                sourceMatch = false;
                                break;
                        }
                    }
                }
            }
        }

        // 如果同时定义了内容匹配和来源匹配，则两者都必须为真
        // 如果只定义了其中一种，则以该种的匹配结果为准
        // 如果两者都没有定义，则认为不匹配（这种情况应该在resolveDestinations中处理）
        if ((rule.getPropertyName() != null && !rule.getPropertyName().isEmpty()) &&
                (rule.getSourcePropertyName() != null && !rule.getSourcePropertyName().isEmpty())) {
            return contentMatch && sourceMatch;
        } else if (rule.getPropertyName() != null && !rule.getPropertyName().isEmpty()) {
            return contentMatch;
        } else if (rule.getSourcePropertyName() != null && !rule.getSourcePropertyName().isEmpty()) {
            return sourceMatch;
        } else {
            return false; // 规则既没有内容匹配也没有来源匹配，视为不匹配
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * 评估条件表达式是否为真
     * 使用MVEL表达式引擎
     */
    private boolean evaluateCondition(Message message, String condition) {
        if (condition == null || condition.isEmpty()) {
            return true; // 没有条件，默认通过
        }

        try {
            // 编译表达式
            Object compiledExpression = MVEL.compileExpression(condition);

            // 创建变量解析工厂，将message对象放入上下文
            Map<String, Object> variables = new java.util.HashMap<>();
            variables.put("message", message); // 将整个消息对象作为变量传入

            VariableResolverFactory resolverFactory = new MapVariableResolverFactory(variables);

            // 执行表达式
            Object result = MVEL.executeExpression(compiledExpression, resolverFactory);

            // 检查结果是否为布尔值
            if (result instanceof Boolean) {
                return (Boolean) result;
            } else {
                log.warn("MVEL条件表达式结果不是布尔值: condition={}, result={}", condition, result);
                return false; // 非布尔结果视为不匹配
            }
        } catch (Exception e) {
            String messageId = "N/A";
            if (message instanceof Command) {
                messageId = ((Command) message).getCommandId();
            } else if (message.getName() != null) {
                messageId = message.getName();
            } else {
                messageId = message.getType().name();
            }
            log.error("评估MVEL条件表达式失败: condition={}, 消息ID={}", condition, messageId, e);
            return false;
        }
    }
}