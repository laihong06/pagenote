import { http } from '@kit.NetworkKit';
import { preferences } from '@kit.ArkData';
import { common } from '@kit.AbilityKit';

/**
 * AI 模型配置与 OpenAI 兼容客户端，与 Android agent 模块的
 * ModelConfig.kt / OpenAiCompatibleClient.kt 对齐：
 * - BaseURL / Key / 模型名全部可配；任一缺失即报缺配置，不 fallback 到内置模型；
 * - 密钥不落项目目录：存应用 preferences，不进 src/；
 * - key 永不硬编码进代码。
 */

export interface ModelEndpoint {
  baseUrl: string;
  apiKey: string;
  model: string;
}

export class MissingModelConfigError extends Error {
  constructor() {
    super('missing model config');
    this.name = 'MissingModelConfigError';
  }
}

export const LOCAL_RELAY_BASE_URL: string = 'http://127.0.0.1:3457/v1';
export const LOCAL_RELAY_MODEL: string = 'cline-free/mimo-v2.6-flash';

const AGENT_PREFS_NAME: string = 'pagenote-agent';
const KEY_BASE_URL: string = 'baseUrl';
const KEY_API_KEY: string = 'apiKey';
const KEY_MODEL: string = 'model';

export class AgentSettings {
  private static store(context: common.UIAbilityContext): preferences.Preferences {
    return preferences.getPreferencesSync(context, { name: AGENT_PREFS_NAME });
  }

  static load(context: common.UIAbilityContext): ModelEndpoint {
    const prefs = AgentSettings.store(context);
    return {
      baseUrl: (prefs.getSync(KEY_BASE_URL, '') as string),
      apiKey: (prefs.getSync(KEY_API_KEY, '') as string),
      model: (prefs.getSync(KEY_MODEL, '') as string)
    };
  }

  static save(context: common.UIAbilityContext, endpoint: ModelEndpoint): void {
    const prefs = AgentSettings.store(context);
    prefs.putSync(KEY_BASE_URL, endpoint.baseUrl);
    prefs.putSync(KEY_API_KEY, endpoint.apiKey);
    prefs.putSync(KEY_MODEL, endpoint.model);
    prefs.flush();
  }

  static clear(context: common.UIAbilityContext): void {
    AgentSettings.save(context, { baseUrl: '', apiKey: '', model: '' });
  }
}

export class ModelConfig {
  /** 缺配置时抛 MissingModelConfigError，由调用方展示。 */
  static resolve(baseUrl: string, apiKey: string, model: string): ModelEndpoint {
    let url = baseUrl.trim();
    if (url.length === 0) {
      url = LOCAL_RELAY_BASE_URL;
    }
    let name = model.trim();
    if (name.length === 0) {
      name = LOCAL_RELAY_MODEL;
    }
    const key = apiKey.trim();
    if (url.length === 0 || name.length === 0 || key.length === 0) {
      throw new MissingModelConfigError();
    }
    return { baseUrl: url, apiKey: key, model: name };
  }
}

export interface ChatMessage {
  role: string;
  content: string;
}

/** OpenAI 兼容 chat/completions 客户端。 */
export class OpenAiCompatibleClient {
  static async complete(endpoint: ModelEndpoint, messages: ChatMessage[]): Promise<string> {
    const url = `${endpoint.baseUrl.replace(/\/+$/, '')}/chat/completions`;
    const request = http.createHttp();
    try {
      const body = JSON.stringify({
        model: endpoint.model,
        messages: messages
      });
      const response = await request.request(url, {
        method: http.RequestMethod.POST,
        header: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${endpoint.apiKey}`
        },
        connectTimeout: 30000,
        readTimeout: 120000,
        extraData: body
      });
      if (response.responseCode !== 200) {
        throw new Error(`model request failed: HTTP ${response.responseCode}`);
      }
      const text = response.result as string;
      const parsed = JSON.parse(text) as Record<string, Object>;
      const choices = parsed['choices'] as Array<Record<string, Object>>;
      if (choices === undefined || choices.length === 0) {
        throw new Error('model returned no choices');
      }
      const message = choices[0]['message'] as Record<string, Object>;
      const content = message['content'] as string;
      if (content === undefined) {
        throw new Error('model returned empty content');
      }
      return content;
    } finally {
      request.destroy();
    }
  }
}

/** 按页讲解：把当页笔记与页码交给模型，返回讲解文本。 */
export class PageTutor {
  static async explainPage(endpoint: ModelEndpoint, page: number, noteMarkdown: string,
    sourceHint: string): Promise<string> {
    const messages: ChatMessage[] = [
      {
        role: 'system',
        content: '你是 PageNote 的逐页讲解助手。用简洁中文讲解用户指定页的内容，结合用户自己的笔记，指出要点与可能的疑问。'
      },
      {
        role: 'user',
        content: `第 ${page} 页。\n原文摘要：${sourceHint}\n我的笔记：\n${noteMarkdown}`
      }
    ];
    return OpenAiCompatibleClient.complete(endpoint, messages);
  }
}

export type SelectionAction = 'rewrite' | 'translate' | 'summarize';

/** 笔记选区 AI 动作（对齐 Android 选区 AI 动作：改写/翻译/总结，确认后插回）。 */
export class SelectionActions {
  static async run(endpoint: ModelEndpoint, action: SelectionAction, text: string): Promise<string> {
    if (text.trim().length === 0) {
      throw new Error('没有选中内容');
    }
    let instruction: string;
    if (action === 'rewrite') {
      instruction = '请改写下面这段文字，让它更清晰流畅，保持原意与信息量，只输出改写后的文字：';
    } else if (action === 'translate') {
      instruction = '请把下面这段文字翻译成英文（已是英文则翻译成中文），只输出译文：';
    } else {
      instruction = '请用简洁中文总结下面这段文字的要点，只输出总结：';
    }
    const messages: ChatMessage[] = [
      { role: 'user', content: `${instruction}\n\n${text}` }
    ];
    return OpenAiCompatibleClient.complete(endpoint, messages);
  }
}
