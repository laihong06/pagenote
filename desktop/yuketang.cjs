const crypto = require('node:crypto');
const {
  ORIGIN,
  RainClassroomError,
  normalizeUser,
  normalizeCourses,
  normalizeLessons,
  normalizeCurrentLessons,
  normalizePresentation,
  normalizeSlides,
  unwrapData,
  liveSlideKey,
  normalizeLiveMessage,
  selectTimelineReplay,
  assertRainAssetUrl,
  assertRainQrTicketUrl,
} = require('./yuketang-protocol.cjs');

const LOGIN_TIMEOUT_MS = 60_000;
const MAX_IMAGE_BYTES = 24 * 1024 * 1024;
const MAX_QR_IMAGE_BYTES = 2 * 1024 * 1024;

function parseMessage(event) {
  try {
    const value =
      typeof event.data === 'string' ? event.data : String(event.data);
    return JSON.parse(value);
  } catch {
    return null;
  }
}

function setCookieHeader(response) {
  const headers = response.headers;
  const entries =
    typeof headers.getSetCookie === 'function'
      ? headers.getSetCookie()
      : [headers.get('set-cookie')].filter(Boolean);
  const cookies = new Map();
  for (const entry of entries) {
    const pair = String(entry).split(';', 1)[0];
    const separator = pair.indexOf('=');
    if (separator > 0)
      cookies.set(
        pair.slice(0, separator).trim(),
        pair.slice(separator + 1).trim(),
      );
  }
  return [...cookies].map(([name, value]) => `${name}=${value}`).join('; ');
}

function bearerAuthorization(value) {
  const token = String(value || '')
    .replace(/^Bearer\s*/i, '')
    .trim();
  return token ? `Bearer ${token}` : null;
}

async function fetchLoginQrImage(ticket, fetchImpl) {
  const url = assertRainQrTicketUrl(ticket);
  const response = await fetchImpl(url, {
    headers: {
      Accept: 'image/png,image/jpeg,image/webp,image/gif',
      'User-Agent': 'pagenote/desktop',
    },
    redirect: 'error',
    signal: AbortSignal.timeout(15_000),
  });
  if (!response.ok)
    throw new RainClassroomError(
      'QR_UNAVAILABLE',
      '雨课堂二维码图片暂时无法加载',
    );
  const responseType = response.headers
    .get('content-type')
    ?.split(';', 1)[0]
    .trim()
    .toLowerCase();
  const contentType =
    responseType === 'image/jpg' ? 'image/jpeg' : responseType;
  if (
    !['image/png', 'image/jpeg', 'image/webp', 'image/gif'].includes(
      contentType,
    )
  )
    throw new RainClassroomError(
      'QR_UNAVAILABLE',
      `雨课堂返回的二维码图片格式无法识别（${
        /^[\w.+-]+\/[\w.+-]+$/.test(contentType || '') ? contentType : '未知'
      }）`,
    );
  const declaredSize = Number(response.headers.get('content-length'));
  if (Number.isFinite(declaredSize) && declaredSize > MAX_QR_IMAGE_BYTES)
    throw new RainClassroomError(
      'QR_UNAVAILABLE',
      '雨课堂二维码图片超过大小限制',
    );
  const reader = response.body?.getReader();
  if (!reader)
    throw new RainClassroomError('QR_UNAVAILABLE', '雨课堂二维码图片为空');
  const chunks = [];
  let totalSize = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      totalSize += value.byteLength;
      if (totalSize > MAX_QR_IMAGE_BYTES) {
        await reader.cancel();
        throw new RainClassroomError(
          'QR_UNAVAILABLE',
          '雨课堂二维码图片超过大小限制',
        );
      }
      chunks.push(Buffer.from(value));
    }
  } finally {
    reader.releaseLock();
  }
  const bytes = Buffer.concat(chunks, totalSize);
  if (!bytes.length)
    throw new RainClassroomError('QR_UNAVAILABLE', '雨课堂二维码图片为空');
  return `data:${contentType};base64,${bytes.toString('base64')}`;
}

function createRainClassroomService({
  secureStore,
  fetchImpl = fetch,
  WebSocketImpl = WebSocket,
}) {
  const pendingLogins = new Map();
  const presentations = new Map();
  let live = null;
  let sessionWrites = Promise.resolve();

  function updateSession(update) {
    const task = sessionWrites.then(async () => update(await storedSession()));
    sessionWrites = task.catch(() => {});
    return task;
  }

  async function storedSession() {
    const serialized = await secureStore.get();
    if (!serialized) return null;
    try {
      const session = JSON.parse(serialized);
      return typeof session.cookie === 'string' && session.cookie
        ? session
        : null;
    } catch {
      return null;
    }
  }

  async function request(
    path,
    { cookie, body, authorization, headers = {} } = {},
  ) {
    const url = new URL(path, ORIGIN);
    if (url.origin !== ORIGIN)
      throw new RainClassroomError('INVALID_URL', '雨课堂请求地址无效');
    const saved = cookie ? await storedSession() : null;
    const bearer = bearerAuthorization(
      authorization || (saved?.cookie === cookie ? saved?.authorization : null),
    );
    const response = await fetchImpl(url, {
      method: body === undefined ? 'GET' : 'POST',
      headers: {
        Accept: 'application/json',
        Referer: `${ORIGIN}/web?next=/v2/web/index&type=3`,
        Origin: ORIGIN,
        xtbz: 'ykt',
        'X-Client': 'h5',
        'User-Agent': 'pagenote/desktop',
        ...(cookie ? { Cookie: cookie } : {}),
        ...(bearer ? { Authorization: bearer } : {}),
        ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
        ...headers,
      },
      body: body === undefined ? undefined : JSON.stringify(body),
      redirect: 'error',
      signal: AbortSignal.timeout(25_000),
    });
    const rotated = response.headers.get('set-auth');
    if (response.ok && rotated && saved && saved.cookie === cookie) {
      await updateSession(async (current) => {
        if (current && current.cookie === cookie)
          await secureStore.set(
            JSON.stringify({
              ...current,
              authorization: bearerAuthorization(rotated),
            }),
          );
      });
    }
    if (response.status === 401 || response.status === 403)
      throw new RainClassroomError(
        'AUTH_REQUIRED',
        '雨课堂登录已失效，请重新扫码',
      );
    if (!response.ok)
      throw new RainClassroomError(
        'REMOTE_ERROR',
        `雨课堂请求失败（HTTP ${response.status}）`,
      );
    try {
      return { payload: await response.json(), response };
    } catch {
      throw new RainClassroomError(
        'PROTOCOL_CHANGED',
        '雨课堂返回了无法识别的数据',
      );
    }
  }

  async function validateCookie(cookie, authorization) {
    const { payload, response } = await request('/api/v3/user/basic-info', {
      cookie,
      authorization,
    });
    return {
      user: normalizeUser(payload),
      authorization: bearerAuthorization(
        response.headers.get('set-auth') || authorization,
      ),
    };
  }

  async function finishLogin(login, auth) {
    if (!auth?.UserID || !auth?.Auth || login.finished) return;
    login.finished = true;
    try {
      const response = await fetchImpl(`${ORIGIN}/pc/web_login`, {
        method: 'POST',
        headers: {
          Accept: 'application/json',
          'Content-Type': 'application/json',
          Referer: `${ORIGIN}/web?next=/v2/web/index&type=3`,
          'User-Agent': 'pagenote/desktop',
        },
        body: JSON.stringify({ UserID: auth.UserID, Auth: auth.Auth }),
        redirect: 'error',
        signal: AbortSignal.timeout(25_000),
      });
      if (!response.ok)
        throw new RainClassroomError(
          'AUTH_REQUIRED',
          '雨课堂确认登录失败，请重试',
        );
      const cookie = setCookieHeader(response);
      if (!cookie)
        throw new RainClassroomError(
          'AUTH_REQUIRED',
          '雨课堂没有返回有效登录会话',
        );
      const authorization = response.headers.get('set-auth');
      const validated = await validateCookie(cookie, authorization);
      await updateSession(async () => {
        if (pendingLogins.get(login.id) !== login) return;
        await secureStore.set(
          JSON.stringify({
            cookie,
            ...validated,
            savedAt: new Date().toISOString(),
          }),
        );
      });
      if (pendingLogins.get(login.id) === login)
        login.state = { status: 'authenticated', user: validated.user };
    } catch (error) {
      login.state = {
        status: 'failed',
        error: error.message || '雨课堂登录失败',
      };
    } finally {
      login.socket?.close();
      clearTimeout(login.timer);
      setTimeout(() => pendingLogins.delete(login.id), 30_000).unref?.();
    }
  }

  function startLogin() {
    if (typeof WebSocketImpl !== 'function')
      throw new RainClassroomError(
        'UNAVAILABLE',
        '当前运行环境不支持雨课堂扫码连接',
      );
    for (const old of pendingLogins.values()) {
      clearTimeout(old.timer);
      old.socket?.close();
    }
    pendingLogins.clear();
    const id = crypto.randomUUID();
    const login = {
      id,
      state: { status: 'connecting' },
      socket: new WebSocketImpl('wss://www.yuketang.cn/wsapp/'),
      timer: null,
      finished: false,
    };
    pendingLogins.set(id, login);
    login.timer = setTimeout(() => {
      if (login.finished) return;
      login.state = { status: 'expired', error: '二维码已过期，请刷新' };
      login.socket?.close();
    }, LOGIN_TIMEOUT_MS);
    login.timer.unref?.();
    login.socket.addEventListener('open', () => {
      login.socket.send(
        JSON.stringify({
          op: 'requestlogin',
          role: 'web',
          version: 1.4,
          type: 'qrcode',
          from: 'web',
        }),
      );
    });
    login.socket.addEventListener('message', (event) => {
      const message = parseMessage(event);
      if (!message) return;
      if (
        typeof message.ticket === 'string' &&
        login.state.status === 'connecting'
      ) {
        login.state = { status: 'loading_qr' };
        void fetchLoginQrImage(message.ticket, fetchImpl)
          .then((qrDataUrl) => {
            if (pendingLogins.get(login.id) !== login || login.finished) return;
            login.state = { status: 'waiting_scan', qrDataUrl };
          })
          .catch((error) => {
            if (pendingLogins.get(login.id) !== login || login.finished) return;
            login.state = {
              status: 'failed',
              error:
                error instanceof RainClassroomError
                  ? error.message
                  : '雨课堂二维码图片暂时无法加载',
            };
            login.socket.close();
          });
        return;
      }
      if (message.UserID && message.Auth) void finishLogin(login, message);
    });
    login.socket.addEventListener('error', () => {
      if (!login.finished)
        login.state = {
          status: 'failed',
          error: '无法连接雨课堂，请检查网络后重试',
        };
    });
    return { loginId: id };
  }

  function loginStatus(id) {
    const login = pendingLogins.get(id);
    return login
      ? { loginId: login.id, ...login.state }
      : { loginId: id, status: 'not_found' };
  }

  function cancelLogin(id) {
    const login = pendingLogins.get(id);
    if (!login) return;
    clearTimeout(login.timer);
    login.socket?.close();
    pendingLogins.delete(id);
  }

  async function status() {
    const session = await storedSession();
    if (!session) return { authenticated: false };
    try {
      const { user } = await validateCookie(session.cookie);
      return { authenticated: true, user };
    } catch (error) {
      if (error.code === 'AUTH_REQUIRED') {
        await updateSession(async (current) => {
          if (current?.cookie === session.cookie) await secureStore.clear();
        });
        return { authenticated: false };
      }
      throw error;
    }
  }

  async function logout() {
    const liveState = await stopLive();
    if (liveState.pendingSlides) {
      throw new RainClassroomError(
        'PENDING_SLIDES',
        '还有未写入的课堂页面，请重试后再退出登录',
      );
    }
    for (const id of [...pendingLogins.keys()]) cancelLogin(id);
    await updateSession(() => secureStore.clear());
    presentations.clear();
    return { authenticated: false };
  }

  async function withSession(fn) {
    const session = await storedSession();
    if (!session)
      throw new RainClassroomError('AUTH_REQUIRED', '请先登录雨课堂');
    return fn(session);
  }

  async function listCourses() {
    return withSession(async (session) => {
      const { payload } = await request('/v2/api/web/courses/list?identity=2', {
        cookie: session.cookie,
      });
      return normalizeCourses(payload);
    });
  }

  async function listLessons(classroomId) {
    if (typeof classroomId !== 'string' || !classroomId)
      throw new RainClassroomError('INVALID_ID', '课程标识无效');
    return withSession(async (session) => {
      const query = new URLSearchParams({
        actype: '-1',
        page: '0',
        offset: '100',
        sort: '-1',
      });
      const { payload } = await request(
        `/v2/api/web/logs/learn/${encodeURIComponent(classroomId)}?${query}`,
        { cookie: session.cookie },
      );
      return normalizeLessons(payload, classroomId);
    });
  }

  async function listCurrentLessons() {
    return withSession(async (session) => {
      const { payload } = await request('/api/v3/classroom/on-lesson', {
        cookie: session.cookie,
      });
      return normalizeCurrentLessons(payload);
    });
  }

  async function fetchPresentation(presentationId, authorization) {
    if (typeof presentationId !== 'string' || !presentationId)
      throw new RainClassroomError('INVALID_ID', '课件标识无效');
    return withSession(async (session) => {
      const query = new URLSearchParams({ presentation_id: presentationId });
      const { payload, response } = await request(
        `/api/v3/lesson/presentation/fetch?${query}`,
        {
          cookie: session.cookie,
          authorization: session.authorization || authorization,
        },
      );
      const latestAuth = response.headers.get('set-auth');
      const presentation = normalizePresentation(payload, presentationId);
      presentations.set(presentationId, presentation);
      return {
        ...presentation,
        authorization: latestAuth || authorization || null,
      };
    });
  }

  async function fetchLesson(lesson) {
    if (!lesson?.lessonId) return fetchPresentation(lesson?.presentationId);
    return withSession(async (session) => {
      const query = new URLSearchParams({ lesson_id: String(lesson.lessonId) });
      const { payload } = await request(
        `/api/v3/classroom-report/student/lesson-info?${query}`,
        { cookie: session.cookie },
      );
      const data = unwrapData(payload);
      if (!Array.isArray(data.presentationIds))
        throw new RainClassroomError(
          'PROTOCOL_CHANGED',
          '无法识别雨课堂课后报告课件列表',
        );
      const pages = [];
      for (const id of [
        ...new Set(data.presentationIds.filter((id) => id != null).map(String)),
      ]) {
        const deckQuery = new URLSearchParams({
          lesson_id: String(lesson.lessonId),
          presentationId: id,
        });
        const { payload: deckPayload } = await request(
          `/api/v3/classroom-report/student/ppt?${deckQuery}`,
          { cookie: session.cookie },
        );
        pages.push(
          ...normalizeSlides(unwrapData(deckPayload).slideList, id, true).sort(
            (a, b) => a.sourceIndex - b.sourceIndex,
          ),
        );
      }
      const visible = [
        ...new Map(
          pages.map((page) => [
            JSON.stringify([page.presentationId, page.pageId]),
            page,
          ]),
        ).values(),
      ];
      if (!visible.length)
        throw new RainClassroomError(
          'NO_VISIBLE_PAGES',
          '该课堂没有已开放的课件页面',
        );
      return { name: lesson.name || '', pages: visible };
    });
  }

  async function downloadImage(value) {
    const url = assertRainAssetUrl(value);
    const response = await fetchImpl(url, {
      redirect: 'error',
      signal: AbortSignal.timeout(30_000),
    });
    if (!response.ok)
      throw new RainClassroomError(
        'ASSET_FAILED',
        `课件图片下载失败（HTTP ${response.status}）`,
      );
    const type = (response.headers.get('content-type') || '')
      .split(';')[0]
      .toLowerCase();
    if (!['image/jpeg', 'image/png'].includes(type))
      throw new RainClassroomError(
        'INVALID_ASSET',
        '雨课堂页面不是可支持的 JPEG/PNG 图片',
      );
    const bytes = Buffer.from(await response.arrayBuffer());
    if (!bytes.length || bytes.length > MAX_IMAGE_BYTES)
      throw new RainClassroomError(
        'INVALID_ASSET',
        '雨课堂页面图片为空或超过 24 MB',
      );
    return { bytes, contentType: type };
  }

  function beginLiveStop(state) {
    state.stopped = true;
    state.stopping = true;
    clearTimeout(state.reconnectTimer);
    state.socket?.close();
  }

  function finishLiveStop(state) {
    if (!state.stopped || state.queuedMessages > 0) return;
    state.stopping = false;
    state.status = 'stopped';
    state.onState?.({
      status: 'stopped',
      pendingSlides: state.pendingSlides.size,
      error: state.error || null,
    });
  }

  async function stopLive() {
    const state = live;
    if (!state) return { status: 'stopped', pendingSlides: 0 };
    beginLiveStop(state);
    await state.eventQueue.catch(() => {});
    finishLiveStop(state);
    return {
      status: 'stopped',
      pendingSlides: state.pendingSlides.size,
      error: state.error || null,
    };
  }

  async function startLive({ lessonId, onSlide, onState }) {
    if (live) {
      if (live.stopping)
        throw new RainClassroomError(
          'LIVE_ACTIVE',
          '课堂正在停止并保存页面，请稍候',
        );
      if (live.stopped && !live.pendingSlides.size) live = null;
      else if (live.stopped)
        throw new RainClassroomError(
          'PENDING_SLIDES',
          '还有未写入的课堂页面，请重试后再开始新课堂',
        );
      else throw new RainClassroomError('LIVE_ACTIVE', '已有正在采集的雨课堂');
    }
    if (typeof lessonId !== 'string' || !lessonId)
      throw new RainClassroomError('INVALID_ID', '课堂标识无效');
    const session = await storedSession();
    if (!session)
      throw new RainClassroomError('AUTH_REQUIRED', '请先登录雨课堂');
    const { payload, response } = await request('/api/v3/lesson/checkin', {
      cookie: session.cookie,
      body: { source: 5, lessonId },
      headers: {
        Referer: `${ORIGIN}/lesson/fullscreen/v3/${encodeURIComponent(lessonId)}?source=5`,
      },
    });
    const data = payload.data || {};
    if (!data.lessonToken || data.identityId == null)
      throw new RainClassroomError(
        'CLASSROOM_JOIN_FAILED',
        payload.msg || '进入雨课堂失败',
      );
    const state = {
      status: 'connecting',
      lessonId,
      token: String(data.lessonToken),
      identityId: String(data.identityId),
      authorization: response.headers.get('set-auth') || null,
      attempts: 0,
      connections: 0,
      stopped: false,
      stopping: false,
      queuedMessages: 0,
      presentations: new Map(),
      lastTimelineKey: null,
      lastTimelinePageKey: null,
      initialTimelineReceived: false,
      initialReplayKeys: new Set(),
      possibleGap: false,
      pendingSlides: new Map(),
      onSlide,
      onState,
      socket: null,
      eventQueue: Promise.resolve(),
      reconnectTimer: null,
    };
    live = state;

    const handleSlide = async (event) => {
      const key = liveSlideKey(event);
      state.pendingSlides.set(key, event);
      let presentation = state.presentations.get(event.presentationId);
      if (!presentation) {
        presentation = await fetchPresentation(
          event.presentationId,
          state.authorization,
        );
        state.authorization = presentation.authorization || state.authorization;
        state.presentations.set(event.presentationId, presentation);
      }
      const page = presentation.pages.find((item) =>
        event.sourceIndex != null
          ? item.sourceIndex === event.sourceIndex
          : item.pageId === event.pageId,
      );
      if (!page)
        throw new RainClassroomError(
          'PAGE_NOT_FOUND',
          '课件中找不到时间线指定的页面',
        );
      const appended = await onSlide({
        ...page,
        presentationId: event.presentationId,
      });
      state.lastTimelineKey = `${event.presentationId}:${page.sourceIndex}`;
      state.lastTimelinePageKey = `${event.presentationId}:page:${page.pageId}`;
      state.pendingSlides.delete(key);
      state.initialReplayKeys.delete(key);
      state.latest = {
        presentationId: event.presentationId,
        sourceIndex: page.sourceIndex,
        projectPage: appended?.page ?? null,
        projectId: appended?.projectId ?? null,
        added: appended?.added ?? false,
        changedAt: Date.now(),
      };
    };

    const handleMessage = async (raw) => {
      if (
        ['presentationupdated', 'presentationcreated'].includes(raw.op) &&
        raw.presentation != null
      )
        state.presentations.delete(String(raw.presentation));
      const event = normalizeLiveMessage(raw);
      if (event.type === 'finished') {
        onState?.({
          status: 'finished',
          pendingSlides: state.pendingSlides.size,
        });
        beginLiveStop(state);
        return;
      }
      if (event.type === 'timeline') {
        const initial = !state.initialTimelineReceived;
        const replay = selectTimelineReplay(
          event.slides,
          state.lastTimelineKey,
          initial,
          state.lastTimelinePageKey,
        );
        state.initialTimelineReceived = true;
        if (replay.possibleGap) {
          state.possibleGap = true;
          onState?.({
            status: state.status,
            possibleGap: true,
            warning: '重连时间线未包含上次位置，可能存在缺页',
          });
        }
        for (const slide of replay.slides) {
          const key = liveSlideKey(slide);
          if (initial) state.initialReplayKeys.add(key);
          if (!state.pendingSlides.has(key))
            state.pendingSlides.set(key, slide);
        }
        for (const slide of [...state.pendingSlides.values()])
          await handleSlide(slide);
        return;
      }
      if (event.type === 'slide') {
        const key = liveSlideKey(event);
        if (!state.pendingSlides.has(key)) state.pendingSlides.set(key, event);
        for (const slide of [...state.pendingSlides.values()])
          await handleSlide(slide);
      }
    };

    state.retryPending = async () => {
      for (const slide of [...state.pendingSlides.values()])
        await handleSlide(slide);
    };

    const connect = () => {
      if (state.stopped || live !== state) return;
      let socket;
      try {
        socket = new WebSocketImpl('wss://www.yuketang.cn/wsapp/');
      } catch {
        state.attempts += 1;
        state.reconnectTimer = setTimeout(
          connect,
          Math.min(30_000, 1000 * 2 ** Math.min(state.attempts, 5)),
        );
        state.reconnectTimer.unref?.();
        return;
      }
      state.socket = socket;
      socket.addEventListener('open', () => {
        if (state.stopped) return socket.close();
        state.attempts = 0;
        state.connections += 1;
        state.status = 'active';
        onState?.({
          status: 'active',
          lessonId,
          possibleGap: state.possibleGap,
        });
        socket.send(
          JSON.stringify({
            op: 'hello',
            userid: state.identityId,
            role: 'student',
            auth: state.token,
            lessonid: lessonId,
          }),
        );
      });
      socket.addEventListener('message', (message) => {
        if (state.stopped) return;
        const raw = parseMessage(message);
        if (!raw) return;
        state.queuedMessages += 1;
        state.eventQueue = state.eventQueue
          .then(() => handleMessage(raw))
          .catch((error) => {
            state.error = error.message || '课件页面处理失败';
            state.status = state.stopped ? 'stopped' : 'error';
            onState?.({
              status: state.status,
              pendingSlides: state.pendingSlides.size,
              error: state.error,
            });
          })
          .finally(() => {
            state.queuedMessages -= 1;
            if (state.stopped) finishLiveStop(state);
          });
      });
      socket.addEventListener('close', () => {
        if (state.stopped || live !== state) return;
        state.attempts += 1;
        state.status = 'reconnecting';
        onState?.({ status: 'reconnecting', attempt: state.attempts });
        const delay = Math.min(30_000, 1000 * 2 ** Math.min(state.attempts, 5));
        state.reconnectTimer = setTimeout(connect, delay);
        state.reconnectTimer.unref?.();
      });
      socket.addEventListener('error', () => socket.close());
    };

    connect();
    return { status: 'connecting', lessonId };
  }

  return {
    startLogin,
    loginStatus,
    cancelLogin,
    status,
    logout,
    listCourses,
    listLessons,
    listCurrentLessons,
    fetchPresentation,
    fetchLesson,
    downloadImage,
    startLive,
    stopLive,
    retryLive: () => {
      if (!live?.retryPending)
        throw new RainClassroomError('LIVE_STOPPED', '当前没有正在采集的课堂');
      const state = live;
      const task = state.eventQueue
        .catch(() => {})
        .then(async () => {
          state.status = state.stopped ? 'stopped' : 'active';
          state.error = null;
          await state.retryPending();
          if (state.stopped) finishLiveStop(state);
        });
      state.eventQueue = task.catch((error) => {
        state.error = error.message || '课件页面处理失败';
        state.status = state.stopped ? 'stopped' : 'error';
        state.onState?.({
          status: state.status,
          pendingSlides: state.pendingSlides.size,
          error: state.error,
        });
      });
      return task;
    },
    getLiveStatus: () =>
      live
        ? {
            status: live.status || 'connecting',
            lessonId: live.lessonId,
            latest: live.latest || null,
            error: live.error || null,
            possibleGap: live.possibleGap || false,
            pendingSlides: live.pendingSlides?.size || 0,
            backfillPendingPages: live.initialReplayKeys?.size || 0,
            initialTimelineReceived: live.initialTimelineReceived,
          }
        : {
            status: 'stopped',
            pendingSlides: 0,
            latest: null,
            error: null,
            possibleGap: false,
          },
  };
}

module.exports = { createRainClassroomService };
