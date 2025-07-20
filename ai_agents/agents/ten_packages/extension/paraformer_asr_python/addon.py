from ten_runtime import (
    Addon,
    register_addon_as_extension,
    TenEnv,
)


@register_addon_as_extension("paraformer_asr_python")
class ParaformerASRExtensionAddon(Addon):
    def on_create_instance(self, ten: TenEnv, addon_name: str, context) -> None:
        from .extension import ParaformerASRExtension

        ten.log_info("on_create_instance")
        ten.on_create_instance_done(ParaformerASRExtension(addon_name), context)
