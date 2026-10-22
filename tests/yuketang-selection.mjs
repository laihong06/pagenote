import assert from 'node:assert/strict';
import {
  currentLessonCourse,
  selectedItem,
} from '../src/rain-classroom-selection.js';

const values = ['course', 'lesson'];
assert.equal(selectedItem('', values), null);
assert.equal(selectedItem('0', values), 'course');
assert.equal(selectedItem('1', values), 'lesson');
assert.equal(selectedItem('2', values), null);
assert.equal(selectedItem('not-an-index', values), null);

const courses = [
  { courseId: 'c1', classroomId: 'room1', name: 'Course 1' },
  { courseId: 'c2', classroomId: 'room2', name: 'Course 2' },
];
assert.equal(
  currentLessonCourse({ classroomId: 'room2' }, courses),
  courses[1],
);
assert.equal(
  currentLessonCourse({ classroomId: 'missing', courseId: 'c1' }, courses),
  null,
  'contradicting classroom and course IDs must not link by either field alone',
);
assert.equal(
  currentLessonCourse({ classroomId: 'room1', courseId: 'c1' }, courses),
  courses[0],
);
assert.equal(
  currentLessonCourse(
    { courseId: 'missing', classroomId: 'missing' },
    courses,
    courses[0],
  ),
  null,
);
assert.equal(
  currentLessonCourse({ lessonId: 'l1' }, courses, courses[0]),
  courses[0],
);
assert.equal(currentLessonCourse(null, courses, courses[0]), null);
assert.equal(
  currentLessonCourse(
    { classroomId: 'room1', courseId: 'live-course-alias' },
    courses,
  ),
  courses[0],
  'the unique classroom identity is authoritative when the two endpoints expose different course namespaces',
);

console.log('Yuketang selection tests passed');
