import axios from "axios";
import { Graph, AddonDef } from "./graph";

const agentUrl = "http://localhost:9090";
const api = axios.create({
  baseURL: agentUrl,
});

// 定义通用的请求参数类型，不包含 token
interface CommonRequestParams {
  url: string;
  method: "get" | "post" | "put" | "delete";
  data?: any;
  params?: any;
}

// 通用请求函数，不处理 token
const commonRequest = async ({ url, method, data, params }: CommonRequestParams) => {
  try {
    const response = await api.request({
      url,
      method,
      data,
      params,
    });
    return response.data;
  } catch (error) {
    console.error(`Error during ${method} request to ${url}:`, error);
    throw error; // Re-throw to allow caller to handle
  }
};

export const apiReloadPackage = () => {
  return commonRequest({
    url: "/reload_package",
    method: "post",
  });
};

export const apiFetchGraphs = (): Promise<Graph[]> => {
  return commonRequest({
    url: "/graphs",
    method: "get",
  });
};

export const apiFetchInstalledAddons = (): Promise<AddonDef[]> => {
  return commonRequest({
    url: "/installed_addons",
    method: "get",
  });
};

export const apiLoadApp = () => {
  return commonRequest({
    url: "/load_app",
    method: "post",
  });
};

export const apiFetchGraphDetails = (graph: Graph): Promise<Graph> => {
  return commonRequest({
    url: `/graph_details/${graph.uuid}`,
    method: "get",
  });
};

export const apiUpdateGraph = (
  uuid: string,
  updates: Partial<Graph>,
): Promise<void> => {
  return commonRequest({
    url: `/update_graph/${uuid}`,
    method: "post",
    data: updates,
  });
};

export const apiSaveProperty = (
  graph_id: string,
  node_name: string,
  properties: Record<string, any>,
  property_name: string,
  property_value: any,
): Promise<any> => {
  return commonRequest({
    url: "/save_property",
    method: "post",
    data: {
      graph_id,
      node_name,
      properties,
      property_name,
      property_value,
    },
  });
};

export const apiPing = (channel: string): Promise<any> => {
  return commonRequest({
    url: "/ping", // Assuming /ping is the correct endpoint for agent ping
    method: "post",
    data: { channel_name: channel },
  });
};

interface StartRequestConfig {
  channel: string;
  userId: number;
  graphName: string;
  language: string; // Assuming Language is a string
  voiceType: string; // Assuming VoiceType is a string
  envProperties?: Record<string, string>;
}

export const apiStartService = (config: StartRequestConfig): Promise<any> => {
  const { channel, userId, graphName, language, voiceType, envProperties } = config;
  return commonRequest({
    url: `/api/agents/start`,
    method: "post",
    data: {
      request_id: Date.now().toString() + Math.random().toString().substring(2, 8), // Generate a simple UUID
      channel_name: channel,
      user_uid: userId,
      graph_name: graphName,
      language,
      voice_type: voiceType,
      env_properties: envProperties,
    },
  });
};

export const apiStopService = (channel: string): Promise<any> => {
  return commonRequest({
    url: `/api/agents/stop`,
    method: "post",
    data: {
      request_id: Date.now().toString() + Math.random().toString().substring(2, 8), // Generate a simple UUID
      channel_name: channel,
    },
  });
};
