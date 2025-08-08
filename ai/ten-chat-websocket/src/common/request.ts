import { genUUID } from "./utils";
import { IAgentEnv, Language } from "@/types";
import axios from "axios";
import {
  AddonDef,
  Connection,
  Graph,
  Node,
  ProtocolLabel,
} from "./graph";
import { isEditModeOn } from "./constant";

interface ApiResponse<T> {
  code: string;
  msg: string;
  data: T;
}

interface IStartServiceResponse {
  message: string;
}

interface IStopServiceResponse {
  message: string;
}

interface IDocumentListResponse {
  documents: unknown[];
}

interface IPingResponse {
  message: string;
}

interface IGraphNodeApiItem {
  name: string;
  addon: string;
  extension_group: string;
  app: string;
  property?: Record<string, unknown>;
}

interface IGraphNodeApiResponse {
  nodes: IGraphNodeApiItem[];
}

interface IDestinationApiItem {
  app: string;
  extension: string;
  extension_group?: string;
  msgConversion?: IMsgConversionApiItem;
}

interface IMsgConversionRuleApiItem {
  path: string;
  conversionMode: string;
  value?: string;
  originalPath?: string;
}

interface IMsgConversionApiItem {
  type: string;
  rules: IMsgConversionRuleApiItem[];
  keepOriginal?: boolean;
}

interface ICommandApiItem {
  name: string;
  dest: IDestinationApiItem[];
}

interface IDataApiItem {
  name: string;
  dest: IDestinationApiItem[];
}

interface IAudioFrameApiItem {
  name: string;
  dest: IDestinationApiItem[];
}

interface IVideoFrameApiItem {
  name: string;
  dest: IDestinationApiItem[];
}

interface IGraphConnectionApiItem {
  app: string;
  extension: string;
  cmd?: ICommandApiItem[];
  data?: IDataApiItem[];
  audio_frame?: IAudioFrameApiItem[];
  video_frame?: IVideoFrameApiItem[];
}

interface IGraphConnectionApiResponse {
  connections: IGraphConnectionApiItem[];
}

interface IGraphApiItem {
  name: string;
  uuid: string;
  auto_start: boolean;
  nodes?: unknown[];
  connections?: unknown[];
}

interface IGraphApiResponse {
  graphs: IGraphApiItem[];
}

interface IAddonModuleDefaultProperty {
  addon: string;
  property: unknown;
}

interface IAddonModuleDefaultPropertiesResponse {
  data: IAddonModuleDefaultProperty[];
}

interface IDefaultPropertyResponse {
  property: unknown;
}

interface StartRequestConfig {
  channel: string;
  userId: number;
  graphName: string;
  language: Language;
  voiceType: "male" | "female";
  token?: string;
  properties?: Record<string, unknown>;
  envProperties?: IAgentEnv;
}

export const apiStartService = async (
  config: StartRequestConfig,
): Promise<IStartServiceResponse> => {
  console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiStartService: Initiating request with config:`, config);
  const url = `/api/agents/start`;
  const {
    channel,
    userId,
    graphName,
    language,
    voiceType,
    token,
    properties,
    envProperties,
  } = config;
  const data = {
    request_id: genUUID(),
    channel_name: channel,
    user_uid: userId,
    graph_name: graphName,
    language,
    voice_type: voiceType,
    token: token ?? undefined,
    properties: properties ?? undefined,
    env_properties: envProperties ?? undefined,
  };
  try {
    const resp = await axios.post<ApiResponse<IStartServiceResponse>>(url, data);
  if (resp.data.code !== "0") {
      console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiStartService: API returned error code ${resp.data.code}. Message: ${resp.data.msg}`, resp.data);
    throw new Error(resp.data.msg);
  }
    console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiStartService: Request successful. Response:`, resp.data.data);
  return resp.data.data;
  } catch (error: any) {
    console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiStartService: Request failed. Error: ${error.message || 'Unknown error'}`, error);
    throw error; // Re-throw the error after logging
  }
};

export const apiStopService = async (channel: string): Promise<IStopServiceResponse> => {
  console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiStopService: Initiating request for channel: ${channel}`);
  const url = `/api/agents/stop`;
  const data = {
    request_id: genUUID(),
    channel_name: channel,
  };
  try {
    const resp = await axios.post<ApiResponse<IStopServiceResponse>>(url, data);
  if (resp.data.code !== "0") {
      console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiStopService: API returned error code ${resp.data.code}. Message: ${resp.data.msg}`, resp.data);
    throw new Error(resp.data.msg);
  }
    console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiStopService: Request successful. Response:`, resp.data.data);
  return resp.data.data;
  } catch (error: unknown) {
    console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiStopService: Request failed. Error: ${(error as Error).message || 'Unknown error'}`, error);
    throw error; // Re-throw the error after logging
  }
};

export const apiGetDocumentList = async (): Promise<IDocumentListResponse> => {
  console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiGetDocumentList: Initiating request.`);
  // the request will be rewrite at middleware.tsx to send to $AGENT_SERVER_URL
  const url = `/api/vector/document/preset/list`;
  try {
    const resp = await axios.get<ApiResponse<IDocumentListResponse>>(url);
  if (resp.data.code !== "0") {
      console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiGetDocumentList: API returned error code ${resp.data.code}. Message: ${resp.data.msg}`, resp.data);
    throw new Error(resp.data.msg);
  }
    console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiGetDocumentList: Request successful. Response:`, resp.data.data);
  return resp.data.data;
  } catch (error: any) {
    console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiGetDocumentList: Request failed. Error: ${error.message || 'Unknown error'}`, error);
    throw error; // Re-throw the error after logging
  }
};

export const apiUpdateDocument = async (options: {
  channel: string;
  collection: string;
  fileName: string;
}) => {
  console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiUpdateDocument: Initiating request with options:`, options);
  const url = `/api/vector/document/update`;
  const { channel, collection, fileName } = options;
  const data = {
    request_id: genUUID(),
    channel_name: channel,
    collection: collection,
    file_name: fileName,
  };
  try {
    const resp = await axios.post<ApiResponse<unknown>>(url, data);
  if (resp.data.code !== "0") {
      console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiUpdateDocument: API returned error code ${resp.data.code}. Message: ${resp.data.msg}`, resp.data);
    throw new Error(resp.data.msg);
  }
    console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiUpdateDocument: Request successful. Response:`, resp.data.data);
  return resp.data.data;
  } catch (error: any) {
    console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiUpdateDocument: Request failed. Error: ${error.message || 'Unknown error'}`, error);
    throw error; // Re-throw the error after logging
  }
};

// ping/pong
export const apiPing = async (channel: string): Promise<IPingResponse> => {
  console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiPing: Initiating request for channel: ${channel}`);
  const url = `/api/agents/ping`;
  const data = {
    request_id: genUUID(),
    channel_name: channel,
  };
  try {
    const resp = await axios.post<ApiResponse<IPingResponse>>(url, data);
  if (resp.data.code !== "0") {
      console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiPing: API returned error code ${resp.data.code}. Message: ${resp.data.msg}`, resp.data);
    throw new Error(resp.data.msg);
  }
    console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiPing: Request successful. Response:`, resp.data.data);
  return resp.data.data;
  } catch (error: any) {
    console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiPing: Request failed. Error: ${error.message || 'Unknown error'}`, error);
    throw error; // Re-throw the error after logging
  }
};

export const apiFetchAddonsExtensions = async (): Promise<
  AddonDef[]
> => {
  console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiFetchAddonsExtensions: Initiating request.`);
  try {
    const resp = await axios.post<ApiResponse<AddonDef[]>>(`/api/dev/v1/apps/addons`, {
    base_dir: "/app/agents",
  });
    if (resp.data.code !== "0") {
      console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiFetchAddonsExtensions: API returned error code ${resp.data.code}. Message: ${resp.data.msg}`, resp.data);
      throw new Error(resp.data.msg);
    }
    console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiFetchAddonsExtensions: Request successful. Fetched ${resp.data.data.length} addons.`);
    return resp.data.data;
  } catch (error: any) {
    console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiFetchAddonsExtensions: Request failed. Error: ${error.message || 'Unknown error'}`, error);
    throw error; // Re-throw the error after logging
  }
};

export const apiCheckCompatibleMessages = async (payload: {
  app: string;
  graph: string;
  extension_group: string;
  extension: string;
  msg_type: string;
  msg_direction: string;
  msg_name: string;
}): Promise<unknown> => {
  console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiCheckCompatibleMessages: Initiating request with payload:`, payload);
  try {
    let resp: unknown = await axios.post(`/api/dev/v1/messages/compatible`, payload);
    resp = (resp as ApiResponse<unknown>).data;
    if ((resp as ApiResponse<unknown>).code !== "0") {
        console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiCheckCompatibleMessages: API returned error code ${(resp as ApiResponse<unknown>).code}. Message: ${(resp as ApiResponse<unknown>).msg}`, resp);
        throw new Error((resp as ApiResponse<unknown>).msg);
    }
    console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiCheckCompatibleMessages: Request successful. Response:`, resp);
    return resp;
  } catch (error: any) {
    console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiCheckCompatibleMessages: Request failed. Error: ${error.message || 'Unknown error'}`, error);
    throw error; // Re-throw the error after logging
  }
};

export const apiFetchGraphs = async (): Promise<Graph[]> => {
  console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiFetchGraphs: Initiating request.`);
  try {
  if (isEditModeOn) {
      const resp = await axios.post<ApiResponse<IGraphApiResponse>>(`/api/dev/v1/graphs`, {});
      if (resp.data.code !== "0") {
        console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiFetchGraphs (Edit Mode): API returned error code ${resp.data.code}. Message: ${resp.data.msg}`, resp.data);
        throw new Error(resp.data.msg);
      }
      console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiFetchGraphs (Edit Mode): Request successful. Fetched ${resp.data.data.graphs.length} graphs.`);
    return resp.data.data.graphs.map((graph) => ({
      name: graph.name,
      uuid: graph.uuid,
      autoStart: graph.auto_start,
      nodes: [],
      connections: [],
    }));
  } else {
      const resp = await axios.get<ApiResponse<IGraphApiResponse>>(`/api/agents/graphs`);
      if (resp.data.code !== "0") {
        console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiFetchGraphs (Production Mode): API returned error code ${resp.data.code}. Message: ${resp.data.msg}`, resp.data);
        throw new Error(resp.data.msg);
      }
      console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiFetchGraphs (Production Mode): Request successful. Fetched ${resp.data.data.graphs.length} graphs.`);
    return resp.data.data.graphs.map((graph) => ({
      name: graph.name,
      uuid: graph.uuid,
      autoStart: graph.auto_start,
      nodes: [],
      connections: [],
    }));
    }
  } catch (error: any) {
    console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiFetchGraphs: Request failed. Error: ${error.message || 'Unknown error'}`, error);
    throw error; // Re-throw the error after logging
  }
};

export const apiLoadApp = async (): Promise<unknown> => {
  console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiLoadApp: Initiating request.`);
  try {
    const resp = await axios.post<ApiResponse<unknown>>(`/api/dev/v1/apps/load`, {
    base_dir: "/app/agents",
  });
    if (resp.data.code !== "0") {
      console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiLoadApp: API returned error code ${resp.data.code}. Message: ${resp.data.msg}`, resp.data);
      throw new Error(resp.data.msg);
    }
    console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiLoadApp: Request successful. Response:`, resp.data.data);
    return resp.data.data;
  } catch (error: any) {
    console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiLoadApp: Request failed. Error: ${error.message || 'Unknown error'}`, error);
    throw error; // Re-throw the error after logging
  }
};

export const apiFetchGraphNodes = async (graphId: string): Promise<Node[]> => {
  console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiFetchGraphNodes: Initiating request for graphId: ${graphId}`);
  try {
    const resp = await axios.post<ApiResponse<IGraphNodeApiResponse>>(`/api/dev/v1/graphs/nodes`, {
    graph_id: graphId,
  });
    if (resp.data.code !== "0") {
      console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiFetchGraphNodes: API returned error code ${resp.data.code}. Message: ${resp.data.msg}`, resp.data);
      throw new Error(resp.data.msg);
    }
    console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiFetchGraphNodes: Request successful. Fetched ${resp.data.data.nodes.length} nodes.`);
    return resp.data.data.nodes.map((node) => ({
      name: node.name,
      addon: node.addon,
      extensionGroup: node.extension_group,
      app: node.app,
      property: node.property || {},
    }));
  } catch (error: any) {
    console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiFetchGraphNodes: Request failed. Error: ${error.message || 'Unknown error'}`, error);
    throw error; // Re-throw the error after logging
  }
};

export const apiFetchGraphConnections = async (
  graphId: string,
): Promise<Connection[]> => {
  console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiFetchGraphConnections: Initiating request for graphId: ${graphId}`);
  try {
    const resp = await axios.post<ApiResponse<IGraphConnectionApiResponse>>(`/api/dev/v1/graphs/connections`, {
    graph_id: graphId,
  });
    if (resp.data.code !== "0") {
      console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiFetchGraphConnections: API returned error code ${resp.data.code}. Message: ${resp.data.msg}`, resp.data);
      throw new Error(resp.data.msg);
    }
    console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiFetchGraphConnections: Request successful. Fetched ${resp.data.data.connections.length} connections.`);
    return resp.data.data.connections.map((connection) => ({
      app: connection.app,
      extension: connection.extension,
      cmd: connection.cmd?.map((cmd) => ({
        name: cmd.name,
        dest: cmd.dest.map((dest) => ({
          app: dest.app,
          extension: dest.extension,
          msgConversion: dest.msgConversion
            ? {
              type: dest.msgConversion.type,
              rules: dest.msgConversion.rules.map((rule) => ({
                path: rule.path,
                conversionMode: rule.conversionMode,
                value: rule.value,
                originalPath: rule.originalPath,
              })),
              keepOriginal: dest.msgConversion.keepOriginal,
          }
          : undefined,
      })),
    })),
      data: connection.data?.map((data) => ({
        name: data.name,
        dest: data.dest.map((dest) => ({
          app: dest.app,
          extension: dest.extension,
          msgConversion: dest.msgConversion
            ? {
              type: dest.msgConversion.type,
              rules: dest.msgConversion.rules.map((rule) => ({
                path: rule.path,
                conversionMode: rule.conversionMode,
                value: rule.value,
                originalPath: rule.originalPath,
              })),
              keepOriginal: dest.msgConversion.keepOriginal,
          }
          : undefined,
      })),
    })),
      audio_frame: connection.audio_frame?.map((audioFrame) => ({
        name: audioFrame.name,
        dest: audioFrame.dest.map((dest) => ({
          app: dest.app,
          extension: dest.extension,
          msgConversion: dest.msgConversion
            ? {
              type: dest.msgConversion.type,
              rules: dest.msgConversion.rules.map((rule) => ({
                path: rule.path,
                conversionMode: rule.conversionMode,
                value: rule.value,
                originalPath: rule.originalPath,
              })),
              keepOriginal: dest.msgConversion.keepOriginal,
          }
          : undefined,
      })),
    })),
      video_frame: connection.video_frame?.map((videoFrame) => ({
        name: videoFrame.name,
        dest: videoFrame.dest.map((dest) => ({
          app: dest.app,
          extension: dest.extension,
          msgConversion: dest.msgConversion
            ? {
              type: dest.msgConversion.type,
              rules: dest.msgConversion.rules.map((rule) => ({
                path: rule.path,
                conversionMode: rule.conversionMode,
                value: rule.value,
                originalPath: rule.originalPath,
              })),
              keepOriginal: dest.msgConversion.keepOriginal,
          }
          : undefined,
      })),
    })),
  }));
  } catch (error: any) {
    console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiFetchGraphConnections: Request failed. Error: ${error.message || 'Unknown error'}`, error);
    throw error; // Re-throw the error after logging
  }
};

export const apiGetDefaultProperty = async (module: string): Promise<unknown> => {
  console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiGetDefaultProperty: Initiating request for module: ${module}`);
  try {
    const resp = await axios.post<ApiResponse<IDefaultPropertyResponse>>(`/api/dev/v1/extensions/property/get`, {
    addon_name: module,
    app_base_dir: "/app/agents",
  });
    if (resp.data.code !== "0") {
      console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiGetDefaultProperty: API returned error code ${resp.data.code}. Message: ${resp.data.msg}`, resp.data);
      throw new Error(resp.data.msg);
    }
    console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiGetDefaultProperty: Request successful. Response:`, resp.data.data.property);
    return resp.data.data.property;
  } catch (error: any) {
    console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiGetDefaultProperty: Request failed. Error: ${error.message || 'Unknown error'}`, error);
    throw error; // Re-throw the error after logging
  }
};

export const apiAddNode = async (
  graphId: string,
  name: string,
  module: string,
  properties: Record<string, unknown>,
) => {
  console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiAddNode: Initiating request to add node to graph: ${graphId} with name: ${name}, module: ${module}.`);
  try {
    const resp: unknown = await axios.post(`/api/dev/v1/graphs/nodes/add`, {
    graph_id: graphId,
    name,
    addon: module,
    property: properties,
  });
    if ((resp as ApiResponse<unknown>).code !== "0") {
        console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiAddNode: API returned error code ${(resp as ApiResponse<unknown>).code}. Message: ${(resp as ApiResponse<unknown>).msg}`, resp);
        throw new Error((resp as ApiResponse<unknown>).msg);
    }
    console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiAddNode: Request successful. Added node to graph: ${graphId}. Response:`, resp);
    return (resp as ApiResponse<unknown>).data.data;
  } catch (error: any) {
    console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiAddNode: Request failed. Error: ${error.message || 'Unknown error'}`, error);
    throw error; // Re-throw the error after logging
  }
};

export const apiReplaceNodeModule = async (
  graphId: string,
  name: string,
  module: string,
  properties: Record<string, unknown>,
) => {
  console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiReplaceNodeModule: Initiating request to replace node module in graph: ${graphId}, node: ${name}.`);
  try {
    const resp: unknown = await axios.post(`/api/dev/v1/graphs/nodes/replace`, {
    graph_id: graphId,
    name,
    addon: module,
    property: properties,
  });
    if ((resp as ApiResponse<unknown>).code !== "0") {
        console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiReplaceNodeModule: API returned error code ${(resp as ApiResponse<unknown>).code}. Message: ${(resp as ApiResponse<unknown>).msg}`, resp);
        throw new Error((resp as ApiResponse<unknown>).msg);
    }
    console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiReplaceNodeModule: Request successful. Replaced module for node: ${name} in graph: ${graphId}. Response:`, resp);
    return (resp as ApiResponse<unknown>).data.data;
  } catch (error: any) {
    console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiReplaceNodeModule: Request failed. Error: ${error.message || 'Unknown error'}`, error);
    throw error; // Re-throw the error after logging
  }
};

export const apiRemoveNode = async (
  graphId: string,
  name: string,
  module: string,
) => {
  console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiRemoveNode: Initiating request to remove node from graph: ${graphId}, node: ${name}.`);
  try {
    const resp: unknown = await axios.post(`/api/dev/v1/graphs/nodes/delete`, {
    graph_id: graphId,
    name,
    addon: module,
  });
    if ((resp as ApiResponse<unknown>).code !== "0") {
        console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiRemoveNode: API returned error code ${(resp as ApiResponse<unknown>).code}. Message: ${(resp as ApiResponse<unknown>).msg}`, resp);
        throw new Error((resp as ApiResponse<unknown>).msg);
    }
    console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiRemoveNode: Request successful. Removed node: ${name} from graph: ${graphId}. Response:`, resp);
    return (resp as ApiResponse<unknown>).data.data;
  } catch (error: any) {
    console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiRemoveNode: Request failed. Error: ${error.message || 'Unknown error'}`, error);
    throw error; // Re-throw the error after logging
  }
};

export const apiAddConnection = async (
  graphId: string,
  srcExtension: string,
  msgType: ProtocolLabel,
  msgName: string,
  dest_extension: string,
) => {
  console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiAddConnection: Initiating request to add connection to graph: ${graphId}. Source: ${srcExtension}, Dest: ${dest_extension}, MsgType: ${msgType}, MsgName: ${msgName}.`);
  try {
    const resp: unknown = await axios.post(`/api/dev/v1/graphs/connections/add`, {
    graph_id: graphId,
    src_extension: srcExtension,
    msg_type: msgType,
    msg_name: msgName,
    dest_extension: dest_extension,
  });
    if ((resp as ApiResponse<unknown>).code !== "0") {
        console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiAddConnection: API returned error code ${(resp as ApiResponse<unknown>).code}. Message: ${(resp as ApiResponse<unknown>).msg}`, resp);
        throw new Error((resp as ApiResponse<unknown>).msg);
    }
    console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiAddConnection: Request successful. Added connection to graph: ${graphId}. Response:`, resp);
    return (resp as ApiResponse<unknown>).data.data;
  } catch (error: any) {
    console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiAddConnection: Request failed. Error: ${error.message || 'Unknown error'}`, error);
    throw error; // Re-throw the error after logging
  }
};

export const apiRemoveConnection = async (
  graphId: string,
  srcExtension: string,
  msgType: ProtocolLabel,
  msgName: string,
  dest_extension: string,
) => {
  console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiRemoveConnection: Initiating request to remove connection from graph: ${graphId}. Source: ${srcExtension}, Dest: ${dest_extension}, MsgType: ${msgType}, MsgName: ${msgName}.`);
  try {
    const resp: unknown = await axios.post(`/api/dev/v1/graphs/connections/delete`, {
    graph_id: graphId,
    src_extension: srcExtension,
    msg_type: msgType,
    msg_name: msgName,
    dest_extension: dest_extension,
  });
    if ((resp as ApiResponse<unknown>).code !== "0") {
        console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiRemoveConnection: API returned error code ${(resp as ApiResponse<unknown>).code}. Message: ${(resp as ApiResponse<unknown>).msg}`, resp);
        throw new Error((resp as ApiResponse<unknown>).msg);
    }
    console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiRemoveConnection: Request successful. Removed connection from graph: ${graphId}. Response:`, resp);
    return (resp as ApiResponse<unknown>).data.data;
  } catch (error: any) {
    console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiRemoveConnection: Request failed. Error: ${error.message || 'Unknown error'}`, error);
    throw error; // Re-throw the error after logging
  }
};

export const apiUpdateGraph = async (
  graphId: string,
  updates: Partial<Graph>,
) => {
  console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiUpdateGraph: Initiating request to update graph: ${graphId}. Updates:`, updates);
  const { autoStart, nodes, connections } = updates;
  const payload: Record<string, unknown> = {};

  // Map autoStart field
  if (autoStart !== undefined) payload.auto_start = autoStart;

  // Map nodes to the payload
  if (nodes) {
    payload.nodes = nodes.map((node) => ({
      name: node.name,
      addon: node.addon,
      extension_group: node.extensionGroup,
      app: node.app,
      property: node.property,
    }));
  }

  // Map connections to the payload
  if (connections) {
    payload.connections = connections.map((connection) => ({
      app: connection.app,
      extension: connection.extension,
      cmd: connection.cmd?.map((cmd) => ({
        name: cmd.name,
        dest: cmd.dest.map((dest) => ({
          app: dest.app,
          extension: dest.extension,
          msgConversion: dest.msgConversion
            ? {
              type: dest.msgConversion.type,
              rules: dest.msgConversion.rules.map((rule) => ({
                path: rule.path,
                conversionMode: rule.conversionMode,
                value: rule.value,
                originalPath: rule.originalPath,
              })),
              keepOriginal: dest.msgConversion.keepOriginal,
            }
            : undefined,
        })),
      })),
      data: connection.data?.map((data) => ({
        name: data.name,
        dest: data.dest.map((dest) => ({
          app: dest.app,
          extension: dest.extension,
          msgConversion: dest.msgConversion
            ? {
              type: dest.msgConversion.type,
              rules: dest.msgConversion.rules.map((rule) => ({
                path: rule.path,
                conversionMode: rule.conversionMode,
                value: rule.value,
                originalPath: rule.originalPath,
              })),
              keepOriginal: dest.msgConversion.keepOriginal,
            }
            : undefined,
        })),
      })),
      audio_frame: connection.audio_frame?.map((audioFrame) => ({
        name: audioFrame.name,
        dest: audioFrame.dest.map((dest) => ({
          app: dest.app,
          extension: dest.extension,
          msgConversion: dest.msgConversion
            ? {
              type: dest.msgConversion.type,
              rules: dest.msgConversion.rules.map((rule) => ({
                path: rule.path,
                conversionMode: rule.conversionMode,
                value: rule.value,
                originalPath: rule.originalPath,
              })),
              keepOriginal: dest.msgConversion.keepOriginal,
            }
            : undefined,
        })),
      })),
      video_frame: connection.video_frame?.map((videoFrame) => ({
        name: videoFrame.name,
        dest: videoFrame.dest.map((dest) => ({
          app: dest.app,
          extension: dest.extension,
          msgConversion: dest.msgConversion
            ? {
              type: dest.msgConversion.type,
              rules: dest.msgConversion.rules.map((rule) => ({
                path: rule.path,
                conversionMode: rule.conversionMode,
                value: rule.value,
                originalPath: rule.originalPath,
              })),
              keepOriginal: dest.msgConversion.keepOriginal,
            }
            : undefined,
        })),
      })),
    }));
  }

  try {
    let resp = await axios.post<ApiResponse<unknown>>(`/api/dev/v1/graphs/update`, {
    graph_id: graphId,
    nodes: payload.nodes,
    connections: payload.connections,
  });
    resp = resp.data;
    if ((resp as ApiResponse<unknown>).code !== "0") {
        console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiUpdateGraph: API returned error code ${(resp as ApiResponse<unknown>).code}. Message: ${(resp as ApiResponse<unknown>).msg}`, resp);
        throw new Error((resp as ApiResponse<unknown>).msg);
    }
    console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiUpdateGraph: Request successful. Updated graph: ${graphId}. Response:`, resp);
  return resp;
  } catch (error: any) {
    console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiUpdateGraph: Request failed. Error: ${error.message || 'Unknown error'}`, error);
    throw error; // Re-throw the error after logging
  }
};

export const apiFetchAddonModulesDefaultProperties = async (): Promise<
  Record<string, Partial<AddonDef>>
> => {
  console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiFetchAddonModulesDefaultProperties: Initiating request.`);
  try {
    const resp = await axios.get<ApiResponse<IAddonModuleDefaultPropertiesResponse>>(`/api/dev/v1/addons/default-properties`);
    if (resp.data.code !== "0") {
      console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiFetchAddonModulesDefaultProperties: API returned error code ${resp.data.code}. Message: ${resp.data.msg}`, resp.data);
      throw new Error(resp.data.msg);
    }
    const properties = resp.data.data.data;
  const result: Record<string, Partial<AddonDef>> = {};
  for (const property of properties) {
      result[property.addon] = property.property as Partial<AddonDef>;
    }
    console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiFetchAddonModulesDefaultProperties: Request successful. Fetched ${properties.length} default properties.`);
    return result;
  } catch (error: any) {
    console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiFetchAddonModulesDefaultProperties: Request failed. Error: ${error.message || 'Unknown error'}`, error);
    throw error; // Re-throw the error after logging
  }
};

export const apiSaveProperty = async () => {
  console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiSaveProperty: Initiating request.`);
  try {
    let resp = await axios.put<ApiResponse<unknown>>(`/api/dev/v1/property`);
    resp = resp.data;
    if ((resp as ApiResponse<unknown>).code !== "0") {
        console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiSaveProperty: API returned error code ${(resp as ApiResponse<unknown>).code}. Message: ${(resp as ApiResponse<unknown>).msg}`, resp);
        throw new Error((resp as ApiResponse<unknown>).msg);
    }
    console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiSaveProperty: Request successful.`);
  return resp;
  } catch (error: any) {
    console.error(`聪明的开发杭一: [${new Date().toISOString()}] apiSaveProperty: Request failed. Error: ${error.message || 'Unknown error'}`, error);
    throw error; // Re-throw the error after logging
  }
};

export const apiReloadPackage = async () => {
  console.log(`聪明的开发杭一: [${new Date().toISOString()}] apiReloadPackage: Initiating request.`);
  let resp = await axios.post<ApiResponse<unknown>>(`/api/dev/v1/apps/reload`, {
    base_dir: "/app/agents",
  });
  resp = resp.data;
  return resp;
};

export const apiFetchInstalledAddons = async (): Promise<AddonDef[]> => {
  const [modules, defaultProperties] = await Promise.all([
    apiFetchAddonsExtensions(),
    apiFetchAddonModulesDefaultProperties(),
  ]);
  return modules.map((module) => ({
    name: module.name,
    defaultProperty: defaultProperties[module.name],
    api: module.api,
  }));
};

export const apiFetchGraphDetails = async (graph: Graph): Promise<Graph> => {
  const [nodes, connections] = await Promise.all([
    apiFetchGraphNodes(graph.uuid),
    apiFetchGraphConnections(graph.uuid),
  ]);
  return {
    uuid: graph.uuid,
    name: graph.name,
    autoStart: graph.autoStart,
    nodes,
    connections,
  };
};
