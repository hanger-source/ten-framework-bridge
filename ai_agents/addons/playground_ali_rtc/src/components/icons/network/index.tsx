import { ReactComponent as AverageSvg } from "@/assets/network/average.svg"
import { ReactComponent as GoodSvg } from "@/assets/network/good.svg"
import { ReactComponent as PoorSvg } from "@/assets/network/poor.svg"
import { ReactComponent as DisconnectedSvg } from "@/assets/network/disconnected.svg"
import { ReactComponent as ExcellentSvg } from "@/assets/network/excellent.svg"

import { IconProps } from "../types"

interface INetworkIconProps extends IconProps {
  level?: number
}

export const NetworkIcon = (props: INetworkIconProps) => {
  const { level, ...rest } = props
  switch (level) {
    case 0:
      return <DisconnectedSvg {...rest} />
    case 1:
      return <ExcellentSvg {...rest} />
    case 2:
      return <GoodSvg {...rest} />
    case 3:
      return <AverageSvg {...rest} />
    case 4:
      return <AverageSvg {...rest} />
    case 5:
      return <PoorSvg {...rest} />
    case 6:
      return <DisconnectedSvg {...rest} />
    default:
      return <DisconnectedSvg {...rest} />
  }
}
