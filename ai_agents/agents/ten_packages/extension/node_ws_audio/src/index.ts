//
// This file is part of TEN Framework, an open source project.
// Licensed under the Apache License, Version 2.0.
// See the LICENSE file for more information.
//
import {
  Addon,
  RegisterAddonAsExtension,
  Extension,
  TenEnv,
  Cmd,
  CmdResult,
  StatusCode,
} from "ten-runtime-nodejs";

class NodeWsAudioExtension extends Extension {
  constructor(name: string) {
    super(name);
  }

  async onConfigure(_tenEnv: TenEnv): Promise<void> {
    console.log("NodeWsAudioExtension onConfigure");
  }

  async onInit(_tenEnv: TenEnv): Promise<void> {
    console.log("NodeWsAudioExtension onInit");
  }

  async onStart(_tenEnv: TenEnv): Promise<void> {
    console.log("NodeWsAudioExtension onStart");
  }

  async onCmd(tenEnv: TenEnv, cmd: Cmd): Promise<void> {
    console.log("NodeWsAudioExtension onCmd", cmd.getName());

    const cmdResult = CmdResult.Create(StatusCode.OK, cmd);
    cmdResult.setPropertyString("detail", "This is a demo");
    tenEnv.returnResult(cmdResult);
  }

  async onStop(_tenEnv: TenEnv): Promise<void> {
    console.log("NodeWsAudioExtension onStop");
  }

  async onDeinit(_tenEnv: TenEnv): Promise<void> {
    console.log("NodeWsAudioExtension onDeinit");
  }
}

@RegisterAddonAsExtension("node_ws_audio")
class NodeWsAudioExtensionAddon extends Addon {
  async onCreateInstance(
    _tenEnv: TenEnv,
    instanceName: string
  ): Promise<Extension> {
    return new NodeWsAudioExtension(instanceName);
  }
}
