export interface IconProps {
  width?: number;
  height?: number;
  fill?: string;
  viewBox?: string;
  size?: "small" | "default";
  // style?: React.CSSProperties
  transform?: string;
  onClick?: () => void;
  className?: string; // Ensure className is present
}
