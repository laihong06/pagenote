export function selectedItem(value, items) {
  if (typeof value !== 'string' || !/^(0|[1-9]\d*)$/.test(value)) return null;
  const index = Number(value);
  return Number.isSafeInteger(index) && index < items.length
    ? (items[index] ?? null)
    : null;
}

export function currentLessonCourse(lesson, courses, selectedCourse = null) {
  if (!lesson) return null;
  if (!lesson.classroomId && !lesson.courseId) return selectedCourse;
  const matches = courses.filter(
    (course) =>
      (!lesson.classroomId || course.classroomId === lesson.classroomId) &&
      (!lesson.courseId || course.courseId === lesson.courseId),
  );
  return matches.length === 1 ? matches[0] : null;
}
