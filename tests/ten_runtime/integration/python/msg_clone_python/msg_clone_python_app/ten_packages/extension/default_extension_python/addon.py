#
# Copyright © 2025 Agora
# This file is part of TEN Framework, an open source project.
# Licensed under the Apache License, Version 2.0, with certain conditions.
# Refer to the "LICENSE" file in the root directory for more information.
#
from ten_runtime import (
    Addon,
    register_addon_as_extension,
    TenEnv,
    LogLevel,
)
from .extension import ServerExtension, ClientExtension


@register_addon_as_extension("default_extension_python")
class DefaultExtensionAddon(Addon):
    def on_create_instance(self, ten_env: TenEnv, name: str, context) -> None:
        ten_env.log(LogLevel.INFO, "on_create_instance" + name)

        if name == "server":
            ten_env.on_create_instance_done(ServerExtension(name), context)
        elif name == "client":
            ten_env.on_create_instance_done(ClientExtension(name), context)
        else:
            assert False
