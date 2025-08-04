import { IconProps } from "../types"
import { ReactComponent as MicMuteSvg } from "@/assets/mic_mute.svg"
import { ReactComponent as MicUnMuteSvg } from "@/assets/mic_unmute.svg"

interface IMicIconProps extends IconProps {
  active?: boolean
}

export const MicIcon = (props: IMicIconProps) => {
  const { active, color, ...rest } = props

  if (active) {
    return <MicUnMuteSvg style={{ color: color || "#3D53F5" }} {...rest} />
  } else {
    return <MicMuteSvg style={{ color: color || "#667085" }} {...rest} />
  }
}
