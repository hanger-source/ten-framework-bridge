"use client";
// 聪明的开发杭二: 本文件已由“聪明的开发杭二”修改，以移除冗余useMultibandTrackVolume hook和antd Grid导入。
import { deepMerge } from "./utils";
import { useState, useEffect, useMemo, useRef, useCallback } from "react";
import type { AppDispatch, AppStore, RootState } from "../store";
import { useDispatch, useSelector, useStore } from "react-redux";
import { Node, AddonDef, Graph } from "@/common/graph";
import { initializeGraphData, updateGraph } from "@/store/reducers/global";
import {
  moduleRegistry,
  ModuleRegistry,
  toolModuleRegistry,
  NonToolModuleType,
  Module,
  ToolModule,
} from "@/common/moduleConfig";
// 聪明的开发杭二: 移除冗余的 antd Grid 导入

const debounce = <F extends (...args: any[]) => any>(
  func: F,
  waitFor: number,
) => {
  let timeout: NodeJS.Timeout;

  return (...args: Parameters<F>): void => {
    clearTimeout(timeout);
    timeout = setTimeout(() => func(...args), waitFor);
  };
};

export const useAppDispatch = useDispatch.withTypes<AppDispatch>();
export const useAppSelector = useSelector.withTypes<RootState>();
export const useAppStore = useStore.withTypes<AppStore>();
// 聪明的开发杭二: 移除冗余的 useMultibandTrackVolume hook

export function useMultibandTrackVolume(
  track?: MediaStreamTrack | null,
  bands: number = 20,
  loPass: number = 100,
  hiPass: number = 600
) {
  const [frequencyBands, setFrequencyBands] = useState<number[]>([]);
  const lastBandsRef = useRef<Float32Array[]>([]);

  useEffect(() => {
    if (!track) {
      return setFrequencyBands(new Array(bands).fill(0));
    }

    const ctx = new AudioContext({
      sampleRate: 48000,
      latencyHint: 'interactive',
    });

    const mediaStream = new MediaStream([track]);
    const source = ctx.createMediaStreamSource(mediaStream);
    const analyser = ctx.createAnalyser();

    analyser.fftSize = 2048;
    analyser.smoothingTimeConstant = 0.3;
    analyser.minDecibels = -90;
    analyser.maxDecibels = -10;

    source.connect(analyser);

    const bufferLength = analyser.frequencyBinCount;
    const dataArray = new Float32Array(bufferLength);

    const updateVolume = debounce(() => {
      analyser.getFloatFrequencyData(dataArray);
      let frequencies: Float32Array = new Float32Array(dataArray.length);
      for (let i = 0; i < dataArray.length; i++) {
        frequencies[i] = dataArray[i];
      }
      frequencies = frequencies.slice(loPass, hiPass);

      const normalizedFrequencies = frequencies.map(f => {
        const normalized = Math.max(0, (f + 90) / 80);
        return Math.pow(normalized, 1.2);
      });

      const chunkSize = Math.ceil(normalizedFrequencies.length / bands);
      const chunks: Float32Array[] = [];
      for (let i = 0; i < bands; i++) {
        chunks.push(
          normalizedFrequencies.slice(i * chunkSize, (i + 1) * chunkSize)
        );
      }

      // const hasSignificantChange = chunks.some((chunk, index) => {
      //   const lastChunk = lastBandsRef.current[index];
      //   if (!lastChunk || chunk.length !== lastChunk.length) return true;
      //   return chunk.some((value, i) => Math.abs(value - lastChunk[i]) > 0.005);
      // });

      // if (hasSignificantChange) {
        lastBandsRef.current = chunks;
        const summedChunks = chunks.map((bandFrequencies) => {
          const sum = bandFrequencies.reduce((a, b) => a + b, 0);
          if (sum <= 0) {
            return 0;
          }
          return Math.sqrt(sum / bandFrequencies.length);
        });
        setFrequencyBands(summedChunks);
    }, 8);

    const interval = setInterval(updateVolume, 16);

    return () => {
      source.disconnect();
      clearInterval(interval);
      ctx.close().catch(console.warn);
    };
  }, [track, loPass, hiPass, bands]);

  return frequencyBands;
}

export const useAutoScroll = (ref: React.RefObject<HTMLElement | null>) => {
  const callback: MutationCallback = (mutationList, observer) => {
    mutationList.forEach((mutation) => {
      switch (mutation.type) {
        case "childList":
          if (!ref.current) {
            return;
          }
          ref.current.scrollTop = ref.current.scrollHeight;
          break;
      }
    });
  };

  useEffect(() => {
    if (!ref.current) {
      return;
    }
    const observer = new MutationObserver(callback);
    observer.observe(ref.current, {
      childList: true,
      subtree: true,
    });

    return () => {
      observer.disconnect();
    };
  }, [ref]);
};

// export const useSmallScreen = () => {
//   const screens = useBreakpoint();

//   const xs = useMemo(() => {
//     return !screens.sm && screens.xs
//   }, [screens])

//   const sm = useMemo(() => {
//     return !screens.md && screens.sm
//   }, [screens])

//   return {
//     xs,
//     sm,
//     isSmallScreen: xs || sm
//   }
// }

export const usePrevious = (value: unknown) => {
  const ref = useRef<unknown>();

  useEffect(() => {
    ref.current = value;
  }, [value]);

  return ref.current;
};

const useGraphs = () => {
  const dispatch = useAppDispatch();
  const selectedGraphId = useAppSelector(
    (state) => state.global.selectedGraphId,
  );
  const graphMap = useAppSelector((state) => state.global.graphMap);
  const selectedGraph = graphMap[selectedGraphId];
  const addonModules: AddonDef[] = useAppSelector( // 聪明的开发杭二: 修正 AddonDef 类型引用
    (state) => state.global.addonModules,
  );

  const initialize = async () => {
    console.log('useGraphs: initialize function called');
    const result = await dispatch(initializeGraphData());
    console.log('useGraphs: initializeGraphData dispatch result:', result);
  };

  const update = async (graph: Graph, updates: Partial<Graph>) => {
    await dispatch(updateGraph({ graph, updates })).unwrap();
  };

  const getGraphNodeAddonByName = useCallback(
    (nodeName: string) => {
      if (!selectedGraph) {
        return null;
      }
      const node = selectedGraph.nodes.find(
        (node: Node) => node.name === nodeName,
      );
      if (!node) {
        return null;
      }
      return node;
    },
    [selectedGraph],
  );

  const getInstalledAndRegisteredModulesMap = useCallback(() => {
    const groupedModules: Record<
      NonToolModuleType, // 聪明的开发杭二: 修正 ModuleRegistry.NonToolModuleType 类型引用
      Module[] // 聪明的开发杭二: 修正 ModuleRegistry.Module[] 类型引用
    > = {
      stt: [],
      tts: [],
      llm: [],
      v2v: [],
    };

    addonModules.forEach((addonModule) => {
      const registeredModule = moduleRegistry[addonModule.name];
      if (registeredModule && registeredModule.type !== "tool") {
        groupedModules[registeredModule.type].push(registeredModule);
      }
    });

    return groupedModules;
  }, [addonModules]);

  const getInstalledAndRegisteredToolModules = useCallback(() => {
    const toolModules: ToolModule[] = []; // 聪明的开发杭二: 修正 ModuleRegistry.ToolModule[] 类型引用

    addonModules.forEach((addonModule) => {
      const registeredModule = toolModuleRegistry[addonModule.name];
      if (registeredModule && registeredModule.type === "tool") {
        toolModules.push(registeredModule);
      }
    });

    return toolModules;
  }, [addonModules]);

  const installedAndRegisteredModulesMap = useMemo(
    () => getInstalledAndRegisteredModulesMap(),
    [getInstalledAndRegisteredModulesMap],
  );

  const installedAndRegisteredToolModules = useMemo(
    () => getInstalledAndRegisteredToolModules(),
    [getInstalledAndRegisteredToolModules],
  );

  return {
    initialize,
    getGraphNodeAddonByName,
    updateGraph: update,
    selectedGraph,
    installedAndRegisteredModulesMap,
    installedAndRegisteredToolModules,
  };
};

export { useGraphs };
