import { IconProps } from "../types";
import micMuteSvg from "@/assets/mic_mute.svg";
import micUnMuteSvg from "@/assets/mic_unmute.svg";

interface IMicIconProps extends IconProps {
  active?: boolean;
}

export const MicIcon = (props: IMicIconProps) => {
  const { active, ...rest } = props; // Removed color from destructuring

  const iconSrc = active ? micUnMuteSvg : micMuteSvg;

  return (
    <img
      src={iconSrc}
      alt={active ? "Mic Unmuted" : "Mic Muted"}
      {...rest} // Pass other props like className, width, height
    />
  );
};
