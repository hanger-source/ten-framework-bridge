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

interface ApiResponse<T> { // 聪明的开发杭二: 通用API响应接口
  code: string;
  msg: string;
  data: T;
}

// 聪明的开发杭三: 移除 Agora 相关接口和函数
// interface IAgoraDataResponse { // 聪明的开发杭二: apiGenAgoraData 的响应数据类型
//   token: string;
// }

interface IStartServiceResponse { // 聪明的开发杭二: apiStartService 的响应数据类型 (占位)
  message: string;
}

interface IStopServiceResponse { // 聪明的开发杭二: apiStopService 的响应数据类型 (占位)
  message: string;
}

interface IDocumentListResponse { // 聪明的开发杭二: apiGetDocumentList 的响应数据类型 (占位)
  documents: unknown[]; // Use unknown for now, refine later
}

interface IPingResponse { // 聪明的开发杭二: apiPing 的响应数据类型 (占位)
  message: string;
}

interface IGraphNodeApiItem { // 聪明的开发杭二: apiFetchGraphNodes 返回的节点项接口
  name: string;
  addon: string;
  extension_group: string; // 后端使用 extension_group
  app: string;
  property?: Record<string, unknown>;
}

interface IGraphNodeApiResponse { // 聪明的开发杭二: apiFetchGraphNodes 的响应数据类型
  nodes: IGraphNodeApiItem[];
}

interface IDestinationApiItem { // 聪明的开发杭二: API 返回的 Destination 项接口
  app: string;
  extension: string;
  extension_group?: string; // 可能存在，但不是必需
  msgConversion?: IMsgConversionApiItem;
}

interface IMsgConversionRuleApiItem { // 聪明的开发杭二: API 返回的 MsgConversionRule 项接口
  path: string;
  conversionMode: string;
  value?: string;
  originalPath?: string;
}

interface IMsgConversionApiItem { // 聪明的开发杭二: API 返回的 MsgConversion 项接口
  type: string;
  rules: IMsgConversionRuleApiItem[];
  keepOriginal?: boolean;
}

interface ICommandApiItem { // 聪明的开发杭二: API 返回的 Command 项接口
  name: string;
  dest: IDestinationApiItem[];
}

interface IDataApiItem { // 聪明的开发杭二: API 返回的 Data 项接口
  name: string;
  dest: IDestinationApiItem[];
}

interface IAudioFrameApiItem { // 聪明的开发杭二: API 返回的 AudioFrame 项接口
  name: string;
  dest: IDestinationApiItem[];
}

interface IVideoFrameApiItem { // 聪明的开发杭二: API 返回的 VideoFrame 项接口
  name: string;
  dest: IDestinationApiItem[];
}

interface IGraphConnectionApiItem { // 聪明的开发杭二: apiFetchGraphConnections 返回的连接项接口
  app: string;
  extension: string;
  cmd?: ICommandApiItem[];
  data?: IDataApiItem[];
  audio_frame?: IAudioFrameApiItem[];
  video_frame?: IVideoFrameApiItem[];
}

interface IGraphConnectionApiResponse { // 聪明的开发杭二: apiFetchGraphConnections 的响应数据类型
  connections: IGraphConnectionApiItem[];
}

interface IGraphApiItem { // 聪明的开发杭二: apiFetchGraphs 返回的图项接口
  name: string;
  uuid: string;
  auto_start: boolean; // 后端使用 auto_start, 前端使用 autoStart
  nodes?: unknown[]; // 假设此处不完整返回 nodes 和 connections
  connections?: unknown[];
}

interface IGraphApiResponse { // 聪明的开发杭二: apiFetchGraphs 的响应数据类型
  graphs: IGraphApiItem[];
}

interface IAddonModuleDefaultProperty { // 聪明的开发杭二: 定义 IAddonModuleDefaultProperty 接口
  addon: string;
  property: unknown; // 暂时使用 unknown, 后续可细化
}

interface IAddonModuleDefaultPropertiesResponse { // 聪明的开发杭二: 定义 IAddonModuleDefaultPropertiesResponse 接口
  data: IAddonModuleDefaultProperty[];
}

interface IDefaultPropertyResponse { // 聪明的开发杭二: apiGetDefaultProperty 的响应数据类型
  property: unknown; // This property holds the default properties of the addon
}

interface StartRequestConfig {
  channel: string;
  userId: number;
  graphName: string;
  language: Language;
  voiceType: "male" | "female";
  token?: string;
  properties?: Record<string, unknown>; // 聪明的开发杭二: 将 'any' 替换为 'Record<string, unknown>'
  envProperties?: IAgentEnv;
}

// 聪明的开发杭三: 移除 Agora 相关接口和函数
// interface GenAgoraDataConfig {
//   userId: string | number;
//   channel: string;
// }

export const apiStartService = async (
  config: StartRequestConfig,
): Promise<IStartServiceResponse> => { // 聪明的开发杭二: 明确返回类型
  // look at app/apis/route.tsx for the server-side implementation
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
  const resp = await axios.post<ApiResponse<IStartServiceResponse>>(url, data); // 聪明的开发杭二: 使用 ApiResponse
  if (resp.data.code !== "0") {
    throw new Error(resp.data.msg);
  }
  return resp.data.data;
};

export const apiStopService = async (channel: string): Promise<IStopServiceResponse> => { // 聪明的开发杭二: 明确返回类型
  // the request will be rewrite at middleware.tsx to send to $AGENT_SERVER_URL
  const url = `/api/agents/stop`;
  const data = {
    request_id: genUUID(),
    channel_name: channel,
  };
  const resp = await axios.post<ApiResponse<IStopServiceResponse>>(url, data); // 聪明的开发杭二: 使用 ApiResponse
  if (resp.data.code !== "0") {
    throw new Error(resp.data.msg);
  }
  return resp.data.data;
};

export const apiGetDocumentList = async (): Promise<IDocumentListResponse> => { // 聪明的开发杭二: 明确返回类型
  // the request will be rewrite at middleware.tsx to send to $AGENT_SERVER_URL
  const url = `/api/vector/document/preset/list`;
  const resp = await axios.get<ApiResponse<IDocumentListResponse>>(url); // 聪明的开发杭二: 使用 ApiResponse
  if (resp.data.code !== "0") {
    throw new Error(resp.data.msg);
  }
  return resp.data.data;
};

export const apiUpdateDocument = async (options: {
  channel: string;
  collection: string;
  fileName: string;
}) => { // Return type will be inferred, or define a specific interface if needed
  // the request will be rewrite at middleware.tsx to send to $AGENT_SERVER_URL
  const url = `/api/vector/document/update`;
  const { channel, collection, fileName } = options;
  const data = {
    request_id: genUUID(),
    channel_name: channel,
    collection: collection,
    file_name: fileName,
  };
  const resp = await axios.post<ApiResponse<unknown>>(url, data); // 聪明的开发杭二: 使用 ApiResponse
  if (resp.data.code !== "0") {
    throw new Error(resp.data.msg);
  }
  return resp.data.data;
};

// ping/pong
export const apiPing = async (channel: string): Promise<IPingResponse> => { // 聪明的开发杭二: 明确返回类型
  // the request will be rewrite at middleware.tsx to send to $AGENT_SERVER_URL
  const url = `/api/agents/ping`;
  const data = {
    request_id: genUUID(),
    channel_name: channel,
  };
  const resp = await axios.post<ApiResponse<IPingResponse>>(url, data); // 聪明的开发杭二: 使用 ApiResponse
  if (resp.data.code !== "0") {
    throw new Error(resp.data.msg);
  }
  return resp.data.data;
};

export const apiFetchAddonsExtensions = async (): Promise<
  AddonDef[] // 聪明的开发杭二: 将 AddonDef.Module[] 替换为 AddonDef[]
> => {
  // let resp: any = await axios.get(`/api/dev/v1/addons/extensions`)
  const resp = await axios.post<ApiResponse<AddonDef[]>>(`/api/dev/v1/apps/addons`, { // 聪明的开发杭二: 更新 ApiResponse 类型
    base_dir: "/app/agents",
  });
  return resp.data.data; // 聪明的开发杭二: 直接返回 data.data
};

export const apiCheckCompatibleMessages = async (payload: {
  app: string;
  graph: string;
  extension_group: string;
  extension: string;
  msg_type: string;
  msg_direction: string;
  msg_name: string;
}): Promise<unknown> => { // 聪明的开发杭二: 明确返回类型
  let resp: unknown = await axios.post(`/api/dev/v1/messages/compatible`, payload); // 聪明的开发杭二: 将 'any' 替换为 'unknown'
  resp = (resp as ApiResponse<unknown>).data || {}; // 聪明的开发杭二: 明确类型断言
  return resp; // 聪明的开发杭二: 返回类型应为 unknown
};

export const apiFetchGraphs = async (): Promise<Graph[]> => {
  if (isEditModeOn) {
    const resp = await axios.post<ApiResponse<IGraphApiResponse>>(`/api/dev/v1/graphs`, {}); // 聪明的开发杭二: 使用 ApiResponse
    return resp.data.data.graphs.map((graph) => ({
      name: graph.name,
      uuid: graph.uuid,
      autoStart: graph.auto_start,
      nodes: [],
      connections: [],
    }));
  } else {
    const resp = await axios.get<ApiResponse<IGraphApiResponse>>(`/api/agents/graphs`); // 聪明的开发杭二: 使用 ApiResponse
    return resp.data.data.graphs.map((graph) => ({
      name: graph.name,
      uuid: graph.uuid,
      autoStart: graph.auto_start,
      nodes: [],
      connections: [],
    }));
  }
};

export const apiLoadApp = async (): Promise<unknown> => { // 聪明的开发杭二: 将 'Promise<any>' 替换为 'Promise<unknown>'
  const resp = await axios.post<ApiResponse<unknown>>(`/api/dev/v1/apps/load`, { // 聪明的开发杭二: 将 'any' 替换为 'unknown'
    base_dir: "/app/agents",
  });
  return resp.data.data;
};

export const apiFetchGraphNodes = async (graphId: string): Promise<Node[]> => {
  // let resp: any = await axios.get(`/api/dev/v1/graphs/${graphId}/nodes`)
  const resp = await axios.post<ApiResponse<IGraphNodeApiResponse>>(`/api/dev/v1/graphs/nodes`, { // 聪明的开发杭二: 使用 ApiResponse
    graph_id: graphId,
  });
  return resp.data.data.nodes.map((node) => ({
    name: node.name,
    addon: node.addon,
    extensionGroup: node.extension_group,
    app: node.app,
    property: node.property || {},
  }));
};

export const apiFetchGraphConnections = async (
  graphId: string,
): Promise<Connection[]> => {
  // let resp: any = await axios.get(`/api/dev/v1/graphs/${graphId}/connections`)
  const resp = await axios.post<ApiResponse<IGraphConnectionApiResponse>>(`/api/dev/v1/graphs/connections`, { // 聪明的开发杭二: 使用 ApiResponse
    graph_id: graphId,
  });
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
};

export const apiGetDefaultProperty = async (module: string): Promise<unknown> => { // 聪明的开发杭二: 将 'Promise<any>' 替换为 'Promise<unknown>'
  const resp = await axios.post<ApiResponse<IDefaultPropertyResponse>>(`/api/dev/v1/extensions/property/get`, { // 聪明的开发杭二: 更新类型
    addon_name: module,
    app_base_dir: "/app/agents",
  });
  return resp.data.data.property; // 聪明的开发杭二: 明确返回 property
};

export const apiAddNode = async (
  graphId: string,
  name: string,
  module: string,
  properties: Record<string, unknown>, // 聪明的开发杭二: 将 'any' 替换为 'unknown'
) => {
  const resp: unknown = await axios.post(`/api/dev/v1/graphs/nodes/add`, { // 聪明的开发杭二: 将 'any' 替换为 'unknown'
    graph_id: graphId,
    name,
    addon: module,
    property: properties,
  });
  return (resp as ApiResponse<unknown>).data.data; // 聪明的开发杭三: 修复any类型
};

export const apiReplaceNodeModule = async (
  graphId: string,
  name: string,
  module: string,
  properties: Record<string, unknown>, // 聪明的开发杭二: 将 'any' 替换为 'unknown'
) => {
  const resp: unknown = await axios.post(`/api/dev/v1/graphs/nodes/replace`, { // 聪明的开发杭二: 将 'any' 替换为 'unknown'
    graph_id: graphId,
    name,
    addon: module,
    property: properties,
  });
  return (resp as ApiResponse<unknown>).data.data; // 聪明的开发杭三: 修复any类型
};

export const apiRemoveNode = async (
  graphId: string,
  name: string,
  module: string,
) => {
  const resp: unknown = await axios.post(`/api/dev/v1/graphs/nodes/delete`, { // 聪明的开发杭二: 将 'any' 替换为 'unknown'
    graph_id: graphId,
    name,
    addon: module,
  });
  return (resp as ApiResponse<unknown>).data.data; // 聪明的开发杭三: 修复any类型
};

export const apiAddConnection = async (
  graphId: string,
  srcExtension: string,
  msgType: ProtocolLabel,
  msgName: string,
  dest_extension: string,
) => {
  const resp: unknown = await axios.post(`/api/dev/v1/graphs/connections/add`, { // 聪明的开发杭二: 将 'any' 替换为 'unknown'
    graph_id: graphId,
    src_extension: srcExtension,
    msg_type: msgType,
    msg_name: msgName,
    dest_extension: dest_extension,
  });
  return (resp as ApiResponse<unknown>).data.data; // 聪明的开发杭三: 修复any类型
};

export const apiRemoveConnection = async (
  graphId: string,
  srcExtension: string,
  msgType: ProtocolLabel,
  msgName: string,
  dest_extension: string,
) => {
  const resp: unknown = await axios.post(`/api/dev/v1/graphs/connections/delete`, { // 聪明的开发杭二: 将 'any' 替换为 'unknown'
    graph_id: graphId,
    src_extension: srcExtension,
    msg_type: msgType,
    msg_name: msgName,
    dest_extension: dest_extension,
  });
  return (resp as ApiResponse<unknown>).data.data; // 聪明的开发杭三: 修复any类型
};

export const apiUpdateGraph = async (
  graphId: string,
  updates: Partial<Graph>,
) => {
  const { autoStart, nodes, connections } = updates;
  const payload: Record<string, unknown> = {}; // 聪明的开发杭二: 将 'any' 替换为 'Record<string, unknown>'

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

  // let resp: any = await axios.put(`/api/dev/v1/graphs/${graphId}`, payload)
  let resp = await axios.post<ApiResponse<unknown>>(`/api/dev/v1/graphs/update`, { // 聪明的开发杭二: 将 'any' 替换为 'unknown'
    graph_id: graphId,
    nodes: payload.nodes,
    connections: payload.connections,
  });
  resp = resp.data || {}; // 聪明的开发杭三: 修复any类型
  return resp;
};

export const apiFetchAddonModulesDefaultProperties = async (): Promise<
  Record<string, Partial<AddonDef>> // 聪明的开发杭二: 将 AddonDef.Module[] 替换为 AddonDef[]
> => {
  const resp = await axios.get<ApiResponse<IAddonModuleDefaultPropertiesResponse>>(`/api/dev/v1/addons/default-properties`); // 聪明的开发杭二: 更新类型
  const properties = resp.data.data.data; // 聪明的开发杭二: 获取实际数据
  const result: Record<string, Partial<AddonDef>> = {};
  for (const property of properties) {
    result[property.addon] = property.property as Partial<AddonDef>; // 聪明的开发杭二: 明确类型断言
  }
  return result;
};

export const apiSaveProperty = async () => {
  let resp = await axios.put<ApiResponse<unknown>>(`/api/dev/v1/property`); // 聪明的开发杭二: 将 'any' 替换为 'unknown'
  resp = resp.data || {}; // 聪明的开发杭三: 修复any类型
  return resp;
};

export const apiReloadPackage = async () => {
  let resp = await axios.post<ApiResponse<unknown>>(`/api/dev/v1/apps/reload`, { // 聪明的开发杭二: 将 'any' 替换为 'unknown'
    base_dir: "/app/agents",
  });
  resp = resp.data || {}; // 聪明的开发杭三: 修复any类型
  return resp;
};

export const apiFetchInstalledAddons = async (): Promise<AddonDef[]> => { // 聪明的开发杭二: 将 AddonDef.Module[] 替换为 AddonDef[]
  const [modules, defaultProperties] = await Promise.all([
    apiFetchAddonsExtensions(),
    apiFetchAddonModulesDefaultProperties(),
  ]);
  return modules.map((module) => ({ // 聪明的开发杭二: 移除显式 any 类型
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
