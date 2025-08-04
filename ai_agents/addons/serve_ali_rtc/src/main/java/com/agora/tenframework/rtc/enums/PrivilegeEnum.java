package com.agora.tenframework.rtc.enums;

public enum PrivilegeEnum {
    ENABLE_PRIVILEGE(0x1),
    ENABLE_AUDIO_PUBLISH(0x2),
    ENABLE_VIDEO_PUBLISH(0x4),
    ENABLE_SCREEN_PUBLISH(0x8),
    ;

    private final int privilege;

    PrivilegeEnum(int privilege) {
        this.privilege = privilege;
    }

    public int getPrivilege() {
        return privilege;
    }
}
