"use client";

import * as React from "react";

import { LoadingButton } from "@/components/Button/LoadingButton";
import { setAgentConnected, setMobileActiveTab } from "@/store/reducers/global";
import {
  useAppDispatch,
  useAppSelector,
  apiPing,
  apiStartService,
  apiStopService,
  MOBILE_ACTIVE_TAB_MAP,
  EMobileActiveTab,
  isEditModeOn,
  useGraphs,
} from "@/common";
import { toast } from "sonner";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { cn } from "@/lib/utils";
import { RemotePropertyCfgSheet } from "@/components/Chat/ChatCfgPropertySelect";
import { RemoteGraphSelect } from "@/components/Chat/ChatCfgGraphSelect";
import { RemoteModuleCfgSheet } from "@/components/Chat/ChatCfgModuleSelect";
import { TrulienceCfgSheet } from "../Chat/ChatCfgTrulienceSetting";
import { Button } from "@/components/ui/button";
import { Settings } from "lucide-react";
import SettingsDialog from "@/components/Settings/SettingsDialog";
import { useAgentSettings } from "@/hooks/useAgentSettings";

// 导入类型
interface IPingResponse {
  message: string;
}

let intervalId: NodeJS.Timeout | null = null;

export default function Action(props: { className?: string }) {
  const { className } = props;
  const dispatch = useAppDispatch();
  const agentConnected = useAppSelector((state) => state.global.agentConnected);
  const channel = useAppSelector((state) => state.global.options.channel);
  const userId = useAppSelector((state) => state.global.options.userId);
  const language = useAppSelector((state) => state.global.language);
  const voiceType = useAppSelector((state) => state.global.voiceType);
  const selectedGraphId = useAppSelector(
    (state) => state.global.selectedGraphId,
  );
  const graphList = useAppSelector((state) => state.global.graphList);
  const mobileActiveTab = useAppSelector(
    (state) => state.global.mobileActiveTab,
  );
  const [loading, setLoading] = React.useState(false);
  const [settingsOpen, setSettingsOpen] = React.useState(false);
  const { agentSettings, saveSettings } = useAgentSettings();

  React.useEffect(() => {
    if (channel) {
      checkAgentConnected();
    }
  }, [channel]);

  const checkAgentConnected = async () => {
    try {
      const res = await apiPing(channel);
      // 如果 ping 成功，说明 Agent 已连接
      dispatch(setAgentConnected(true));
    } catch (error) {
      console.log("Agent not connected:", error);
    }
  };

  const onClickConnect = async () => {
    if (loading) {
      return;
    }
    setLoading(true);
    if (agentConnected) {
      try {
        await apiStopService(channel);
        dispatch(setAgentConnected(false));
        toast.success("Agent 已断开连接");
        stopPing();
      } catch (error) {
        console.error("Error stopping service:", error);
        toast.error("断开连接失败");
      }
    } else {
      const selectedGraph = graphList.find(
        (graph) => graph.uuid === selectedGraphId,
      );
      if (!selectedGraph) {
        toast.error("Please select a graph first");
        setLoading(false);
        return;
      }

      const { token, env } = agentSettings;
      try {
        await apiStartService({
          channel,
          userId,
          graphName: selectedGraph.name,
          language,
          voiceType,
          token: token || undefined,
          envProperties: env,
        });
        dispatch(setAgentConnected(true));
        toast.success("Agent 已连接");
        startPing();
      } catch (error) {
        console.error("Error starting service:", error);
        toast.error("连接失败");
      }
    }
    setLoading(false);
  };

  const startPing = () => {
    if (intervalId) {
      stopPing();
    }
    intervalId = setInterval(() => {
      apiPing(channel);
    }, 3000);
  };

  const stopPing = () => {
    if (intervalId) {
      clearInterval(intervalId);
      intervalId = null;
    }
  };

  const onChangeMobileActiveTab = (tab: string) => {
    dispatch(setMobileActiveTab(tab as EMobileActiveTab));
  };

  return (
    <>
      {/* Action Bar */}
      <div
        className={cn(
          "mx-2 mt-2 flex items-center justify-between rounded-t-lg bg-white shadow-sm border border-gray-200 p-2 md:m-2 md:rounded-lg",
          className,
        )}
      >
        {/* -- Description Part */}
        <div className="hidden md:block">
          <span className="ml-2 text-xs text-gray-600 whitespace-nowrap">
            实时对话式 AI 智能体
          </span>
        </div>

        <div className="flex w-full flex-col md:flex-row md:items-center justify-between md:justify-end">
          {/* -- Tabs Section */}
          <Tabs
            defaultValue={mobileActiveTab}
            className="md:hidden w-full md:flex-row"
            onValueChange={onChangeMobileActiveTab}
          >
            <TabsList className="flex justify-center md:justify-start">
              {Object.values(EMobileActiveTab).map((tab) => (
                <TabsTrigger key={tab} value={tab} className="w-24 text-sm">
                  {MOBILE_ACTIVE_TAB_MAP[tab]}
                </TabsTrigger>
              ))}
            </TabsList>
          </Tabs>

          {/* -- Graph Select Part */}
          <div className="flex flex-wrap items-center justify-between w-full md:w-auto gap-2 mt-2 md:mt-0">
            <RemoteGraphSelect />
            {isEditModeOn && (
              <>
                <TrulienceCfgSheet />
                <RemoteModuleCfgSheet />
                <RemotePropertyCfgSheet />
              </>
            )}

            {/* -- Action Button */}
            <div className="ml-auto flex items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setSettingsOpen(true)}
                className="flex items-center gap-2"
              >
                <Settings className="h-4 w-4" />
                <span className="hidden md:inline">设置</span>
              </Button>
              <LoadingButton
                onClick={onClickConnect}
                variant={!agentConnected ? "default" : "destructive"}
                size="sm"
                disabled={!selectedGraphId && !agentConnected}
                className="w-fit min-w-24"
                loading={loading}
                svgProps={{ className: "h-4 w-4 text-muted-foreground" }}
              >
                {loading ? "连接中" : !agentConnected ? "连接" : "断开"}
              </LoadingButton>
            </div>
          </div>
        </div>
      </div>

      {/* Settings Dialog */}
      <SettingsDialog
        open={settingsOpen}
        onOpenChange={setSettingsOpen}
        defaultValues={{
          greeting: agentSettings.greeting,
          prompt: agentSettings.prompt,
          token: agentSettings.token,
          bailian_dashscope_api_key:
            agentSettings.env?.BAILIAN_DASHSCOPE_API_KEY || "",
        }}
        onSubmit={(values) => {
          saveSettings({
            greeting: values.greeting || "",
            prompt: values.prompt || "",
            token: values.token || "",
            env: {
              BAILIAN_DASHSCOPE_API_KEY: values.bailian_dashscope_api_key || "",
              GREETING: values.greeting || "",
              CHAT_PROMPT: values.prompt || "",
            },
          });
          toast.success("设置已保存");
        }}
      />
    </>
  );
}
