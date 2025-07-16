//
// This file is part of TEN Framework, an open source project.
// Licensed under the Apache License, Version 2.0.
// See the LICENSE file for more information.
//

package go_bridge

import (
	"fmt"

	ten "ten_framework/ten_runtime"
)

type defaultExtension struct {
	ten.DefaultExtension
}

func newExtension(name string) ten.Extension {
	return &defaultExtension{}
}

func (e *defaultExtension) OnStart(tenEnv ten.TenEnv) {
	tenEnv.Log(ten.LogLevelDebug, "OnStart")
	tenEnv.OnStartDone()
}

func (e *defaultExtension) OnStop(tenEnv ten.TenEnv) {
	tenEnv.Log(ten.LogLevelDebug, "OnStop")

	tenEnv.OnStopDone()
}

func (e *defaultExtension) OnCmd(
	tenEnv ten.TenEnv,
	cmd ten.Cmd,
) {
	tenEnv.Log(ten.LogLevelDebug, "OnCmd")

	cmdResult, _ := ten.NewCmdResult(ten.StatusCodeOk, cmd)
	tenEnv.ReturnResult(cmdResult, nil)
}

func (e *defaultExtension) OnConfigure(tenEnv ten.TenEnv) {
	tenEnv.Log(ten.LogLevelDebug, "[go_bridge] OnConfigure called")
	tenEnv.OnConfigureDone()
}

func (e *defaultExtension) OnInit(tenEnv ten.TenEnv) {
	tenEnv.Log(ten.LogLevelDebug, "[go_bridge] OnInit called")
	tenEnv.OnInitDone()
}

func (e *defaultExtension) OnDeinit(tenEnv ten.TenEnv) {
	tenEnv.Log(ten.LogLevelDebug, "[go_bridge] OnDeinit called")
	tenEnv.OnDeinitDone()
}

func (e *defaultExtension) OnData(tenEnv ten.TenEnv, data ten.Data) {
	name, err := data.GetName()
	jsonBytes, err2 := data.GetPropertyToJSONBytes("")
	if err != nil {
		tenEnv.Log(ten.LogLevelWarn, "[go_bridge] OnData GetName error: "+err.Error())
	} else if err2 != nil {
		tenEnv.Log(ten.LogLevelWarn, "[go_bridge] OnData GetPropertyToJSONBytes error: "+err2.Error())
	} else {
		tenEnv.Log(ten.LogLevelDebug, "[go_bridge] OnData name: "+name+" properties: "+string(jsonBytes))
	}
}

func (e *defaultExtension) OnVideoFrame(tenEnv ten.TenEnv, videoFrame ten.VideoFrame) {
	tenEnv.Log(ten.LogLevelDebug, fmt.Sprintf("[go_bridge] OnVideoFrame called: %+v", videoFrame))
}

func (e *defaultExtension) OnAudioFrame(tenEnv ten.TenEnv, audioFrame ten.AudioFrame) {
	tenEnv.Log(ten.LogLevelDebug, fmt.Sprintf("[go_bridge] OnAudioFrame called: %+v", audioFrame))
}

func init() {
	fmt.Println("defaultExtension init")

	// Register addon
	ten.RegisterAddonAsExtension(
		"go_bridge",
		ten.NewDefaultExtensionAddon(newExtension),
	)
}
