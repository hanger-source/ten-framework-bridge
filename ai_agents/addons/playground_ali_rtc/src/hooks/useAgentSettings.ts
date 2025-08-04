import { useState, useEffect } from 'react';
import { IAgentSettings } from '@/types';

const DEFAULT_AGENT_SETTINGS: IAgentSettings = {
    greeting: "",
    prompt: "",
    token: "",
    env: {
        BAILIAN_DASHSCOPE_API_KEY: "",
        ALI_APP_ID: "",
        ALI_APP_CERTIFICATE: "",
        GREETING: "",
        CHAT_PROMPT: "",
    },
};

// 全局的agentSettings管理器
class AgentSettingsManager {
    private static instance: AgentSettingsManager;
    private settings: IAgentSettings = DEFAULT_AGENT_SETTINGS;

    private constructor() {
        this.loadSettings();
    }

    static getInstance(): AgentSettingsManager {
        if (!AgentSettingsManager.instance) {
            AgentSettingsManager.instance = new AgentSettingsManager();
        }
        return AgentSettingsManager.instance;
    }

    private loadSettings() {
        if (typeof window !== 'undefined') {
            const saved = localStorage.getItem('agent_settings');
            if (saved) {
                try {
                    this.settings = JSON.parse(saved);
                } catch (e) {
                    console.error('Failed to parse saved settings:', e);
                }
            }
        }
    }

    getSettings(): IAgentSettings {
        return this.settings;
    }

    saveSettings(settings: IAgentSettings) {
        this.settings = settings;
        if (typeof window !== 'undefined') {
            localStorage.setItem('agent_settings', JSON.stringify(settings));
        }
    }

    updateSettings(partialSettings: Partial<IAgentSettings>) {
        this.settings = { ...this.settings, ...partialSettings };
        this.saveSettings(this.settings);
    }
}

// 供rtcManager等使用的普通函数
export const getAgentSettings = (): IAgentSettings => {
    return {
        prompt: "你是一个智能助手，请帮助用户解决问题。",
        greeting: "你好！我是你的智能助手，有什么可以帮助你的吗？",
        token: "",
        env: {
            BAILIAN_DASHSCOPE_API_KEY: "",
            ALI_APP_ID: "",
            ALI_APP_CERTIFICATE: "",
            GREETING: "你好！我是你的智能助手，有什么可以帮助你的吗？",
            CHAT_PROMPT: "你是一个智能助手，请帮助用户解决问题。",
        },
    };
};

// React hook
export const useAgentSettings = () => {
    const manager = AgentSettingsManager.getInstance();
    const [agentSettings, setAgentSettings] = useState<IAgentSettings>(manager.getSettings());

    const saveSettings = (settings: IAgentSettings) => {
        manager.saveSettings(settings);
        setAgentSettings(settings);
    };

    const updateSettings = (partialSettings: Partial<IAgentSettings>) => {
        manager.updateSettings(partialSettings);
        setAgentSettings(manager.getSettings());
    };

    return {
        agentSettings,
        saveSettings,
        updateSettings,
    };
};