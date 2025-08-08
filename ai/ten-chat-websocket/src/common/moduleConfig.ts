export enum ModuleType {
  STT = "stt",
  LLM = "llm",
  V2V = "v2v",
  TTS = "tts",
  TOOL = "tool",
}

export interface Module {
  name: string;
  type: ModuleType;
  label: string;
}

// 聪明的开发杭二: 定义 ModuleRegistry 接口
export interface ModuleRegistry {
  Module: Module;
  LLMModule: LLMModule;
  V2VModule: V2VModule;
  ToolModule: ToolModule;
}

export type NonToolModuleType = Exclude<ModuleType, ModuleType.TOOL>;
export type NonToolModule = Module & { type: NonToolModuleType };
export enum Modalities {
  Video = "video",
  Audio = "audio",
  Text = "text",
}
export interface LLMModuleOptions {
  inputModalities: Modalities[];
}
export interface V2VModuleOptions {
  inputModalities: Modalities[];
}
export interface ToolModuleOptions {
  outputContentText?: boolean;
}
// Extending Module to define LLMModule with options
export interface LLMModule extends Module {
  type: ModuleType.LLM; // Ensuring it's specific to LLM
  options: LLMModuleOptions;
}
export interface V2VModule extends Module {
  type: ModuleType.V2V;
  options: LLMModuleOptions;
}
export interface ToolModule extends Module {
  type: ModuleType.TOOL;
  options: ToolModuleOptions;
}

// Custom labels for specific keys
export const ModuleTypeLabels: Record<
  NonToolModuleType,
  string
> = {
  [ModuleType.STT]: "STT (Speech to Text)",
  [ModuleType.LLM]: "LLM (Large Language Model)",
  [ModuleType.TTS]: "TTS (Text to Speech)",
  [ModuleType.V2V]: "LLM v2v (V2V Large Language Model)",
};

export const sttModuleRegistry: Record<string, Module> = {
  deepgram_asr_python: {
    name: "deepgram_asr_python",
    type: ModuleType.STT,
    label: "Deepgram STT",
  },
  transcribe_asr_python: {
    name: "transcribe_asr_python",
    type: ModuleType.STT,
    label: "Transcribe STT",
  },
  speechmatics_asr_python: {
    name: "speechmatics_asr_python",
    type: ModuleType.STT,
    label: "Speechmatics STT",
  },
};

export const llmModuleRegistry: Record<string, LLMModule> = {
  openai_chatgpt_python: {
    name: "openai_chatgpt_python",
    type: ModuleType.LLM,
    label: "OpenAI ChatGPT",
    options: { inputModalities: [Modalities.Text] },
  },
  dify_python: {
    name: "dify_python",
    type: ModuleType.LLM,
    label: "Dify Chat Bot",
    options: { inputModalities: [Modalities.Text] },
  },
  coze_python_async: {
    name: "coze_python_async",
    type: ModuleType.LLM,
    label: "Coze Chat Bot",
    options: { inputModalities: [Modalities.Text] },
  },
  gemini_llm_python: {
    name: "gemini_llm_python",
    type: ModuleType.LLM,
    label: "Gemini LLM",
    options: { inputModalities: [Modalities.Text] },
  },
  bedrock_llm_python: {
    name: "bedrock_llm_python",
    type: ModuleType.LLM,
    label: "Bedrock LLM",
    options: {
      inputModalities: [
        Modalities.Text,
        Modalities.Video,
      ],
    },
  },
};

export const ttsModuleRegistry: Record<string, Module> = {
  azure_tts: {
    name: "azure_tts",
    type: ModuleType.TTS,
    label: "Azure TTS",
  },
  cartesia_tts: {
    name: "cartesia_tts",
    type: ModuleType.TTS,
    label: "Cartesia TTS",
  },
  cosy_tts_python: {
    name: "cosy_tts_python",
    type: ModuleType.TTS,
    label: "Cosy TTS",
  },
  elevenlabs_tts_python: {
    name: "elevenlabs_tts_python",
    type: ModuleType.TTS,
    label: "Elevenlabs TTS",
  },
  fish_audio_tts: {
    name: "fish_audio_tts",
    type: ModuleType.TTS,
    label: "Fish Audio TTS",
  },
  minimax_tts_python: {
    name: "minimax_tts_python",
    type: ModuleType.TTS,
    label: "Minimax TTS",
  },
  polly_tts: {
    name: "polly_tts",
    type: ModuleType.TTS,
    label: "Polly TTS",
  },
  neuphonic_tts: {
    name: "neuphonic_tts",
    type: ModuleType.TTS,
    label: "Neuphonic TTS",
  },
  openai_tts_python: {
    name: "openai_tts_python",
    type: ModuleType.TTS,
    label: "OpenAI TTS",
  },
  dubverse_tts: {
    name: "dubverse_tts",
    type: ModuleType.TTS,
    label: "Dubverse TTS",
  },
};

export const v2vModuleRegistry: Record<string, V2VModule> = {
  openai_v2v_python: {
    name: "openai_v2v_python",
    type: ModuleType.V2V,
    label: "OpenAI Realtime",
    options: { inputModalities: [Modalities.Audio] },
  },
  gemini_v2v_python: {
    name: "gemini_v2v_python",
    type: ModuleType.V2V,
    label: "Gemini Realtime",
    options: {
      inputModalities: [
        Modalities.Video,
        Modalities.Audio,
      ],
    },
  },
  glm_v2v_python: {
    name: "glm_v2v_python",
    type: ModuleType.V2V,
    label: "GLM Realtime",
    options: { inputModalities: [Modalities.Audio] },
  },
  stepfun_v2v_python: {
    name: "stepfun_v2v_python",
    type: ModuleType.V2V,
    label: "Stepfun Realtime",
    options: { inputModalities: [Modalities.Audio] },
  },
  azure_v2v_python: {
    name: "azure_v2v_python",
    type: ModuleType.V2V,
    label: "Azure Realtime",
    options: { inputModalities: [Modalities.Audio] },
  },
};

export const toolModuleRegistry: Record<string, ToolModule> = {
  vision_analyze_tool_python: {
    name: "vision_analyze_tool_python",
    type: ModuleType.TOOL,
    label: "Vision Analyze Tool",
    options: {},
  },
  weatherapi_tool_python: {
    name: "weatherapi_tool_python",
    type: ModuleType.TOOL,
    label: "WeatherAPI Tool",
    options: {},
  },
  bingsearch_tool_python: {
    name: "bingsearch_tool_python",
    type: ModuleType.TOOL,
    label: "BingSearch Tool",
    options: {},
  },
  vision_tool_python: {
    name: "vision_tool_python",
    type: ModuleType.TOOL,
    label: "Vision Tool",
    options: {},
  },
  openai_image_generate_tool: {
    name: "openai_image_generate_tool",
    type: ModuleType.TOOL,
    label: "OpenAI Image Generate Tool",
    options: { outputContentText: true },
  },
  computer_tool_python: {
    name: "computer_tool_python",
    type: ModuleType.TOOL,
    label: "Computer Tool",
    options: { outputContentText: true },
  },
  mcp_client_python: {
    name: "mcp_client_python",
    type: ModuleType.TOOL,
    label: "MCP Client Tool",
    options: {},
  },
};

export const moduleRegistry: Record<string, Module> = {
  ...sttModuleRegistry,
  ...llmModuleRegistry,
  ...ttsModuleRegistry,
  ...v2vModuleRegistry,
};

export const compatibleTools: Record<string, string[]> = {
  openai_chatgpt_python: [
    "vision_tool_python",
    "weatherapi_tool_python",
    "bingsearch_tool_python",
    "openai_image_generate_tool",
    "computer_tool_python",
    "mcp_client_python",
  ],
  openai_v2v_python: [
    "weatherapi_tool_python",
    "bingsearch_tool_python",
    "openai_image_generate_tool",
    "computer_tool_python",
    "mcp_client_python",
  ],
  gemini_v2v_python: [
    "weatherapi_tool_python",
    "bingsearch_tool_python",
    "openai_image_generate_tool",
    "computer_tool_python",
  ],
  glm_v2v_python: [
    "weatherapi_tool_python",
    "bingsearch_tool_python",
    "openai_image_generate_tool",
    "computer_tool_python",
  ],
  stepfun_v2v_python: [
    "weatherapi_tool_python",
    "bingsearch_tool_python",
    "openai_image_generate_tool",
    "computer_tool_python",
    "mcp_client_python",
  ],
  azure_v2v_python: [
    "weatherapi_tool_python",
    "bingsearch_tool_python",
    "openai_image_generate_tool",
    "computer_tool_python",
    "mcp_client_python",
  ],
};
