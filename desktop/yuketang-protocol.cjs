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
  if (
    value == null ||
    typeof value === 'boolean' ||
    (typeof value === 'string' && !value.trim())
  )
    return fallback;
  const index = typeof value === 'number' ? value : Number(value);
  return Number.isInteger(index) && index >= 0 ? index : fallback;
}

function unwrapData(payload) {
  if (!payload || typeof payload !== 'object')
    throw new RainClassroomError(
      'PROTOCOL_CHANGED',
      '雨课堂返回了无法识别的数据',
    );
  const code = payload.code ?? payload.errcode;
  if (code !== undefined && Number(code) !== 0)
    throw new RainClassroomError(
      (payload.msg || payload.errmsg) === 'LESSON_ON'
        ? 'LESSON_ON'
        : Number(code) === 401 || Number(code) === 403
          ? 'AUTH_REQUIRED'
          : 'REMOTE_ERROR',
      (payload.msg || payload.errmsg) === 'LESSON_ON'
        ? '该课堂正在上课，请选择正在进行的课堂并开始实时采集'
        : String(
            payload.msg ||
              payload.errmsg ||
              payload.message ||
              '雨课堂请求失败',
          ),
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
      const presentationId = asId(item.presentation_id ?? item.presentationId);
      const reportType = [3, 14].includes(Number(item.type));
      if (item.type != null && !reportType && !presentationId) return null;
      const lessonId = asId(
        item.lesson_id ??
          item.lessonId ??
          (reportType ? item.courseware_id : null),
      );
      if (!presentationId && !lessonId) return null;
      return {
        classroomId: String(classroomId),
        lessonId,
        presentationId: presentationId || `report-${lessonId}`,
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
    : Array.isArray(data.on_lesson_classrooms)
      ? data.on_lesson_classrooms
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
    .filter(
      (item) =>
        item &&
        typeof item === 'object' &&
        !Array.isArray(item) &&
        (item.status === undefined ||
          item.status === null ||
          item.status === true ||
          String(item.status) === '1'),
    )
    .map((item) => {
      const lessonId = asId(item.lessonId ?? item.lesson_id ?? item.id);
      if (!lessonId) return null;
      const course =
        item.course && typeof item.course === 'object' ? item.course : {};
      return {
        lessonId,
        classroomId: asId(
          item.classroomId ?? item.classroom_id ?? item.classroom?.id,
        ),
        courseId: asId(
          item.courseId ??
            item.course_id ??
            course.courseId ??
            course.course_id ??
            course.id,
        ),
        name: String(
          item.name ||
            item.title ||
            item.lessonName ||
            item.lesson_name ||
            course.name ||
            item.classroomName ||
            item.classroom_name ||
            item.courseName ||
            item.course_name ||
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

function normalizeSlides(slides, presentationId, visibleOnly = false) {
  if (!Array.isArray(slides))
    throw new RainClassroomError('PROTOCOL_CHANGED', '无法识别雨课堂课件页面');
  const pages = slides
    .map((slide, order) => {
      if (visibleOnly && ![true, 1, 'true', '1'].includes(slide.visible))
        return null;
      const pageId = asId(
        slide.id ??
          slide.pageId ??
          slide.page_id ??
          slide.slideId ??
          slide.slide_id,
      );
      const url = slideImageUrl(slide);
      if (!pageId || !url) {
        if (visibleOnly)
          throw new RainClassroomError(
            'PROTOCOL_CHANGED',
            '雨课堂已开放的课件页面数据不完整',
          );
        return null;
      }
      return {
        pageId,
        presentationId: String(presentationId),
        sourceIndex: asIndex(slide.index, order),
        imageUrl: new URL(url, ORIGIN).href,
      };
    })
    .filter(Boolean);
  return pages;
}

function normalizePresentation(payload, presentationId) {
  const data = unwrapData(payload);
  const pages = normalizeSlides(data.slides, presentationId);
  if (!pages.length)
    throw new RainClassroomError('PROTOCOL_CHANGED', '课件没有可用的页面图片');
  return {
    presentationId: String(presentationId),
    name: String(data.title || data.name || ''),
    pages,
  };
}

function liveSlideKey(slide) {
  return slide.sourceIndex != null
    ? `${slide.presentationId}:${slide.sourceIndex}`
    : `${slide.presentationId}:page:${slide.pageId}`;
}

function liveSlideReference(item) {
  const presentationId = asId(item?.pres);
  const sourceIndex = asIndex(item?.si);
  const pageId = sourceIndex == null ? asId(item?.sid) : null;
  if (!presentationId || (sourceIndex == null && !pageId)) return null;
  return { presentationId, sourceIndex, ...(pageId ? { pageId } : {}) };
}

function normalizeLiveMessage(message) {
  const op = String(message?.op || '');
  if (op === 'lessonfinished') return { type: 'finished' };
  if (['hello', 'fetchtimeline'].includes(op)) {
    const timeline = Array.isArray(message.timeline) ? message.timeline : [];
    const slides = timeline
      .filter((item) => item && (item.type == null || item.type === 'slide'))
      .map(liveSlideReference)
      .filter(Boolean);
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
    const slide = liveSlideReference({
      pres: message.presentation ?? message.slide?.pres,
      si: message.slideindex ?? message.slide?.si,
      sid: message.slide?.sid,
    });
    return slide ? { type: 'slide', ...slide } : { type: 'state' };
  }
  if (op === 'slidenav') {
    const slide = liveSlideReference(message.slide);
    return slide ? { type: 'slide', ...slide } : { type: 'state' };
  }
  return { type: 'ignored' };
}

function selectTimelineReplay(
  slides,
  cursor,
  initialConnection,
  pageCursor = null,
) {
  const valid = Array.isArray(slides) ? slides : [];
  if (!valid.length) return { slides: [], possibleGap: false };
  if (initialConnection)
    return {
      slides: [
        ...new Map(valid.map((slide) => [liveSlideKey(slide), slide])).values(),
      ],
      possibleGap: false,
    };
  if (cursor || pageCursor) {
    const cursorIndex = valid.findLastIndex(
      (slide) =>
        liveSlideKey(slide) === cursor || liveSlideKey(slide) === pageCursor,
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
  normalizeSlides,
  unwrapData,
  liveSlideKey,
  normalizeLiveMessage,
  selectTimelineReplay,
  assertRainAssetUrl,
  assertRainQrTicketUrl,
};
