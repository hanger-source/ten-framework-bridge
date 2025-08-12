import { useMultibandTrackVolume } from "@/common/hooks";

export interface AudioVisualizerProps {
  type: "agent" | "user";
  track?: MediaStreamTrack | null;
  gap: number;
  barWidth: number;
  minBarHeight: number;
  maxBarHeight: number;
  borderRadius: number;
}

export default function AudioVisualizer(props: AudioVisualizerProps) {
  const {
    // frequencies,
    track,
    gap,
    barWidth,
    minBarHeight,
    maxBarHeight,
    borderRadius,
    type,
  } = props;

  const frequencies = useMultibandTrackVolume(track, 20);

  return (
    <div
      className={`flex items-center justify-center`}
      style={{ gap: `${gap}px` }}
    >
      {frequencies.map((frequency, index) => {
        const style = {
          height:
            minBarHeight + frequency * (maxBarHeight - minBarHeight) + "px",
          borderRadius: borderRadius + "px",
          width: barWidth + "px",
          transition:
            "background-color 0.35s ease-out, transform 0.25s ease-out, height 0.1s ease-out", // 添加 height 过渡
          // transform: transform,
          backgroundColor: type === "agent" ? "#0888FF" : "#3B82F6",
          boxShadow: type === "agent" ? "0 0 10px #EAECF0" : "0 0 5px #3B82F6",
        };

        return <span key={index} style={style} />;
      })}
    </div>
  );
}