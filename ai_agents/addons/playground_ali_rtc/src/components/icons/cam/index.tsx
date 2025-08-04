import { ReactComponent as CamMuteSvg } from "@/assets/cam_mute.svg"
import { ReactComponent as CamUnMuteSvg } from "@/assets/cam_unmute.svg"
import { IconProps } from "../types"

interface ICamIconProps extends IconProps {
  active?: boolean
}

export const CamIcon = (props: ICamIconProps) => {
  const { active, ...rest } = props

  if (active) {
    return <CamUnMuteSvg {...rest} />
  } else {
    return <CamMuteSvg {...rest} />
  }
}
