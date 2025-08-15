import React, { useRef, useEffect, useCallback, useMemo, useState } from "react";
import { webSocketManager } from "@/manager/websocket/websocket";
import { WebSocketConnectionState, SessionConnectionState, Message, CommandResult, CommandType, MessageType, Location } from "@/types/websocket";
import { MESSAGE_CONSTANTS } from '@/common/constant';
import { useAppDispatch, useAppSelector } from "@/common/hooks";
import { setWebsocketConnectionState, setAgentConnected, setSelectedGraphId, setActiveGraphId, setActiveAppUri } from "@/store/reducers/global"; // Import setActiveAppUri
import { Graph } from "@/common/graph";
import { toast } from 'sonner';
import { RootState } from "@/store";
import { useAgentSettings } from "./useAgentSettings"; // Import useAgentSettings

const FRONTEND_APP_URI = "mock_front://test_app"; // Define fixed frontend URI

interface UseWebSocketSessionResult {
  isConnected: boolean;
  sessionState: SessionConnectionState;
  startSession: () => Promise<void>;
  stopSession: () => Promise<void>;
  sendMessage: (name: string, messageContent: string) => void; // Changed message type to string
  sendCommand: (commandType: CommandType, srcLoc?: Location, destLocs?: Location[], properties?: Record<string, any>) => void; // Make srcLoc and destLocs optional
  defaultLocation: Location;
  activeAppUri: string; // Add activeAppUri
  activeGraphId: string; // Add activeGraphId
}

export const useWebSocketSession = (): UseWebSocketSessionResult => {
  const dispatch = useAppDispatch();
  const isConnected = useAppSelector((state: RootState) => state.global.agentConnected);
  const websocketConnectionState = useAppSelector((state: RootState) => state.global.websocketConnectionState);
  const selectedGraphId = useAppSelector((state: RootState) => state.global.selectedGraphId);
  const graphMap = useAppSelector((state: RootState) => state.global.graphMap);
  const activeGraphId = useAppSelector((state: RootState) => state.global.activeGraphId);
  const activeAppUri = useAppSelector((state: RootState) => state.global.activeAppUri); // Get activeAppUri from Redux
  const selectedGraph = selectedGraphId ? graphMap[selectedGraphId] : null;

  // 获取设置中的属性
  const { agentSettings } = useAgentSettings(); // Get agentSettings using the hook
  const options = useAppSelector((state: RootState) => state.global.options);

  // console.log(`useWebSocketSession: Render - selectedGraphId: ${selectedGraphId}, activeGraphId: ${activeGraphId}, activeAppUri: ${activeAppUri}`);

  const sessionStateRef = useRef<SessionConnectionState>(SessionConnectionState.IDLE);
  const [sessionState, setSessionState] = useState<SessionConnectionState>(SessionConnectionState.IDLE);

  // Default location for commands (src_loc)
  const defaultLocation: Location = useMemo(() => ({
    app_uri: FRONTEND_APP_URI, // Frontend fixed URI for src_loc
    graph_id: activeGraphId || selectedGraphId || "",
    extension_name: MESSAGE_CONSTANTS.SYS_EXTENSION_NAME,
  }), [activeGraphId, selectedGraphId]); // FRONTEND_APP_URI is constant, no need in dependency array

  const handleConnectionStateChange = useCallback((newState: WebSocketConnectionState) => {
    console.log("useWebSocketSession: WebSocket connection state changed to:", newState);
    dispatch(setWebsocketConnectionState(newState));
    if (newState === WebSocketConnectionState.CLOSED) {
      dispatch(setAgentConnected(false));
      dispatch(setActiveGraphId("")); // Clear activeGraphId on disconnect
      dispatch(setActiveAppUri("")); // Clear activeAppUri on disconnect
      // webSocketManager.setManualDisconnectFlag(false); // Removed: Flag is reset by WebSocketManager's handleReconnect
      setSessionState(SessionConnectionState.IDLE);
      sessionStateRef.current = SessionConnectionState.IDLE;
      console.log("useWebSocketSession: WebSocket disconnected, session state reset to IDLE.");
    }
  }, [dispatch]);

  const handleCommandResult = useCallback((message: Message) => {
    if (message.type !== MessageType.CMD_RESULT) {
      console.warn("useWebSocketSession: Received non-CMD_RESULT message, ignoring.", message);
      return;
    }
    const cmdResult = message as CommandResult;

    console.log(`useWebSocketSession: Received CMD_RESULT for ${cmdResult.original_cmd_name}: success=${cmdResult.success}, message=${cmdResult.errorMessage || cmdResult.error}`);
    if (cmdResult.original_cmd_name === CommandType.START_GRAPH) {
      if (cmdResult.success) {
        setSessionState(SessionConnectionState.SESSION_ACTIVE);
        sessionStateRef.current = SessionConnectionState.SESSION_ACTIVE;
        dispatch(setAgentConnected(true));
        toast.success("AI 已启动！"); // Changed message
        if (cmdResult.properties && cmdResult.properties.graph_id) {
          dispatch(setActiveGraphId(cmdResult.properties.graph_id));
          // console.log("useWebSocketSession: Active Graph ID set to", cmdResult.properties.graph_id);
        }
        if (cmdResult.properties && cmdResult.properties.app_uri) { // Extract app_uri
          dispatch(setActiveAppUri(cmdResult.properties.app_uri));
          // console.log("useWebSocketSession: Active App URI set to", cmdResult.properties.app_uri);
        }
      } else {
        setSessionState(SessionConnectionState.IDLE);
        sessionStateRef.current = SessionConnectionState.IDLE;
        dispatch(setAgentConnected(false));
        toast.error(`AI 启动失败: ${cmdResult.errorMessage || cmdResult.error}`); // Changed message
      }
    } else if (cmdResult.original_cmd_name === CommandType.STOP_GRAPH) {
      if (cmdResult.success) {
        setSessionState(SessionConnectionState.IDLE);
        sessionStateRef.current = SessionConnectionState.IDLE;
        dispatch(setAgentConnected(false));
        dispatch(setActiveGraphId(""));
        dispatch(setActiveAppUri("")); // Clear activeAppUri on successful stop
        toast.info("AI 已停止。"); // Changed message
      } else {
        toast.error(`AI 停止失败: ${cmdResult.errorMessage || cmdResult.error}`); // Changed message
      }
    }
  }, [dispatch]);

  useEffect(() => {
    webSocketManager.onConnectionStateChange(handleConnectionStateChange);
    webSocketManager.onMessage(MessageType.CMD_RESULT, handleCommandResult);

    return () => {
      webSocketManager.offConnectionStateChange(handleConnectionStateChange);
      webSocketManager.offMessage(MessageType.CMD_RESULT, handleCommandResult);
      console.log("useWebSocketSession: Cleaning up WebSocket connection and session listeners");
      setSessionState(SessionConnectionState.IDLE);
      sessionStateRef.current = SessionConnectionState.IDLE;
    };
  }, [handleConnectionStateChange, handleCommandResult]);

  const startSession = useCallback(async () => {
    if (!selectedGraph) {
      toast.error("请先选择一个图");
      return;
    }

    const currentWebsocketConnectionState = webSocketManager.getConnectionState();
    console.log(`useWebSocketSession: Attempting to start session. websocketConnectionState: ${currentWebsocketConnectionState} sessionState: ${sessionStateRef.current}`);
    if (currentWebsocketConnectionState !== WebSocketConnectionState.OPEN || sessionStateRef.current !== SessionConnectionState.IDLE) {
      toast.error("无法启动 AI：WebSocket 未连接或 AI 已激活"); // Changed message
      return;
    }

    // `graphDefinition` is still needed for predefined_graph_name in properties
    const graphDefinition = {
      graph_name: selectedGraph.name,
      graph_id: selectedGraph.uuid,
      app_uri: defaultLocation.app_uri, // Frontend app_uri for the graph definition
      nodes: selectedGraph.nodes,
      connections: selectedGraph.connections,
    };

    webSocketManager.sendCommand(CommandType.START_GRAPH, {
      ...defaultLocation, // Use defaultLocation for src_loc of START_GRAPH command
      graph_id: selectedGraph.uuid, // Use selected graph's UUID for START_GRAPH
    }, [], { // dest_locs for START_GRAPH can be empty or handled by backend implicitly
      predefined_graph_name: selectedGraph.name,
      // 将智能体设置和 options 打平放入 properties
      ...agentSettings, // Spread agentSettings here
      ...options,
    });
    setSessionState(SessionConnectionState.CONNECTING_SESSION);

  }, [selectedGraph, defaultLocation, dispatch]);

  const stopSession = useCallback(async () => {
    if (sessionStateRef.current === SessionConnectionState.SESSION_ACTIVE) {
      console.log("useWebSocketSession: Explicitly stopping AI. Sending STOP_GRAPH command."); // Changed message
      
      console.log("useWebSocketSession: About to set _isManualDisconnect to true.");
      webSocketManager.setManualDisconnectFlag(true); // Set manual disconnect flag BEFORE sending STOP_GRAPH
      console.log("useWebSocketSession: _isManualDisconnect set to true.");

      const destLocsForStop: Location[] = activeAppUri ? [{
        app_uri: activeAppUri,
        graph_id: activeGraphId,
      }] : [];

      webSocketManager.sendCommand(CommandType.STOP_GRAPH, defaultLocation, destLocsForStop, { location_uri: activeAppUri });
      setSessionState(SessionConnectionState.IDLE);
    } else {
      console.warn("useWebSocketSession: Cannot stop AI, not currently connected."); // Changed message
    }
  }, [defaultLocation, dispatch, activeAppUri, activeGraphId]); // Added activeAppUri, activeGraphId to dependencies

  const sendMessage = useCallback((name: string, messageContent: string) => {
    if (websocketConnectionState === WebSocketConnectionState.OPEN) {
      // console.log(`sendMessage: Using name: ${name}, activeAppUri: ${activeAppUri}, activeGraphId: ${activeGraphId}`);
      const message: Message = {
        id: Date.now().toString() + Math.random().toString().substring(2, 8),
        type: MessageType.DATA,
        src_loc: defaultLocation,
        dest_locs: activeAppUri ? [{
          app_uri: activeAppUri,
          graph_id: activeGraphId,
          extension_name: MESSAGE_CONSTANTS.SYS_EXTENSION_NAME,
        }] : [],
        name: name, // Use passed name
        properties: { text: messageContent, is_final: true }, // 重新添加 is_final: true
        timestamp: Date.now(),
      };
      webSocketManager.sendMessage(message);
    } else {
      console.warn("Cannot send message, WebSocket is not open.");
      toast.error("WebSocket 未连接，无法发送消息。");
    }
  }, [websocketConnectionState, activeAppUri, activeGraphId, defaultLocation]);

  const sendCommand = useCallback((commandType: CommandType, srcLocOverride?: Location, destLocsOverride?: Location[], properties?: Record<string, any>) => {
    if (websocketConnectionState === WebSocketConnectionState.OPEN) {
      const finalSrcLoc = srcLocOverride || defaultLocation;

      let finalDestLocs: Location[] = destLocsOverride || [];
      // If no explicit destLocs were provided and an activeAppUri exists, construct a default dest_loc
      if (!destLocsOverride && activeAppUri) {
        finalDestLocs = [{
          app_uri: activeAppUri,
          graph_id: activeGraphId,
          extension_name: MESSAGE_CONSTANTS.SYS_EXTENSION_NAME,
        }];
      }

      webSocketManager.sendCommand(commandType, finalSrcLoc, finalDestLocs, { ...properties, graph_id: activeGraphId });
    } else {
      console.warn("Cannot send command, WebSocket is not open.");
      toast.error("WebSocket 未连接，无法发送命令。");
    }
  }, [websocketConnectionState, activeAppUri, activeGraphId, defaultLocation]);

  return {
    isConnected: isConnected,
    sessionState,
    startSession,
    stopSession,
    sendMessage,
    sendCommand,
    defaultLocation,
    activeAppUri,
    activeGraphId,
  };
};
