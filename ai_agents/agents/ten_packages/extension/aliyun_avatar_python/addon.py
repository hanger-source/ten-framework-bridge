#
#
# Agora Real Time Engagement
# Created by XinHui Li in 2024-07.
# Copyright (c) 2024 Agora IO. All rights reserved.
#
#

from ten_runtime import (
    Addon,
    register_addon_as_extension,
    TenEnv,
)


@register_addon_as_extension("aliyun_avatar_python")
class AliyunAvatarExtensionAddon(Addon):
    def on_create_instance(self, ten: TenEnv, addon_name: str, context) -> None:
        ten.log_info("on_create_instance")

        from .extension import AliyunAvatarExtension

        ten.on_create_instance_done(
            AliyunAvatarExtension(addon_name), context
        )
