import { IconProps } from "../types";
import MicMuteSvg from "@/assets/mic_mute.svg?react";
import MicUnMuteSvg from "@/assets/mic_unmute.svg?react";

interface IMicIconProps extends IconProps {
  active?: boolean;
}

export const MicIcon = (props: IMicIconProps) => {
  const { active, className, ...rest } = props; // Keep className for styling

  const IconComponent = active ? MicUnMuteSvg : MicMuteSvg;

  return (
    <IconComponent
      className={className}
      fill={active ? "#000000" : "currentColor"}
      {...rest}
    />
  );
};
