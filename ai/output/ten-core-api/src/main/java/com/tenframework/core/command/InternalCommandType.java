package com.tenframework.core.command;

/**
 * 定义Engine支持的内部命令类型。
 */
public enum InternalCommandType {
    START_GRAPH("start_graph"),
    STOP_GRAPH("stop_graph"),
    ADD_EXTENSION_TO_GRAPH("add_extension_to_graph"),
    REMOVE_EXTENSION_FROM_GRAPH("remove_extension_from_graph");

    private final String commandName;

    InternalCommandType(String commandName) {
        this.commandName = commandName;
    }

    public String getCommandName() {
        return commandName;
    }

    /**
     * 根据命令名称获取对应的InternalCommandType枚举。
     *
     * @param commandName 命令名称
     * @return 对应的InternalCommandType，如果未找到则返回null
     */
    public static InternalCommandType fromCommandName(String commandName) {
        for (InternalCommandType type : values()) {
            if (type.getCommandName().equals(commandName)) {
                return type;
            }
        }
        return null;
    }
}