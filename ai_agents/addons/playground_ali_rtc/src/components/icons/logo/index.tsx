import { IconProps } from "../types"
import { ReactComponent as LogoSvg } from "@/assets/logo.svg"
import { ReactComponent as SmallLogoSvg } from "@/assets/logo_small.svg"

export const LogoIcon = (props: IconProps) => {
  const { size = "default" } = props
  return size == "small" ? <SmallLogoSvg {...props} /> : <LogoSvg {...props} />
}
