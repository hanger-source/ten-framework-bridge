"use client";

import * as React from "react";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { InfoIcon, PaletteIcon } from "@/components/Icon";
import { useAppSelector, useAppDispatch, COLOR_LIST } from "@/common";
import { setThemeColor } from "@/store/reducers/global";
import { cn } from "@/lib/utils";
import { HexColorPicker } from "react-colorful";
import { RootState } from "@/store"; // Import RootState

import styles from "./Header.module.css";

export function HeaderRoomInfo() {
  const options = useAppSelector((state) => state.global.options);
  const { channel, userId } = options;

  const roomConnected = useAppSelector((state) => state.global.roomConnected);
  const agentConnected = useAppSelector((state) => state.global.agentConnected);

  const roomConnectedText = React.useMemo(() => {
    return roomConnected ? "TRUE" : "FALSE";
  }, [roomConnected]);

  const agentConnectedText = React.useMemo(() => {
    return agentConnected ? "TRUE" : "FALSE";
  }, [agentConnected]);

  return (
    <>
      {/* Removed HeaderRoomInfo content */}
    </>
  );
}

export function HeaderActions() {
  const websocketConnectionState = useAppSelector((state: RootState) => state.global.websocketConnectionState);
  return (
    <div className="flex space-x-2 md:space-x-4">
      {/* <NextLink href={GITHUB_URL} target="_blank">
        <GitHubIcon className="h-4 w-4 md:h-5 md:w-5" />
        <span className="sr-only">GitHub</span>
      </NextLink> */}
      <ThemePalettePopover />
      <NetworkIndicator connectionState={websocketConnectionState} />
    </div>
  );
}

export const ThemePalettePopover = () => {
  const themeColor = useAppSelector((state) => state.global.themeColor);
  const dispatch = useAppDispatch();

  const onMainClickSelect = (index: number) => {
    const target = COLOR_LIST[index];
    if (target.active !== themeColor) {
      dispatch(setThemeColor(target.active));
    }
  };

  const onColorSliderChange = (color: string) => {
    console.log(color);
    dispatch(setThemeColor(color));
  };

  return (
    <>
      <Popover>
        <PopoverTrigger>
          <PaletteIcon className="h-4 w-4 md:h-5 md:w-5" color={themeColor} />
        </PopoverTrigger>
        <PopoverContent className="space-y-2 border-none bg-[var(--background-color,#1C1E22)]">
          <div className="text-sm font-semibold text-[var(--Grey-300,#EAECF0)]">
            STYLE
          </div>
          <div className="mt-4 flex gap-3">
            {COLOR_LIST.map((item, index) => {
              const isSelected = item.active === themeColor;
              return (
                <button
                  onClick={() => onMainClickSelect(index)}
                  className={cn(
                    "relative h-7 w-7 rounded-full",
                    {
                      "ring-2 ring-offset-2": isSelected,
                    },
                    "transition-all duration-200 ease-in-out",
                  )}
                  style={{
                    backgroundColor: item.default,
                    ...(isSelected && { ringColor: item.active }),
                  }}
                  key={index}
                >
                  <span
                    className="absolute inset-1 rounded-full"
                    style={{
                      backgroundColor: item.active,
                    }}
                  ></span>
                </button>
              );
            })}
          </div>
          <div className={cn("flex h-6 items-center", styles.colorPicker)}>
            <HexColorPicker color={themeColor} onChange={onColorSliderChange} />
          </div>
        </PopoverContent>
      </Popover>
    </>
  );
};

import NetworkIndicator from "@/components/Dynamic/NetworkIndicator";
