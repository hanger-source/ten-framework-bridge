package com.tenframework.core.command;

import lombok.Getter;

/**
 * 定义Engine支持的内部命令类型。
 */
@Getter
public enum GraphEventCommandType {
    START_GRAPH("__start_graph__"),
    STOP_GRAPH("__stop_graph__"),
    ADD_EXTENSION_TO_GRAPH("__add_extension_to_graph__"),
    REMOVE_EXTENSION_FROM_GRAPH("__remove_extension_from_graph__");

    private final String commandName;

    GraphEventCommandType(String commandName) {
        this.commandName = commandName;
    }

    public static boolean isInternal(String name) {
        return name.startsWith("__") && name.endsWith("__");
    }

    /**
     * 根据命令名称获取对应的InternalCommandType枚举。
     *
     * @param commandName 命令名称
     * @return 对应的InternalCommandType，如果未找到则返回null
     */
    public static GraphEventCommandType fromCommandName(String commandName) {
        for (GraphEventCommandType type : values()) {
            if (type.getCommandName().equals(commandName)) {
                return type;
            }
        }
        return null;
    }
}