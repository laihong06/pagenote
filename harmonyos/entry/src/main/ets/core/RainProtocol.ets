import { url } from '@kit.ArkTS';

/**
 * 雨课堂协议层：类型、请求头与响应解析。
 * 与 Android feature-yuketang 的 RainProtocol.kt / RainHistory.kt 逐条对齐：
 * - 历史列表 type=3/14 的 courseware_id 作为 lessonId，id 不作为课件；
 * - 历史课件只导入明确标记可见的页面，缺必要数据即报错不静默跳页；
 * - 远端 ID 一律按字符串处理。
 */

export interface RainCourse {
  courseId: string;
  classroomId: string;
  name: string;
  teacher: string;
  term: string;
}

export interface RainLesson {
  classroomId: string;
  lessonId: string | null;
  presentationId: string;
  name: string;
  createdAt: string | null;
}

export interface RainSlide {
  pageId: string;
  sourceIndex: number;
  presentationId: string;
  imageUrl: string;
}

export interface RainSlideImage {
  slide: RainSlide;
  contentType: string;
  bytes: ArrayBuffer;
}

export interface RainCurrentLesson {
  lessonId: string;
  classroomId: string | null;
  courseId: string | null;
  name: string;
}

export class RainLessonOngoingError extends Error {
  constructor() {
    super('该课堂正在上课，请新建项目并开始实时采集');
    this.name = 'RainLessonOngoingError';
  }
}

type JsonObject = Record<string, Object | null>;

export function rainAsObject(value: Object | null | undefined): JsonObject | null {
  if (value === null || value === undefined || typeof value !== 'object' || Array.isArray(value)) {
    return null;
  }
  return value as JsonObject;
}

export function rainAsArray(value: Object | null | undefined): Array<Object | null> | null {
  if (value === null || value === undefined || !Array.isArray(value)) {
    return null;
  }
  return value as Array<Object | null>;
}

/** 远端 ID 规范化：非空、非 "null" 文本的字符串。 */
export function rainId(value: Object | null | undefined): string | null {
  if (value === null || value === undefined) {
    return null;
  }
  const text = value.toString();
  return text.length > 0 && text !== 'null' ? text : null;
}

export function rainIdOf(item: JsonObject, keys: string[]): string | null {
  for (const key of keys) {
    const value = rainId(item[key]);
    if (value !== null) {
      return value;
    }
  }
  return null;
}

export function rainInt(value: Object | null | undefined): number | null {
  if (value === null || value === undefined) {
    return null;
  }
  if (typeof value === 'number') {
    return Math.trunc(value);
  }
  if (typeof value === 'string') {
    const parsed = Number(value);
    return Number.isNaN(parsed) ? null : Math.trunc(parsed);
  }
  return null;
}

/** v2/v3 响应统一拆包：code/errcode 非 0 即失败；LESSON_ON 单独成类。 */
export function rainObjectData(payload: JsonObject): JsonObject {
  const code = rainInt(payload['code']) ?? rainInt(payload['errcode']) ?? 0;
  if (code !== 0) {
    const msg = payload['msg']?.toString() ?? payload['errmsg']?.toString() ?? `code ${code}`;
    if (msg === 'LESSON_ON') {
      throw new RainLessonOngoingError();
    }
    throw new Error(`雨课堂请求失败：${msg}`);
  }
  return rainAsObject(payload['data']) ?? payload;
}

export function rainBearerAuthorization(value: string | null): string | null {
  if (value === null || value.trim().length === 0) {
    return null;
  }
  let token = value.trim();
  if (token.toLowerCase().startsWith('bearer')) {
    token = token.substring(6).trim();
  }
  return token.length > 0 ? `Bearer ${token}` : null;
}

export function rainApiHeaders(cookie: string, authorization: string | null,
  referer: string, userAgent: string): Record<string, string> {
  const headers: Record<string, string> = {
    'Accept': 'application/json',
    'Referer': referer,
    'Origin': 'https://www.yuketang.cn',
    'User-Agent': userAgent,
    'xtbz': 'ykt',
    'X-Client': 'h5'
  };
  if (cookie.length > 0) {
    headers['Cookie'] = cookie;
  }
  const bearer = rainBearerAuthorization(authorization);
  if (bearer !== null) {
    headers['Authorization'] = bearer;
  }
  return headers;
}

export function rainHttpError(urlString: string, status: number, operation?: string): Error {
  let path = '/未知接口';
  try {
    path = new url.URL(urlString).pathname;
  } catch (e) {
    // 保留默认路径
  }
  return new Error(`${operation ?? '雨课堂请求失败'}（HTTP ${status}，${path}）`);
}

/** 历史课堂列表：type=3/14 的 courseware_id 作为 lessonId；presentation_id 直连课件。 */
export function normalizeHistoryLessons(payload: JsonObject, classroomId: string): RainLesson[] {
  const data = rainObjectData(payload);
  const activities = rainAsArray(data['activities']);
  if (activities === null) {
    throw new Error('无法识别雨课堂历史课件');
  }
  const lessons: RainLesson[] = [];
  for (const element of activities) {
    const item = rainAsObject(element);
    if (item === null) {
      continue;
    }
    const typeText = rainIdOf(item, ['type']);
    const type = typeText !== null ? Number(typeText) : null;
    const presentation = rainIdOf(item, ['presentation_id', 'presentationId']);
    if (type !== null && type !== 3 && type !== 14 && presentation === null) {
      continue;
    }
    let lessonId = rainIdOf(item, ['lesson_id', 'lessonId']);
    if (lessonId === null && (type === 3 || type === 14)) {
      lessonId = rainIdOf(item, ['courseware_id']);
    }
    if (lessonId === null && presentation === null) {
      continue;
    }
    lessons.push({
      classroomId: classroomId,
      lessonId: lessonId,
      presentationId: presentation ?? `report-${lessonId}`,
      name: item['title']?.toString() ?? item['name']?.toString() ?? '未命名课件',
      createdAt: rainIdOf(item, ['create_time', 'created_at'])
    });
  }
  return lessons;
}

function normalizeSlides(slides: Array<Object | null>, presentationId: string,
  visibleOnly: boolean): RainSlide[] {
  const result: RainSlide[] = [];
  for (let order = 0; order < slides.length; order++) {
    const item = rainAsObject(slides[order]);
    if (item === null) {
      continue;
    }
    if (visibleOnly) {
      const visible = item['visible']?.toString().toLowerCase();
      if (visible !== 'true' && visible !== '1') {
        continue;
      }
    }
    const pageId = rainIdOf(item, ['id', 'pageId', 'page_id', 'slideId', 'slide_id']);
    let image = '';
    const cover = item['cover'];
    if (typeof cover === 'string') {
      image = cover;
    } else {
      const coverObject = rainAsObject(cover);
      if (coverObject !== null) {
        image = coverObject['url']?.toString() ?? coverObject['src']?.toString()
          ?? coverObject['image_url']?.toString() ?? '';
      } else {
        image = item['imageUrl']?.toString() ?? item['image_url']?.toString() ?? '';
      }
    }
    if (pageId === null || image.length === 0) {
      if (visibleOnly) {
        throw new Error('雨课堂已开放的课件页面数据不完整');
      }
      continue;
    }
    const absolute = new url.URL(image, 'https://www.yuketang.cn').toString();
    result.push({
      pageId: pageId,
      sourceIndex: rainInt(item['index']) ?? order,
      presentationId: presentationId,
      imageUrl: absolute
    });
  }
  return result;
}

/** presentation/fetch 的整份课件页列表（无可见性过滤，空列表报错）。 */
export function normalizeRainPresentation(payload: JsonObject, presentationId: string): RainSlide[] {
  const slides = rainAsArray(rainObjectData(payload)['slides']);
  if (slides === null) {
    throw new Error('无法识别雨课堂课件页面');
  }
  const result = normalizeSlides(slides, presentationId, false);
  if (result.length === 0) {
    throw new Error('课件没有可用页面');
  }
  return result;
}

/** 课后报告课件页：按课件顺序与源页序，只保留可见页并去重。 */
export function normalizeReportSlides(payload: JsonObject, presentationId: string): RainSlide[] {
  const pages = rainAsArray(rainObjectData(payload)['slideList']);
  if (pages === null) {
    throw new Error('无法识别雨课堂课后课件页面');
  }
  return normalizeSlides(pages, presentationId, true)
    .sort((a: RainSlide, b: RainSlide): number => a.sourceIndex - b.sourceIndex);
}

/** 当前课堂列表：缺少 status 的项仍为当前课堂，显式非进行中状态不展示。 */
export function normalizeCurrentLessons(payload: JsonObject): RainCurrentLesson[] {
  const data = rainObjectData(payload);
  const list = rainAsArray(data['onLessonClassrooms']) ?? rainAsArray(data['on_lesson_classrooms'])
    ?? rainAsArray(data['result']);
  if (list === null) {
    throw new Error('无法识别雨课堂当前课堂列表');
  }
  const result: RainCurrentLesson[] = [];
  for (const element of list) {
    const item = rainAsObject(element);
    if (item === null) {
      continue;
    }
    const status = item['status'];
    if (status !== null && status !== undefined && status.toString() !== 'true' && status.toString() !== '1') {
      continue;
    }
    const lessonId = rainIdOf(item, ['lessonId', 'lesson_id', 'id']);
    if (lessonId === null) {
      continue;
    }
    const course = rainAsObject(item['course']);
    const classroom = rainAsObject(item['classroom']);
    const name = rainIdOf(item, ['name', 'title', 'lessonName', 'lesson_name'])
      ?? (course !== null ? rainIdOf(course, ['name']) : null)
      ?? rainIdOf(item, ['classroomName', 'classroom_name', 'courseName', 'course_name'])
      ?? '正在进行的课堂';
    result.push({
      lessonId: lessonId,
      classroomId: rainIdOf(item, ['classroomId', 'classroom_id'])
        ?? (classroom !== null ? rainIdOf(classroom, ['id']) : null),
      courseId: rainIdOf(item, ['courseId', 'course_id'])
        ?? (course !== null ? rainIdOf(course, ['courseId', 'course_id', 'id']) : null),
      name: name
    });
  }
  return result;
}
