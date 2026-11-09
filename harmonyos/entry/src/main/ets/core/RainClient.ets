import { http, webSocket } from '@kit.NetworkKit';
import { preferences } from '@kit.ArkData';
import { common } from '@kit.AbilityKit';
import { url as arkUrl } from '@kit.ArkTS';
import { BusinessError } from '@kit.BasicServicesKit';
import {
  RainCourse, RainLesson, RainSlide, RainSlideImage, RainCurrentLesson,
  RainLiveSlideEvent, rainLiveEventKey, normalizeLiveMessage, selectTimelineReplay,
  resolveRainLiveSlide,
  rainApiHeaders, rainHttpError, rainObjectData, rainAsObject, rainAsArray, rainIdOf, rainId,
  normalizeHistoryLessons, normalizeReportSlides, normalizeRainPresentation, normalizeCurrentLessons
} from './RainProtocol';

interface RainPostResult {
  cookies: string[];
  authorization: string | null;
  body: string;
}

/** 响应头大小写不敏感取原始值（set-cookie 可能是数组）。 */
function rainHeaderRaw(header: Record<string, Object>, name: string): Object | undefined {
  const lower = name.toLowerCase();
  const keys = Object.keys(header);
  for (const key of keys) {
    if (key.toLowerCase() === lower) {
      return header[key];
    }
  }
  return undefined;
}

function rainHeaderValue(header: Record<string, Object>, name: string): string {
  return rainHeaderRaw(header, name)?.toString() ?? '';
}

/**
 * 雨课堂客户端：应用内二维码登录、会话存取、课程/课堂列表与课件页面下载。
 * 流程与 Android RainClassroomClient.kt 对齐：
 * wss requestlogin → ticket → 二维码图片（白名单域名校验）→ UserID/Auth →
 * /pc/web_login → Set-Cookie + Set-Auth → user/basic-info → 会话存应用偏好（不落项目目录）。
 */
export type RainLoginStatus = 'signed_out' | 'connecting' | 'loading_qr' | 'waiting_scan'
  | 'authenticated' | 'expired' | 'failed';

export interface RainLoginState {
  status: RainLoginStatus;
  qrImage: ArrayBuffer | null;
  accountName: string | null;
  error: string | null;
}

export interface RainSession {
  cookie: string;
  name: string;
  authorization: string | null;
}

export interface RainLiveState {
  status: string; // stopped | joining | connecting | active | reconnecting | finished | error
  lessonId: string;
  error: string | null;
  collected: number;
  pending: number;
  possibleGap: boolean;
}

const MAX_QR_IMAGE_BYTES: number = 2 * 1024 * 1024;
const MAX_SLIDE_BYTES: number = 24 * 1024 * 1024;
const PREFS_NAME: string = 'pagenote-rain-session';
const KEY_SESSION: string = 'session';
const YUKETANG_HOME: string = 'https://www.yuketang.cn';
const DEFAULT_REFERER: string = 'https://www.yuketang.cn/web?next=/v2/web/index&type=3';
const USER_AGENT: string = 'Mozilla/5.0 (Pad; HarmonyOS) AppleWebKit/537.36 (KHTML, like Gecko) PageNote/0.1';

export class RainSessionStore {
  private static prefs(context: common.UIAbilityContext): preferences.Preferences {
    return preferences.getPreferencesSync(context, { name: PREFS_NAME });
  }

  static save(context: common.UIAbilityContext, session: RainSession): void {
    const store = RainSessionStore.prefs(context);
    store.putSync(KEY_SESSION, JSON.stringify(session));
    store.flush();
  }

  static read(context: common.UIAbilityContext): RainSession | null {
    const store = RainSessionStore.prefs(context);
    const text = store.getSync(KEY_SESSION, '') as string;
    if (text.length === 0) {
      return null;
    }
    try {
      const parsed = rainAsObject(JSON.parse(text) as Object);
      if (parsed === null) {
        return null;
      }
      const cookie = parsed['cookie']?.toString() ?? '';
      if (cookie.length === 0) {
        return null;
      }
      return {
        cookie: cookie,
        name: parsed['name']?.toString() ?? '雨课堂用户',
        authorization: rainId(parsed['authorization'])
      };
    } catch (e) {
      return null;
    }
  }

  static clear(context: common.UIAbilityContext): void {
    const store = RainSessionStore.prefs(context);
    store.deleteSync(KEY_SESSION);
    store.flush();
  }
}

export class RainClient {
  private context: common.UIAbilityContext;
  private loginSocket: webSocket.WebSocket | null = null;
  private loginFinished: boolean = false;
  private loginGeneration: number = 0;

  constructor(context: common.UIAbilityContext) {
    this.context = context;
  }

  // ---- 会话 ----

  session(): RainSession | null {
    return RainSessionStore.read(this.context);
  }

  logout(): void {
    this.cancelLogin();
    RainSessionStore.clear(this.context);
  }

  /** 应用启动时校验已存会话；401/403 清除并视为未登录。 */
  async refreshLogin(): Promise<RainLoginState> {
    const session = this.session();
    if (session === null) {
      return { status: 'signed_out', qrImage: null, accountName: null, error: null };
    }
    try {
      const data = await this.requestJson(`${YUKETANG_HOME}/api/v3/user/basic-info`, session.cookie);
      const unwrapped = rainObjectData(data);
      const user = rainAsObject(unwrapped['user']) ?? unwrapped;
      const name = user['name']?.toString() ?? user['nickname']?.toString() ?? session.name;
      return { status: 'authenticated', qrImage: null, accountName: name, error: null };
    } catch (e) {
      const message = (e as Error).message;
      if (message.indexOf('401') >= 0 || message.indexOf('403') >= 0) {
        RainSessionStore.clear(this.context);
        return { status: 'signed_out', qrImage: null, accountName: null, error: null };
      }
      if (e instanceof Error) {
        throw e;
      }
      throw new Error(message);
    }
  }

  // ---- 登录（二维码 WebSocket 流程）----

  startLogin(onState: (state: RainLoginState) => void): void {
    this.cancelLogin();
    this.loginGeneration += 1;
    const generation = this.loginGeneration;
    this.loginFinished = false;
    onState({ status: 'connecting', qrImage: null, accountName: null, error: null });

    const ws = webSocket.createWebSocket();
    this.loginSocket = ws;
    ws.on('open', () => {
      ws.send(JSON.stringify({
        op: 'requestlogin', role: 'web', version: 1.4, type: 'qrcode', from: 'web'
      })).catch(() => {});
      // 二维码 60 秒过期
      setTimeout(() => {
        if (this.loginGeneration === generation && !this.loginFinished) {
          onState({ status: 'expired', qrImage: null, accountName: null, error: '二维码已过期，请重新生成' });
          ws.close().catch(() => {});
        }
      }, 60000);
    });
    ws.on('message', async (err: BusinessError, value: string | ArrayBuffer) => {
      if (this.loginGeneration !== generation || this.loginFinished) {
        return;
      }
      let message: Record<string, Object | null>;
      try {
        const parsed = rainAsObject(JSON.parse(value as string) as Object);
        if (parsed === null) {
          return;
        }
        message = parsed;
      } catch (e) {
        return;
      }
      const ticket = rainId(message['ticket']);
      if (ticket !== null) {
        onState({ status: 'loading_qr', qrImage: null, accountName: null, error: null });
        try {
          const image = await this.fetchLoginQrImage(ticket);
          if (this.loginGeneration === generation && !this.loginFinished) {
            onState({ status: 'waiting_scan', qrImage: image, accountName: null, error: null });
          }
        } catch (e) {
          if (this.loginGeneration === generation && !this.loginFinished) {
            onState({ status: 'failed', qrImage: null, accountName: null, error: (e as Error).message });
          }
        }
        return;
      }
      if (message['UserID'] !== undefined && message['Auth'] !== undefined) {
        this.loginFinished = true;
        try {
          const state = await this.finishLogin(message['UserID']!.toString(), message['Auth']!.toString());
          onState(state);
        } catch (e) {
          onState({ status: 'failed', qrImage: null, accountName: null, error: (e as Error).message });
        } finally {
          ws.close().catch(() => {});
          this.loginSocket = null;
        }
      }
    });
    ws.on('error', () => {
      if (this.loginGeneration === generation && !this.loginFinished) {
        onState({ status: 'failed', qrImage: null, accountName: null, error: '无法连接雨课堂' });
      }
    });
    ws.connect('wss://www.yuketang.cn/wsapp/').catch(() => {
      if (this.loginGeneration === generation && !this.loginFinished) {
        onState({ status: 'failed', qrImage: null, accountName: null, error: '无法连接雨课堂' });
      }
    });
  }

  cancelLogin(): void {
    this.loginGeneration += 1;
    this.loginFinished = true;
    if (this.loginSocket !== null) {
      this.loginSocket.close().catch(() => {});
      this.loginSocket = null;
    }
  }

  /** ticket 只允许 https 的 yuketang 域或微信官方二维码接口。 */
  private ticketUrl(ticket: string): string {
    let parsed: arkUrl.URL;
    try {
      parsed = arkUrl.URL.parseURL(ticket, YUKETANG_HOME);
    } catch (e) {
      throw new Error('雨课堂二维码地址无效');
    }
    const host = parsed.hostname.toLowerCase();
    const isYuketang = host === 'yuketang.cn' || host.endsWith('.yuketang.cn');
    const isWechatQr = host === 'mp.weixin.qq.com' && parsed.pathname === '/cgi-bin/showqrcode';
    if (parsed.protocol !== 'https:' || parsed.username.length > 0 || (!isYuketang && !isWechatQr)) {
      throw new Error('雨课堂二维码地址无效');
    }
    return parsed.toString();
  }

  private async fetchLoginQrImage(ticket: string): Promise<ArrayBuffer> {
    const url = this.ticketUrl(ticket);
    const request = http.createHttp();
    try {
      const response = await request.request(url, {
        method: http.RequestMethod.GET,
        header: {
          'Accept': 'image/png,image/jpeg,image/webp,image/gif',
          'User-Agent': 'pagenote/harmonyos'
        },
        expectDataType: http.HttpDataType.ARRAY_BUFFER,
        connectTimeout: 20000,
        readTimeout: 30000
      });
      if (response.responseCode < 200 || response.responseCode >= 300) {
        throw new Error('雨课堂二维码图片暂时无法加载');
      }
      const qrHeader = response.header as Record<string, Object>;
      const rawType = rainHeaderValue(qrHeader, 'content-type').split(';')[0].toLowerCase();
      // 与 Android 一致：image/jpg 归一为 image/jpeg
      const contentType = rawType === 'image/jpg' ? 'image/jpeg' : rawType;
      if (['image/png', 'image/jpeg', 'image/webp', 'image/gif'].indexOf(contentType) < 0) {
        throw new Error('雨课堂返回的二维码图片格式无法识别');
      }
      const bytes = response.result as ArrayBuffer;
      if (bytes.byteLength === 0 || bytes.byteLength > MAX_QR_IMAGE_BYTES) {
        throw new Error('雨课堂二维码图片超过大小限制');
      }
      return bytes;
    } finally {
      request.destroy();
    }
  }

  private async finishLogin(userId: string, auth: string): Promise<RainLoginState> {
    const post = await this.postRaw(`${YUKETANG_HOME}/pc/web_login`,
      JSON.stringify({ UserID: userId, Auth: auth }), null);
    const cookies = post.cookies;
    if (cookies.length === 0) {
      throw new Error('雨课堂没有返回登录会话');
    }
    const cookie = cookies.join('; ');
    const data = await this.requestJson(`${YUKETANG_HOME}/api/v3/user/basic-info`, cookie,
      post.authorization);
    const unwrapped = rainObjectData(data);
    const user = rainAsObject(unwrapped['user']) ?? unwrapped;
    const name = user['name']?.toString() ?? user['nickname']?.toString() ?? '雨课堂用户';
    const authorization = post.authorization ?? this.session()?.authorization ?? null;
    RainSessionStore.save(this.context, { cookie: cookie, name: name, authorization: authorization });
    return { status: 'authenticated', qrImage: null, accountName: name, error: null };
  }

  // ---- 课程与课件 ----

  async listCourses(): Promise<RainCourse[]> {
    const session = this.requireSession();
    const data = rainObjectData(await this.requestJson(
      `${YUKETANG_HOME}/v2/api/web/courses/list?identity=2`, session.cookie));
    const list = rainAsArray(data['list']) ?? rainAsArray(data['data']);
    if (list === null) {
      throw new Error('无法识别雨课堂课程列表');
    }
    const courses: RainCourse[] = [];
    for (const element of list) {
      const item = rainAsObject(element);
      if (item === null) {
        continue;
      }
      const course = rainAsObject(item['course']);
      const teacher = rainAsObject(item['teacher']);
      const classroomId = rainIdOf(item, ['classroom_id', 'classroomId']);
      const courseId = rainIdOf(item, ['course_id', 'courseId'])
        ?? (course !== null ? rainIdOf(course, ['id']) : null) ?? rainIdOf(item, ['id']);
      if (classroomId === null || courseId === null) {
        continue;
      }
      courses.push({
        courseId: courseId,
        classroomId: classroomId,
        name: item['name']?.toString() ?? course?.['name']?.toString() ?? '未命名课程',
        teacher: teacher?.['name']?.toString() ?? item['teacher_name']?.toString() ?? '',
        term: item['term_name']?.toString() ?? item['term']?.toString() ?? ''
      });
    }
    return courses;
  }

  async listLessons(classroomId: string): Promise<RainLesson[]> {
    const session = this.requireSession();
    const url = `${YUKETANG_HOME}/v2/api/web/logs/learn/${encodeURIComponent(classroomId)}`
      + '?actype=-1&page=0&offset=100&sort=-1';
    return normalizeHistoryLessons(await this.requestJson(url, session.cookie), classroomId);
  }

  async listCurrentLessons(): Promise<RainCurrentLesson[]> {
    const session = this.requireSession();
    return normalizeCurrentLessons(await this.requestJson(
      `${YUKETANG_HOME}/api/v3/classroom/on-lesson`, session.cookie));
  }

  /** 历史课堂：lesson-info 拿 presentationIds，再逐份拉 slideList（可见页）。 */
  async loadImportSlides(lesson: RainLesson): Promise<RainSlide[]> {
    const session = this.requireSession();
    if (lesson.lessonId !== null && lesson.lessonId.length > 0) {
      const lessonQuery = `lesson_id=${encodeURIComponent(lesson.lessonId)}`;
      const report = rainObjectData(await this.requestJson(
        `${YUKETANG_HOME}/api/v3/classroom-report/student/lesson-info?${lessonQuery}`, session.cookie));
      const ids = rainAsArray(report['presentationIds']);
      if (ids === null) {
        throw new Error('无法识别雨课堂课后报告课件列表');
      }
      const presentationIds: string[] = [];
      for (const element of ids) {
        const id = rainId(element);
        if (id !== null && presentationIds.indexOf(id) < 0) {
          presentationIds.push(id);
        }
      }
      const slides: RainSlide[] = [];
      for (const presentationId of presentationIds) {
        const path = `${YUKETANG_HOME}/api/v3/classroom-report/student/ppt?${lessonQuery}`
          + `&presentationId=${encodeURIComponent(presentationId)}`;
        const deck = await this.requestJson(path, session.cookie);
        slides.push(...normalizeReportSlides(deck, presentationId));
      }
      const seen = new Set<string>();
      const unique = slides.filter((slide: RainSlide): boolean => {
        const key = `${slide.presentationId}:${slide.pageId}`;
        if (seen.has(key)) {
          return false;
        }
        seen.add(key);
        return true;
      });
      if (unique.length === 0) {
        throw new Error('该课堂没有已开放的课件页面');
      }
      return unique;
    }
    const path = `${YUKETANG_HOME}/api/v3/lesson/presentation/fetch?presentation_id=`
      + encodeURIComponent(lesson.presentationId);
    return normalizeRainPresentation(await this.requestJson(path, session.cookie), lesson.presentationId);
  }

  /** 下载单页课件图片：只允许 yuketang 域 https、jpeg/png、≤24MB。 */
  async downloadSlideImage(slide: RainSlide): Promise<RainSlideImage> {
    let parsed: arkUrl.URL;
    try {
      parsed = arkUrl.URL.parseURL(slide.imageUrl);
    } catch (e) {
      throw new Error('课件图片地址无效');
    }
    if (parsed.protocol !== 'https:'
      || (parsed.hostname !== 'yuketang.cn' && !parsed.hostname.endsWith('.yuketang.cn'))) {
      throw new Error('课件图片来自不受支持的资源域名');
    }
    const request = http.createHttp();
    try {
      const response = await request.request(parsed.toString(), {
        method: http.RequestMethod.GET,
        expectDataType: http.HttpDataType.ARRAY_BUFFER,
        connectTimeout: 20000,
        readTimeout: 60000
      });
      if (response.responseCode !== 200) {
        throw rainHttpError(slide.imageUrl, response.responseCode, '课件页面下载失败');
      }
      const slideHeader = response.header as Record<string, Object>;
      const type = rainHeaderValue(slideHeader, 'content-type').split(';')[0].toLowerCase();
      if (type !== 'image/jpeg' && type !== 'image/png') {
        throw new Error('课件页面格式不支持');
      }
      const bytes = response.result as ArrayBuffer;
      if (bytes.byteLength === 0 || bytes.byteLength > MAX_SLIDE_BYTES) {
        throw new Error('课件页面为空或超过 24 MB');
      }
      return { slide: slide, contentType: type, bytes: bytes };
    } finally {
      request.destroy();
    }
  }

  // ---- 实时课堂采集（对齐 Android startLive/connectLive/processPendingLiveSlides）----

  private liveSocket: webSocket.WebSocket | null = null;
  private liveStopped: boolean = true;
  private liveLessonId: string = '';
  private liveAttempts: number = 0;
  private liveProcessing: boolean = false;
  private livePending: Map<string, RainLiveSlideEvent> = new Map<string, RainLiveSlideEvent>();
  private liveInitialReceived: boolean = false;
  private liveLastKey: string | null = null;
  private liveLastPageKey: string | null = null;
  private livePresentations: Map<string, RainSlide[]> = new Map<string, RainSlide[]>();
  private liveToken: string = '';
  private liveIdentity: string = '';
  private liveCollected: number = 0;
  private lessonAuthorization: string | null = null;
  private liveOnSlide: ((image: RainSlideImage) => Promise<void>) | null = null;
  private liveOnState: ((state: RainLiveState) => void) | null = null;

  isLiveActive(): boolean {
    return !this.liveStopped;
  }

  private emitLive(status: string, error: string | null): void {
    if (this.liveOnState !== null) {
      this.liveOnState({
        status: status,
        lessonId: this.liveLessonId,
        error: error,
        collected: this.liveCollected,
        pending: this.livePending.size,
        possibleGap: false
      });
    }
  }

  /** 进入课堂（checkin）并建立 WebSocket 实时连接；每下载好一页回调 onSlide。 */
  async startLive(lesson: RainLesson, onSlide: (image: RainSlideImage) => Promise<void>,
    onState: (state: RainLiveState) => void): Promise<void> {
    if (lesson.lessonId === null || lesson.lessonId.length === 0) {
      throw new Error('此课件没有可连接的课堂编号');
    }
    if (!this.liveStopped) {
      throw new Error('已有正在采集的课堂，请先停止');
    }
    const session = this.requireSession();
    this.livePresentations.clear();
    this.livePending.clear();
    this.liveCollected = 0;
    this.liveLastKey = null;
    this.liveLastPageKey = null;
    this.liveInitialReceived = false;
    this.liveAttempts = 0;
    this.liveStopped = false;
    this.liveOnSlide = onSlide;
    this.liveOnState = onState;
    this.liveLessonId = lesson.lessonId;
    this.emitLive('joining', null);

    const response = await this.postRaw(`${YUKETANG_HOME}/api/v3/lesson/checkin`,
      JSON.stringify({ source: 5, lessonId: lesson.lessonId }), session.cookie,
      `${YUKETANG_HOME}/lesson/fullscreen/v3/${lesson.lessonId}?source=5`);
    this.lessonAuthorization = response.authorization;
    const payload = rainAsObject(JSON.parse(response.body) as Object);
    if (payload === null) {
      throw new Error('进入雨课堂失败');
    }
    const data = rainObjectData(payload);
    const token = data['lessonToken']?.toString() ?? '';
    const identity = rainIdOf(data, ['identityId']);
    if (token.length === 0 || identity === null) {
      throw new Error('进入雨课堂失败');
    }
    this.liveToken = token;
    this.liveIdentity = identity;
    this.emitLive('connecting', null);
    this.connectLive();
  }

  private connectLive(): void {
    if (this.liveStopped) {
      return;
    }
    const ws = webSocket.createWebSocket();
    this.liveSocket = ws;
    ws.on('open', () => {
      this.liveAttempts = 0;
      ws.send(JSON.stringify({
        op: 'hello', userid: this.liveIdentity, role: 'student',
        auth: this.liveToken, lessonid: this.liveLessonId
      })).catch(() => {});
      this.emitLive('active', null);
    });
    ws.on('message', (err: BusinessError, value: string | ArrayBuffer) => {
      if (this.liveStopped) {
        return;
      }
      let payload: Record<string, Object | null>;
      try {
        const parsed = rainAsObject(JSON.parse(value as string) as Object);
        if (parsed === null) {
          return;
        }
        payload = parsed;
      } catch (e) {
        return;
      }
      const message = normalizeLiveMessage(payload);
      if (message.finished) {
        this.emitLive('finished', null);
        this.stopLive();
        return;
      }
      if (message.slides.length === 0) {
        return;
      }
      const initialReplay = message.timeline && !this.liveInitialReceived;
      let replay = message.slides;
      if (message.timeline) {
        const result = selectTimelineReplay(message.slides, this.liveLastKey,
          initialReplay, this.liveLastPageKey);
        if (result.possibleGap) {
          this.emitLive('active', '重连时间线未包含上次位置，可能存在缺页');
        }
        replay = result.slides;
      }
      for (const event of replay) {
        const key = rainLiveEventKey(event);
        if (!this.livePending.has(key)) {
          this.livePending.set(key, event);
        }
      }
      if (initialReplay) {
        this.liveInitialReceived = true;
      }
      this.emitLive(this.liveStopped ? 'stopped' : 'active', null);
      this.processLiveQueue();
    });
    const onDead = (reason: string): void => {
      if (this.liveStopped) {
        return;
      }
      this.liveAttempts += 1;
      this.emitLive('reconnecting', reason);
      const waitMs = Math.min(30000, 1000 * (1 << Math.min(this.liveAttempts, 5)));
      setTimeout(() => {
        if (!this.liveStopped) {
          this.connectLive();
        }
      }, waitMs);
    };
    ws.on('error', () => {
      onDead('课堂连接失败');
    });
    ws.on('close', () => {
      onDead('课堂连接已断开');
    });
    ws.connect('wss://www.yuketang.cn/wsapp/').catch(() => {
      onDead('课堂连接失败');
    });
  }

  /** 顺序处理待写队列：定位页面 → 下载 → 回调写入。ArkTS 单线程，用处理标志代替锁。 */
  private async processLiveQueue(): Promise<void> {
    if (this.liveProcessing) {
      return;
    }
    this.liveProcessing = true;
    try {
      while (this.livePending.size > 0) {
        const firstKey = this.livePending.keys().next().value as string;
        const event = this.livePending.get(firstKey)!;
        let slides = this.livePresentations.get(event.presentationId);
        if (slides === undefined) {
          slides = await this.fetchLiveSlides(event.presentationId);
          this.livePresentations.set(event.presentationId, slides);
        }
        const slide = resolveRainLiveSlide(event, slides);
        const image = await this.downloadSlideImage(slide);
        if (this.liveOnSlide !== null) {
          await this.liveOnSlide(image);
        }
        this.livePending.delete(firstKey);
        this.liveLastKey = `${slide.presentationId}:${slide.sourceIndex}`;
        this.liveLastPageKey = `${slide.presentationId}:page:${slide.pageId}`;
        this.liveCollected += 1;
        this.emitLive(this.liveStopped ? 'stopped' : 'active', null);
      }
    } catch (e) {
      // 失败页保留在队列中，等待显式重试
      this.emitLive('error', (e as Error).message);
    } finally {
      this.liveProcessing = false;
    }
  }

  /** 失败页重试。 */
  async retryLive(): Promise<void> {
    if (this.livePending.size === 0) {
      throw new Error('当前没有待写入的课堂页面');
    }
    await this.processLiveQueue();
  }

  stopLive(): void {
    this.liveStopped = true;
    if (this.liveSocket !== null) {
      this.liveSocket.close().catch(() => {});
      this.liveSocket = null;
    }
    this.emitLive('stopped', null);
  }

  private async fetchLiveSlides(presentationId: string): Promise<RainSlide[]> {
    const session = this.requireSession();
    const url = `${YUKETANG_HOME}/api/v3/lesson/presentation/fetch?presentation_id=`
      + encodeURIComponent(presentationId);
    const payload = await this.requestJson(url, session.cookie, this.lessonAuthorization);
    return normalizeRainPresentation(payload, presentationId);
  }

  // ---- HTTP 基础 ----

  private requireSession(): RainSession {
    const session = this.session();
    if (session === null) {
      throw new Error('请先登录雨课堂');
    }
    return session;
  }

  private async requestJson(url: string, cookie: string,
    authorization?: string | null): Promise<Record<string, Object | null>> {
    const session = this.session();
    const savedAuth = session !== null && session.cookie === cookie ? session.authorization : null;
    const request = http.createHttp();
    try {
      const response = await request.request(url, {
        method: http.RequestMethod.GET,
        header: rainApiHeaders(cookie, authorization ?? savedAuth, DEFAULT_REFERER, USER_AGENT),
        connectTimeout: 20000,
        readTimeout: 35000
      });
      if (response.responseCode < 200 || response.responseCode >= 300) {
        if (response.responseCode === 401 || response.responseCode === 403) {
          RainSessionStore.clear(this.context);
        }
        throw rainHttpError(url, response.responseCode);
      }
      this.captureAuthorization(cookie, response.header);
      const parsed = rainAsObject(JSON.parse(response.result as string) as Object);
      if (parsed === null) {
        throw new Error(`雨课堂返回了无法识别的数据（${arkUrl.URL.parseURL(url).pathname}）`);
      }
      return parsed;
    } finally {
      request.destroy();
    }
  }

  private async postRaw(url: string, body: string, cookie: string | null,
    referer?: string): Promise<RainPostResult> {
    const session = this.session();
    const savedAuth = session !== null && cookie !== null && session.cookie === cookie
      ? session.authorization : null;
    const request = http.createHttp();
    try {
      const headers = rainApiHeaders(cookie ?? '', savedAuth, referer ?? DEFAULT_REFERER, USER_AGENT);
      headers['Content-Type'] = 'application/json; charset=utf-8';
      const response = await request.request(url, {
        method: http.RequestMethod.POST,
        header: headers,
        extraData: body,
        connectTimeout: 20000,
        readTimeout: 35000
      });
      if (response.responseCode < 200 || response.responseCode >= 300) {
        if (response.responseCode === 401 || response.responseCode === 403) {
          RainSessionStore.clear(this.context);
        }
        throw rainHttpError(url, response.responseCode);
      }
      const responseHeader = response.header as Record<string, Object>;
      const setCookie = rainHeaderRaw(responseHeader, 'set-cookie');
      const cookies: string[] = [];
      const collect = (value: string): void => {
        const first = value.split(';')[0].trim();
        if (first.length > 0) {
          cookies.push(first);
        }
      };
      if (Array.isArray(setCookie)) {
        (setCookie as Array<string>).forEach(collect);
      } else if (setCookie !== undefined && setCookie !== null) {
        collect(setCookie.toString());
      }
      const authorization = rainId(rainHeaderRaw(responseHeader, 'set-auth'));
      if (cookie !== null && authorization !== null) {
        this.captureAuthorization(cookie, response.header);
      }
      return { cookies: cookies, authorization: authorization, body: response.result as string };
    } finally {
      request.destroy();
    }
  }

  /** 响应头里的 Set-Auth 需要随会话滚动保存。 */
  private captureAuthorization(cookie: string, header: Object): void {
    const record = header as Record<string, Object>;
    const setAuth = rainId(rainHeaderRaw(record, 'set-auth'));
    if (setAuth === null) {
      return;
    }
    const session = this.session();
    if (session === null || session.cookie !== cookie) {
      return;
    }
    RainSessionStore.save(this.context, {
      cookie: session.cookie, name: session.name, authorization: setAuth
    });
  }
}
