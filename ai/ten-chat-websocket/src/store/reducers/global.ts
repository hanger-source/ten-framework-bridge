import {
  IOptions,
  IChatItem,
  Language,
  VoiceType,
  ITrulienceSettings,
} from "@/types";
import { WebSocketConnectionState } from "@/types/websocket"; // Corrected import path for WebSocketConnectionState
import { createAsyncThunk, createSlice, PayloadAction } from "@reduxjs/toolkit";
import {
  EMobileActiveTab,
  DEFAULT_OPTIONS,
  COLOR_LIST,
  isEditModeOn,
  DEFAULT_TRULIENCE_OPTIONS,
} from "@/common/constant";
import {
  apiReloadPackage,
  apiFetchGraphs,
  apiFetchInstalledAddons,
  apiFetchGraphDetails,
  apiUpdateGraph,
  apiSaveProperty,
  apiLoadApp,
} from "@/common/request";
import {
  setOptionsToLocal,
  setTrulienceSettingsToLocal,
} from "@/common/storage";
import { AddonDef, Graph } from "@/common/graph";

export interface InitialState {
  options: IOptions;
  roomConnected: boolean;
  agentConnected: boolean;
  websocketConnectionState: WebSocketConnectionState;
  themeColor: string;
  language: Language;
  voiceType: VoiceType;
  chatItems: IChatItem[];
  selectedGraphId: string;
  graphList: Graph[];
  graphMap: Record<string, Graph>;
  addonModules: AddonDef[]; // Corrected type: AddonDef.Module[] -> AddonDef[]
  mobileActiveTab: EMobileActiveTab;
  trulienceSettings: ITrulienceSettings;
  activeGraphId: string;
  activeAppUri: string; // New: To store the active app_uri from backend
}

const getInitialState = (): InitialState => {
  return {
    options: DEFAULT_OPTIONS,
    themeColor: COLOR_LIST[0].active,
    roomConnected: false,
    agentConnected: false,
    websocketConnectionState: WebSocketConnectionState.CLOSED, // 聪明的开发杭一: 初始化WebSocket连接状态, changed from "closed"
    language: "en-US",
    voiceType: "male",
    chatItems: [],
    selectedGraphId: "",
    graphList: [],
    graphMap: {},
    addonModules: [],
    mobileActiveTab: EMobileActiveTab.AGENT,
    trulienceSettings: DEFAULT_TRULIENCE_OPTIONS,
    activeGraphId: "", // Initialize activeGraphId
    activeAppUri: "", // Initialize activeAppUri
  };
};

export const globalSlice = createSlice({
  name: "global",
  initialState: getInitialState(),
  reducers: {
    setOptions: (state, action: PayloadAction<Partial<IOptions>>) => {
      state.options = { ...state.options, ...action.payload };
      setOptionsToLocal(state.options);
    },
    setTrulienceSettings: (
      state,
      action: PayloadAction<ITrulienceSettings>,
    ) => {
      state.trulienceSettings = {
        ...state.trulienceSettings,
        ...action.payload,
      };
      setTrulienceSettingsToLocal(state.trulienceSettings);
    },
    setThemeColor: (state, action: PayloadAction<string>) => {
      state.themeColor = action.payload;
      document.documentElement.style.setProperty(
        "--theme-color",
        action.payload,
      );
    },
    setRoomConnected: (state, action: PayloadAction<boolean>) => {
      state.roomConnected = action.payload;
    },
    addChatItem: (state, action: PayloadAction<IChatItem>) => {
      const { userId, text, isFinal, type, time } = action.payload;
      const LastFinalIndex = state.chatItems.findLastIndex((el) => {
        return el.userId == userId && el.isFinal;
      });
      const LastNonFinalIndex = state.chatItems.findLastIndex((el) => {
        return el.userId == userId && !el.isFinal;
      });
      const LastFinalItem = state.chatItems[LastFinalIndex];
      const LastNonFinalItem = state.chatItems[LastNonFinalIndex];
      if (LastFinalItem) {
        // has last final Item
        if (time <= LastFinalItem.time) {
          // discard
          console.log(
            "[test] addChatItem, time < last final item, discard!:",
            text,
            isFinal,
            type,
          );
          return;
        } else {
          if (LastNonFinalItem) {
            console.log(
              "[test] addChatItem, update last item(none final):",
              text,
              isFinal,
              type,
            );
            state.chatItems[LastNonFinalIndex] = action.payload;
          } else {
            console.log(
              "[test] addChatItem, add new item:",
              text,
              isFinal,
              type,
            );
            state.chatItems.push(action.payload);
          }
        }
      } else {
        // no last final Item
        if (LastNonFinalItem) {
          console.log(
            "[test] addChatItem, update last item(none final):",
            text,
            isFinal,
            type,
          );
          state.chatItems[LastNonFinalIndex] = action.payload;
        } else {
          console.log("[test] addChatItem, add new item:", text, isFinal, type);
          state.chatItems.push(action.payload);
        }
      }
      state.chatItems.sort((a, b) => a.time - b.time);
    },
    setAgentConnected: (state, action: PayloadAction<boolean>) => {
      state.agentConnected = action.payload;
    },
    setLanguage: (state, action: PayloadAction<Language>) => {
      state.language = action.payload;
    },
    setSelectedGraphId: (state, action: PayloadAction<string>) => {
      state.selectedGraphId = action.payload;
    },
    setGraphList: (state, action: PayloadAction<Graph[]>) => {
      state.graphList = action.payload;
      console.log('Redux: graphList updated', action.payload);

      // Also populate graphMap with all fetched graphs
      action.payload.forEach(graph => {
        state.graphMap[graph.uuid] = graph;
      });
      console.log('Redux: graphMap populated with all graphs from list', state.graphMap);

      // If selectedGraphId is empty OR existing selectedGraphId is not in the new graphList, set to the first graph's UUID
      const currentSelectedGraphExists = action.payload.some(graph => graph.uuid === state.selectedGraphId);
      if (!state.selectedGraphId || !currentSelectedGraphExists) {
        if (action.payload.length > 0) {
          state.selectedGraphId = action.payload[0].uuid;
          console.log('Redux: selectedGraphId auto-set/reset to first graph', state.selectedGraphId);
        } else {
          state.selectedGraphId = ""; // No graphs available, clear selected ID
          console.log('Redux: No graphs available, selectedGraphId cleared.');
        }
      }
    },
    setVoiceType: (state, action: PayloadAction<VoiceType>) => {
      state.voiceType = action.payload;
    },
    setMobileActiveTab: (state, action: PayloadAction<EMobileActiveTab>) => {
      state.mobileActiveTab = action.payload;
    },
    // 聪明的开发杭一: 新增设置WebSocket连接状态的reducer
    setWebsocketConnectionState: (
      state,
      action: PayloadAction<WebSocketConnectionState>,
    ) => {
      state.websocketConnectionState = action.payload;
      if (action.payload === WebSocketConnectionState.CLOSED) {
        state.agentConnected = false; // Reset agentConnected if WebSocket closes
      }
    },
    reset: (state) => {
      Object.assign(state, getInitialState());
      document.documentElement.style.setProperty(
        "--theme-color",
        COLOR_LIST[0].active,
      );
    },
    setGraph: (state, action: PayloadAction<Graph>) => {
      const graphMap = JSON.parse(JSON.stringify(state.graphMap));
      graphMap[action.payload.uuid] = action.payload;
      state.graphMap = graphMap;
    },
    setAddonModules: (state, action: PayloadAction<AddonDef[]>) => { // Corrected type: AddonDef.Module[] -> AddonDef[]
      state.addonModules = JSON.parse(JSON.stringify(action.payload));
    },
    // Add setActiveGraphId reducer
    setActiveGraphId: (state, action: PayloadAction<string>) => {
      state.activeGraphId = action.payload;
      console.log('Redux: activeGraphId set to', action.payload);
    },
    setActiveAppUri: (state, action: PayloadAction<string>) => { // New reducer for activeAppUri
      state.activeAppUri = action.payload;
      console.log('Redux: activeAppUri set to', action.payload);
    },
  },
});

// Initialize graph data
const initializeGraphData = createAsyncThunk(
  "global/initializeGraphData",
  async (_, { dispatch }) => {
    if (isEditModeOn) {
      // only for development, below requests depend on dev-server
      await apiReloadPackage();
      await apiLoadApp();
      const [fetchedGraphs, modules] = await Promise.all([
        apiFetchGraphs(),
        apiFetchInstalledAddons(),
      ]);
      dispatch(setGraphList(fetchedGraphs.map((graph) => graph)));
      dispatch(setAddonModules(modules));
    } else {
      const fetchedGraphs = await apiFetchGraphs();
      dispatch(setGraphList(fetchedGraphs.map((graph) => graph)));
    }
  },
);
// Fetch graph details
const fetchGraphDetails = createAsyncThunk(
  "global/fetchGraphDetails",
  async (graph: Graph, { dispatch }) => {
    if (isEditModeOn) {
      const updatedGraph = await apiFetchGraphDetails(graph);
      dispatch(setGraph(updatedGraph));
    } else {
      // Do nothing in production
      return;
    }
  },
);

// Update a graph
export const updateGraph = createAsyncThunk(
  "global/updateGraph",
  async (
    { graph, updates }: { graph: Graph; updates: Partial<Graph> },
    { dispatch, rejectWithValue },
  ) => {
    try {
      await apiUpdateGraph(graph.uuid, updates);
      // await apiSaveProperty();
      const updatedGraph = await apiFetchGraphDetails(graph);
      dispatch(setGraph(updatedGraph));
      return updatedGraph; // Optionally return the updated graph
    } catch (error: unknown) {
      // Handle error gracefully
      console.error("Error updating graph:", error);
      return rejectWithValue((error as { response?: { data: unknown }; message: string }).response?.data || (error as { message: string }).message);
    }
  },
);

export const {
  reset,
  setOptions,
  setRoomConnected,
  setAgentConnected,
  setVoiceType,
  addChatItem,
  setThemeColor,
  setLanguage,
  setSelectedGraphId,
  setGraphList,
  setMobileActiveTab,
  setGraph,
  setAddonModules,
  setTrulienceSettings,
  setWebsocketConnectionState, // 聪明的开发杭一: 导出新增的reducer
  setActiveGraphId, // Add setActiveGraphId to exports
  setActiveAppUri, // Add setActiveAppUri to exports
} = globalSlice.actions;

export { initializeGraphData, fetchGraphDetails };

export default globalSlice.reducer;
