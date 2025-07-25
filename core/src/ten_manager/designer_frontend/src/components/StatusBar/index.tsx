//
// Copyright © 2025 Agora
// This file is part of TEN Framework, an open source project.
// Licensed under the Apache License, Version 2.0, with certain conditions.
// Refer to the "LICENSE" file in the root directory for more information.
//

import {
  ChevronRightIcon,
  FolderOpenIcon,
  FolderTreeIcon,
  MessageSquareShareIcon,
} from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";
import { useFetchApps } from "@/api/services/apps";
import { GraphSelectPopupTitle } from "@/components/Popup/Default/GraphSelect";
import { Button } from "@/components/ui/Button";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/Tooltip";
import { TEN_FRAMEWORK_DESIGNER_FEEDBACK_ISSUE_URL } from "@/constants";
import {
  APPS_MANAGER_WIDGET_ID,
  CONTAINER_DEFAULT_ID,
  GRAPH_SELECT_WIDGET_ID,
} from "@/constants/widgets";
import { cn } from "@/lib/utils";
import { useAppStore, useWidgetStore } from "@/store";
import {
  EDefaultWidgetType,
  EWidgetCategory,
  EWidgetDisplayType,
} from "@/types/widgets";
import { LoadedAppsPopupTitle } from "../Popup/Default/App";

export default function StatusBar(props: { className?: string }) {
  const { className } = props;

  return (
    <footer
      className={cn(
        "flex select-none items-center justify-between text-xs",
        "h-5 w-full",
        "fixed right-0 bottom-0 left-0",
        "bg-background/80 backdrop-blur-xs",
        "border-[#e5e7eb] border-t dark:border-[#374151]",
        "select-none",
        className
      )}
    >
      <div className="flex h-full w-full gap-2">
        <StatusApps />
        <StatusWorkspace />
      </div>
      <div className="flex w-fit gap-2 px-2">
        <Feedback />
      </div>
    </footer>
  );
}

const StatusApps = () => {
  const { t } = useTranslation();
  const { data, error, isLoading } = useFetchApps();
  const { appendWidget } = useWidgetStore();
  const { currentWorkspace, updateCurrentWorkspace } = useAppStore();

  const openAppsManagerPopup = () => {
    appendWidget({
      container_id: CONTAINER_DEFAULT_ID,
      group_id: APPS_MANAGER_WIDGET_ID,
      widget_id: APPS_MANAGER_WIDGET_ID,

      category: EWidgetCategory.Default,
      display_type: EWidgetDisplayType.Popup,

      title: <LoadedAppsPopupTitle />,
      metadata: {
        type: EDefaultWidgetType.AppsManager,
      },
    });
  };

  React.useEffect(() => {
    if (
      !currentWorkspace?.initialized &&
      !currentWorkspace?.app?.base_dir &&
      data?.app_info?.[0]?.base_dir
    ) {
      updateCurrentWorkspace({
        app: data?.app_info?.[0],
      });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data, currentWorkspace?.app?.base_dir, currentWorkspace?.initialized]);

  React.useEffect(() => {
    if (error) {
      toast.error(t("statusBar.appsError"));
    }
  }, [error, t]);

  if (isLoading || !data) {
    return null;
  }

  return (
    <Button
      variant="ghost"
      size="status"
      className=""
      onClick={openAppsManagerPopup}
    >
      <FolderTreeIcon className="size-3" />
      <span className="">
        {t("statusBar.appsLoadedWithCount", {
          count: data.app_info?.length || 0,
        })}
      </span>
    </Button>
  );
};

const StatusWorkspace = () => {
  const { t } = useTranslation();
  const { currentWorkspace } = useAppStore();
  const { appendWidget } = useWidgetStore();

  const [baseDirAbbrMemo, baseDirMemo] = React.useMemo(() => {
    if (!currentWorkspace?.app?.base_dir) {
      return [null, null];
    }
    const lastFolderName = currentWorkspace.app.base_dir.split("/").pop();
    return [`...${lastFolderName}`, currentWorkspace.app.base_dir];
  }, [currentWorkspace?.app?.base_dir]);

  const graphNameMemo = React.useMemo(() => {
    if (!currentWorkspace.graph?.name) {
      return null;
    }
    return currentWorkspace.graph.name;
  }, [currentWorkspace.graph?.name]);

  const onOpenExistingGraph = () => {
    appendWidget({
      container_id: CONTAINER_DEFAULT_ID,
      group_id: GRAPH_SELECT_WIDGET_ID,
      widget_id: GRAPH_SELECT_WIDGET_ID,

      category: EWidgetCategory.Default,
      display_type: EWidgetDisplayType.Popup,

      title: <GraphSelectPopupTitle />,
      metadata: {
        type: EDefaultWidgetType.GraphSelect,
      },
      popup: {
        width: 0.5,
        height: 0.8,
      },
    });
  };

  if (!baseDirMemo) {
    return null;
  }

  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <Button
            variant="ghost"
            size="status"
            className=""
            onClick={onOpenExistingGraph}
          >
            <FolderOpenIcon className="size-3" />
            <span className="">{baseDirAbbrMemo}</span>

            {graphNameMemo && (
              <>
                <ChevronRightIcon className="size-3" />
                <span className="">{graphNameMemo}</span>
              </>
            )}
          </Button>
        </TooltipTrigger>
        <TooltipContent className="flex flex-col gap-1">
          <p className="text-sm">{t("statusBar.workspace.title")}</p>
          <p className="flex justify-between gap-1">
            <span className="min-w-24">{t("statusBar.workspace.baseDir")}</span>
            <span className="">{baseDirMemo}</span>
          </p>
          <p className="flex justify-between gap-1">
            <span className="">{t("statusBar.workspace.graphName")}</span>
            <span className="">
              {graphNameMemo ?? t("popup.selectGraph.unspecified")}
            </span>
          </p>
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
};

const Feedback = () => {
  const { t } = useTranslation();

  return (
    <>
      <Button asChild variant="ghost" size="status" className="truncate">
        <a
          target="_blank"
          referrerPolicy="no-referrer"
          href={TEN_FRAMEWORK_DESIGNER_FEEDBACK_ISSUE_URL}
          className="animate-[pulse_1s_ease-in-out_5]"
        >
          <MessageSquareShareIcon className="size-3" />
          <span>{t("statusBar.feedback.title")}</span>
        </a>
      </Button>
    </>
  );
};
