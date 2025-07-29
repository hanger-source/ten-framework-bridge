from ten_runtime import (
    Extension,
    TenEnv,
    Cmd,
    Data,
    StatusCode,
    CmdResult,
)

from alibabacloud_avatar20220130.client import Client
from alibabacloud_tea_openapi import models as open_api_models
from alibabacloud_avatar20220130 import models as avatar_models
import uuid
import atexit
import json

# 配置参数（请根据实际情况填写）
ACCESS_KEY_ID = "<ACCESS_KEY_ID>"
ACCESS_KEY_SECRET = "<ACCESS_KEY_SECRET>"
ENDPOINT = "avatar.cn-zhangjiakou.aliyuncs.com"
TENANT_ID = 30651
APP_ID = "<APP_ID>"
USER_ID = "avatar_sample_userId"
USER_NAME = "avatar_sample_userName"

CMD_NAME_FLUSH = "flush"

TEXT_DATA_TEXT_FIELD = "text"
TEXT_DATA_FINAL_FIELD = "is_final"


class AliyunAvatarExtension(Extension):

    client: Client
    session_id: str

    def __init__(self, name: str):
        super().__init__(name)
        # 1. 初始化
        self.config = open_api_models.Config(
            access_key_id=ACCESS_KEY_ID,
            access_key_secret=ACCESS_KEY_SECRET,
            endpoint=ENDPOINT
        )
        self.client = Client(self.config)

    def on_start(self, ten: TenEnv) -> None:
        ten.log_info("on_start")
        ten.on_start_done()
        start_req = avatar_models.StartInstanceRequest(
            tenant_id=TENANT_ID,
            app=avatar_models.StartInstanceRequestApp(app_id=APP_ID),
            user=avatar_models.StartInstanceRequestUser(user_id=USER_ID, user_name=USER_NAME)
        )
        start_resp = self.client.start_instance(start_req)
        ten.log_info(f"aliyun_avatar_python start_resp (美化):\n {json.dumps(start_resp.to_map(), ensure_ascii=False, indent=2)}")
        self.session_id = str(start_resp.body.data.session_id)

    def on_stop(self, ten: TenEnv) -> None:
        ten.log_info(" aliyun_avatar_python on_stop")
        ten.on_stop_done()
        stop_req = avatar_models.StopInstanceRequest(
            tenant_id=TENANT_ID,
            session_id=self.session_id
        )
        try:
            self.client.stop_instance(stop_req)
            ten.log_info("aliyun_avatar_python 服务已正常结束。")
        except Exception as e:
            ten.log_error(f"aliyun_avatar_python 结束服务时发生异常: {e}")

    def send_flush_cmd(self, ten: TenEnv) -> None:
        flush_cmd = Cmd.create(CMD_NAME_FLUSH)
        ten.send_cmd(
            flush_cmd,
            lambda ten, result, _: ten.log_info("send_cmd done"),
        )

        ten.log_info(f"sent cmd: {CMD_NAME_FLUSH}")

    def on_cmd(self, ten: TenEnv, cmd: Cmd) -> None:
        cmd_name = cmd.get_name()
        ten.log_info("aliyun_avatar_python on_cmd name {}".format(cmd_name))

        # flush whatever cmd incoming at the moment
        self.send_flush_cmd(ten)

        # then forward the cmd to downstream
        cmd_json, _ = cmd.get_property_to_json()
        new_cmd = Cmd.create(cmd_name)
        new_cmd.set_property_from_json(None, cmd_json)
        ten.send_cmd(
            new_cmd,
            lambda ten, result, _: ten.log_info("send_cmd done"),
        )

        cmd_result = CmdResult.create(StatusCode.OK, cmd)
        ten.return_result(cmd_result)

    def on_data(self, ten: TenEnv, data: Data) -> None:
        ten.log_info("aliyun_avatar_python on_data")

        try:
            text, _ = data.get_property_string(TEXT_DATA_TEXT_FIELD)
            if text:
                send_req = avatar_models.SendMessageRequest(
                    tenant_id=TENANT_ID,
                    session_id=self.session_id,
                    text_request=avatar_models.SendMessageRequestTextRequest(
                        command_type="START",
                        speech_text=text,
                        id=str(uuid.uuid4())
                    )
                )
                send_resp = self.client.send_message(send_req)
                ten.log_info(f"aliyun_avatar_python 播报结果: {send_resp}")
        except Exception as e:
            ten.log_warn(
                f"aliyun_avatar_python on_data get_property_string {TEXT_DATA_TEXT_FIELD} error: {e}"
            )
            return

        try:
            final, _ = data.get_property_bool(TEXT_DATA_FINAL_FIELD)
        except Exception as e:
            ten.log_warn(
                f"aliyun_avatar_python on_data get_property_bool {TEXT_DATA_FINAL_FIELD} error: {e}"
            )
            return

        ten.log_debug(
            f"aliyun_avatar_python on_data {TEXT_DATA_TEXT_FIELD}: {text} {TEXT_DATA_FINAL_FIELD}: {final}"
        )
