import * as React from "react";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useAppDispatch, useAppSelector } from "@/common/hooks";
import { setSelectedGraphId } from "@/store/reducers/global";
import { cn } from "@/lib/utils";

export function RemoteGraphSelect() {
  const dispatch = useAppDispatch();
  const graphName = useAppSelector((state) => state.global.selectedGraphId);
  const graphs = useAppSelector((state) => state.global.graphList);
  const websocketConnectionState = useAppSelector((state) => state.global.websocketConnectionState);

  const onGraphNameChange = (val: string) => {
    dispatch(setSelectedGraphId(val));
  };

  const graphOptions = graphs.map((item) => ({
    label: item.name,
    value: item.uuid,
  }));

  return (
    <>
      <Select
        value={graphName}
        onValueChange={onGraphNameChange}
        disabled={websocketConnectionState !== "open"}
      >
        <SelectTrigger
          className={cn(
            "w-auto", // or "w-auto max-w-full" if you want to keep the existing defaults
          )}
        >
          <SelectValue placeholder={"选择流程图"} />
        </SelectTrigger>
        <SelectContent>
          {graphOptions.map((item) => (
            <SelectItem key={item.value} value={item.value}>
              {item.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </>
  );
}
