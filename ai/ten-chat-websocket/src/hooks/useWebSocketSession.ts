import React, { useRef } from "react";
import { webSocketManager } from "@/manager/websocket/websocket";
import { WebSocketConnectionState, SessionConnectionState, Message, CommandResult, CommandType, MessageType } from "@/types/websocket";
import { MESSAGE_CONSTANTS } from '@/common/constant';
import testWebsocketEchoGraph from "../../public/test_websocket_echo_graph.json"; // Import the graph JSON
import { useAppDispatch } from "@/common/hooks"; // Import useAppDispatch
import { setWebsocketConnectionState } from "@/store/reducers/global"; // Import setWebsocketConnectionState

interface UseWebSocketSessionResult {
  isConnected: boolean;
  sessionState: SessionConnectionState;
  defaultLocation: { app_uri: string; graph_id: string; extension_name: string };
  startSession: () => void; // Added startSession to the interface
}

export function useWebSocketSession(): UseWebSocketSessionResult {
  const dispatch = useAppDispatch(); // Get dispatch function
  const [isConnected, setIsConnected] = React.useState(() => {
    const initialState = webSocketManager.getConnectionState() === WebSocketConnectionState.OPEN;
    console.log('useWebSocketSession: Initial isConnected state:', initialState);
    return initialState;
  });

  const [sessionState, setSessionState] = React.useState<SessionConnectionState>(SessionConnectionState.IDLE);
  const sessionStateRef = useRef(sessionState); // New: useRef to store the latest sessionState

  const defaultLocation = {
    app_uri: "mock_front://test_app",
    graph_id: "test-websocket-echo-graph",
    extension_name: MESSAGE_CONSTANTS.SYS_EXTENSION_NAME,
  };

  // Keep the ref always up-to-date with the latest sessionState
  React.useEffect(() => {
    sessionStateRef.current = sessionState;
    console.log('useWebSocketSession: sessionStateRef updated to:', sessionStateRef.current);
  }, [sessionState]);

  // New: Function to start the session explicitly
  const startSession = React.useCallback(() => {
    if (isConnected && sessionStateRef.current === SessionConnectionState.IDLE) {
      console.log('useWebSocketSession: Explicitly starting session. Sending START_GRAPH command.');
      const graphDefinition = {
        graph_name: defaultLocation.graph_id,
        graph_id: defaultLocation.graph_id,
        app_uri: defaultLocation.app_uri,
        nodes: testWebsocketEchoGraph.graph.nodes,
        connections: testWebsocketEchoGraph.graph.connections,
      };
      webSocketManager.sendCommand(CommandType.START_GRAPH, defaultLocation, [], {
        graph_json: JSON.stringify(graphDefinition),
      });
      setSessionState(SessionConnectionState.CONNECTING_SESSION);
    } else {
      console.warn('useWebSocketSession: Cannot start session. isConnected:', isConnected, 'sessionState:', sessionStateRef.current);
    }
  }, [isConnected, defaultLocation]); // defaultLocation is a dependency because it's used in sendCommand

  React.useEffect(() => {
    console.log('useWebSocketSession: useEffect for WebSocket lifecycle and session state triggered');

    const handleConnectionStateChange = (state: WebSocketConnectionState) => {
      console.log('useWebSocketSession: WebSocket connection state changed to:', state);
      setIsConnected(state === WebSocketConnectionState.OPEN);
      dispatch(setWebsocketConnectionState(state)); // Update Redux store
      if (state === WebSocketConnectionState.CLOSED || state === WebSocketConnectionState.CLOSING) {
        setSessionState(SessionConnectionState.IDLE);
        console.log('useWebSocketSession: WebSocket disconnected, session state reset to IDLE.');
      } 
      // Removed automatic START_GRAPH command sending when connection is OPEN
      // Session state will remain IDLE until a command like START_GRAPH is explicitly sent.
    };

    const handleCmdResult = (rawMessage: Message) => {
      const message = rawMessage as CommandResult;
      console.log('useWebSocketSession: Received command result:', message, 'Message Name:', message.name, 'Message Type:', message.type, 'Received Original Cmd Name:', message.original_cmd_name);
      
      console.log(`useWebSocketSession: Checking condition - message.type: ${message.type}, MessageType.CMD_RESULT: ${MessageType.CMD_RESULT}, message.original_cmd_name: ${message.original_cmd_name}, CommandType.START_GRAPH: ${CommandType.START_GRAPH}. Current sessionStateRef: ${sessionStateRef.current}`);
      // Only process CMD_RESULT if it matches CommandType.START_GRAPH by original_cmd_name
      if (message.type === MessageType.CMD_RESULT && message.original_cmd_name === CommandType.START_GRAPH) {
        console.log('useWebSocketSession: CMD_RESULT for START_GRAPH matched. Attempting to update sessionState.');
        setSessionState((prevSessionState) => {
          console.log('useWebSocketSession: Inside setSessionState callback. Previous state:', prevSessionState, 'New state:', message.success ? 'SESSION_ACTIVE' : 'SESSION_FAILED');
          return message.success ? SessionConnectionState.SESSION_ACTIVE : SessionConnectionState.SESSION_FAILED;
        });
        console.log('useWebSocketSession: setSessionState (updated based on success) called. CommandResult success:', message.success);
      } else if (message.type === MessageType.CMD_RESULT) {
          console.warn('useWebSocketSession: Received CMD_RESULT for unknown or non-START_GRAPH command, ignoring.', message);
      }
    };

    const handleCommandSend = (commandName: CommandType, properties: Record<string, any>) => {
      // This callback is for observing commands sent, not for sending them.
      // The START_GRAPH command is now initiated when the WebSocket connects and session is idle.
      console.log('useWebSocketSession: Command sent from elsewhere:', commandName, 'properties:', properties);
      // No longer automatically setting CONNECTING_SESSION here, as it's set where command is initiated.
    };

    webSocketManager.onConnectionStateChange(handleConnectionStateChange);
    webSocketManager.onMessage(MessageType.CMD_RESULT, handleCmdResult);
    webSocketManager.onCommandSend(handleCommandSend);

    webSocketManager.connect().catch(error => {
      console.error('useWebSocketSession: WebSocket 连接失败:', error);
    });

    return () => {
      console.log('useWebSocketSession: Cleaning up WebSocket connection and session listeners');
      webSocketManager.disconnect();
      webSocketManager.offConnectionStateChange(handleConnectionStateChange);
      webSocketManager.offCommandSend(handleCommandSend);
      webSocketManager.offMessage(MessageType.CMD_RESULT, handleCmdResult); // Use offMessage here
    };
  }, [dispatch]); // Add dispatch to dependency array

  return { isConnected, sessionState, defaultLocation, startSession };
}
