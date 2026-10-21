const ORIGIN = 'https://www.yuketang.cn';

class RainClassroomError extends Error {
  constructor(code, message) {
    super(message);
    this.name = 'RainClassroomError';
    this.code = code;
  }
}

function asId(value) {
  if (typeof value === 'string' && value.trim()) return value.trim();
  if (typeof value === 'number' && Number.isFinite(value)) return String(value);
  return null;
}

function asIndex(value, fallback = null) {
  const index = typeof value === 'number' ? value : Number(value);
  return Number.isInteger(index) && index >= 0 ? index : fallback;
}

function unwrapData(payload) {
  if (!payload || typeof payload !== 'object')
    throw new RainClassroomError(
      'PROTOCOL_CHANGED',
      '雨课堂返回了无法识别的数据',
    );
  if (payload.code !== undefined && Number(payload.code) !== 0)
    throw new RainClassroomError(
      payload.code === 401 || payload.code === 403
        ? 'AUTH_REQUIRED'
        : 'REMOTE_ERROR',
      String(payload.msg || payload.message || '雨课堂请求失败'),
    );
  return payload.data ?? payload;
}

function normalizeUser(payload) {
  const data = unwrapData(payload);
  const candidate = Array.isArray(data) ? data[0] : data.user || data;
  const name = candidate?.name || candidate?.nickname || candidate?.username;
  const id = asId(candidate?.id ?? candidate?.user_id ?? candidate?.userid);
  if (!name && !id)
    throw new RainClassroomError('PROTOCOL_CHANGED', '无法识别雨课堂账号信息');
  return { id, name: String(name || '雨课堂用户') };
}

function normalizeCourses(payload) {
  const data = unwrapData(payload);
  const list = Array.isArray(data.list)
    ? data.list
    : Array.isArray(data)
      ? data
      : null;
  if (!list)
    throw new RainClassroomError('PROTOCOL_CHANGED', '无法识别雨课堂课程列表');
  return list
    .map((item) => {
      const classroomId = asId(item.classroom_id ?? item.classroomId);
      const course =
        item.course && typeof item.course === 'object' ? item.course : {};
      const teacher =
        item.teacher && typeof item.teacher === 'object' ? item.teacher : {};
      const courseId = asId(
        item.course_id ?? item.courseId ?? course.id ?? item.id,
      );
      if (!classroomId || !courseId) return null;
      return {
        courseId,
        classroomId,
        name: String(
          item.name || course.name || item.classroom_name || '未命名课程',
        ),
        teacher: String(teacher.name || item.teacher_name || ''),
        term: String(item.term_name || item.term || ''),
      };
    })
    .filter(Boolean);
}

function normalizeLessons(payload, classroomId) {
  const data = unwrapData(payload);
  const list = Array.isArray(data.activities) ? data.activities : null;
  if (!list)
    throw new RainClassroomError(
      'PROTOCOL_CHANGED',
      '无法识别雨课堂历史课件列表',
    );
  return list
    .map((item) => {
      const presentationId = asId(
        item.presentation_id ?? item.presentationId ?? item.courseware_id,
      );
      if (!presentationId) return null;
      return {
        classroomId: String(classroomId),
        lessonId: asId(item.lesson_id ?? item.lessonId),
        presentationId,
        name: String(item.title || item.name || '未命名课件'),
        createdAt: item.create_time ?? item.created_at ?? null,
        status: item.status ?? null,
      };
    })
    .filter(Boolean);
}

function normalizeCurrentLessons(payload) {
  const data = unwrapData(payload);
  const list = Array.isArray(data.onLessonClassrooms)
    ? data.onLessonClassrooms
    : Array.isArray(data.result)
      ? data.result
      : Array.isArray(data)
        ? data
        : null;
  if (!list)
    throw new RainClassroomError(
      'PROTOCOL_CHANGED',
      '无法识别雨课堂当前课堂列表',
    );
  return list
    .filter((item) => String(item?.status) === '1')
    .map((item) => {
      const lessonId = asId(item.lessonId ?? item.lesson_id ?? item.id);
      if (!lessonId) return null;
      const course =
        item.course && typeof item.course === 'object' ? item.course : {};
      return {
        lessonId,
        classroomId: asId(item.classroomId ?? item.classroom_id),
        courseId: asId(item.courseId ?? item.course_id ?? course.id),
        name: String(
          item.name ||
            item.title ||
            item.lesson_name ||
            course.name ||
            '正在进行的课堂',
        ),
      };
    })
    .filter(Boolean);
}

function slideImageUrl(slide) {
  const candidate =
    slide.cover ?? slide.imageUrl ?? slide.image_url ?? slide.coverAlt;
  if (typeof candidate === 'string') return candidate;
  if (candidate && typeof candidate === 'object')
    return candidate.url || candidate.src || candidate.image_url || null;
  return null;
}

function normalizePresentation(payload, presentationId) {
  const data = unwrapData(payload);
  if (!Array.isArray(data.slides))
    throw new RainClassroomError('PROTOCOL_CHANGED', '无法识别雨课堂课件页面');
  const pages = data.slides
    .map((slide, order) => {
      const pageId = asId(slide.id ?? slide.pageId ?? slide.page_id);
      const url = slideImageUrl(slide);
      if (!pageId || !url) return null;
      return {
        pageId,
        sourceIndex: asIndex(slide.index, order),
        imageUrl: new URL(url, ORIGIN).href,
      };
    })
    .filter(Boolean);
  if (!pages.length)
    throw new RainClassroomError('PROTOCOL_CHANGED', '课件没有可用的页面图片');
  return {
    presentationId: String(presentationId),
    name: String(data.title || data.name || ''),
    pages,
  };
}

function normalizeLiveMessage(message) {
  const op = String(message?.op || '');
  if (op === 'lessonfinished') return { type: 'finished' };
  if (['hello', 'fetchtimeline'].includes(op)) {
    const timeline = Array.isArray(message.timeline) ? message.timeline : [];
    const slides = timeline
      .map((item) => ({
        presentationId: asId(item?.pres),
        sourceIndex: asIndex(item?.si),
      }))
      .filter((item) => item.presentationId && item.sourceIndex !== null);
    const current = {
      presentationId: asId(message.presentation),
      sourceIndex: asIndex(message.slideindex),
    };
    if (
      current.presentationId &&
      current.sourceIndex !== null &&
      (slides.at(-1)?.presentationId !== current.presentationId ||
        slides.at(-1)?.sourceIndex !== current.sourceIndex)
    )
      slides.push(current);
    return { type: 'timeline', slides };
  }
  if (
    [
      'showpresentation',
      'presentationupdated',
      'presentationcreated',
      'showfinished',
    ].includes(op)
  ) {
    const presentationId = asId(message.presentation);
    const sourceIndex = asIndex(message.slideindex);
    return presentationId && sourceIndex !== null
      ? { type: 'slide', presentationId, sourceIndex }
      : { type: 'state' };
  }
  if (op === 'slidenav') {
    const presentationId = asId(message.slide?.pres);
    const sourceIndex = asIndex(message.slide?.si);
    return presentationId && sourceIndex !== null
      ? { type: 'slide', presentationId, sourceIndex }
      : { type: 'state' };
  }
  return { type: 'ignored' };
}

function selectTimelineReplay(slides, cursor, initialConnection) {
  const valid = Array.isArray(slides) ? slides : [];
  if (!valid.length) return { slides: [], possibleGap: false };
  if (initialConnection) return { slides: [valid.at(-1)], possibleGap: false };
  if (cursor) {
    const cursorIndex = valid.findLastIndex(
      (slide) => `${slide.presentationId}:${slide.sourceIndex}` === cursor,
    );
    if (cursorIndex >= 0)
      return { slides: valid.slice(cursorIndex + 1), possibleGap: false };
  }
  return { slides: [valid.at(-1)], possibleGap: true };
}

function assertRainAssetUrl(value) {
  let url;
  try {
    url = new URL(value, ORIGIN);
  } catch {
    throw new RainClassroomError('INVALID_ASSET', '雨课堂课件图片地址无效');
  }
  if (url.protocol !== 'https:' || !/(^|\.)yuketang\.cn$/i.test(url.hostname))
    throw new RainClassroomError(
      'INVALID_ASSET',
      '课件图片来自不受支持的资源域名',
    );
  return url;
}

function assertRainQrTicketUrl(value) {
  let url;
  try {
    url = new URL(value, ORIGIN);
  } catch {
    throw new RainClassroomError('INVALID_QR', '雨课堂二维码地址无效');
  }
  const isYuketang =
    url.hostname === 'yuketang.cn' || url.hostname.endsWith('.yuketang.cn');
  const isWechatQr =
    url.hostname === 'mp.weixin.qq.com' &&
    url.pathname === '/cgi-bin/showqrcode';
  if (
    url.protocol !== 'https:' ||
    url.username ||
    url.password ||
    (url.port && url.port !== '443') ||
    (!isYuketang && !isWechatQr)
  )
    throw new RainClassroomError('INVALID_QR', '雨课堂二维码地址无效');
  return url;
}

module.exports = {
  ORIGIN,
  RainClassroomError,
  asId,
  normalizeUser,
  normalizeCourses,
  normalizeLessons,
  normalizeCurrentLessons,
  normalizePresentation,
  normalizeLiveMessage,
  selectTimelineReplay,
  assertRainAssetUrl,
  assertRainQrTicketUrl,
};
