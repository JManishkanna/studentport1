-- Programme-aligned catalogue: subjects, faculty, timetable and enrolments.
USE academy_portal;

START TRANSACTION;

-- 1. Reuse subject ids 1-8, add 9-16, so the existing FK rows survive.
UPDATE subjects SET code='JAVC', name='Java Core and OOP',        department='Full Stack Java',   credits=5 WHERE id=1;
UPDATE subjects SET code='SPRB', name='Spring Boot REST APIs',    department='Full Stack Java',   credits=5 WHERE id=2;
UPDATE subjects SET code='HNBT', name='Hibernate and JPA',        department='Full Stack Java',   credits=4 WHERE id=3;
UPDATE subjects SET code='JSFW', name='JavaScript Frontend',      department='Full Stack Java',   credits=4 WHERE id=4;
UPDATE subjects SET code='PYTF', name='Python Foundations',       department='Full Stack Python', credits=5 WHERE id=5;
UPDATE subjects SET code='DJNG', name='Django Web Development',   department='Full Stack Python', credits=5 WHERE id=6;
UPDATE subjects SET code='FSTA', name='FastAPI Microservices',    department='Full Stack Python', credits=4 WHERE id=7;
UPDATE subjects SET code='REAC', name='React Frontend Basics',    department='Full Stack Python', credits=4 WHERE id=8;
INSERT IGNORE INTO subjects (id, code, name, department, credits) VALUES
 (9,  'STAP', 'Applied Statistics',      'Data Analysis', 5),
 (10, 'SQLA', 'SQL for Analysts',        'Data Analysis', 4),
 (11, 'PYDT', 'Python for Data',         'Data Analysis', 5),
 (12, 'DAVZ', 'Data Visualisation',      'Data Analysis', 4),
 (13, 'DFND', 'Design Foundations',      'UI/UX',         4),
 (14, 'UXRS', 'UX Research Methods',     'UI/UX',         4),
 (15, 'IDGN', 'Interaction Design',      'UI/UX',         5),
 (16, 'PFTP', 'Prototyping with Figma',  'UI/UX',         4);

-- 2. Faculty follow the tracks they now teach.
UPDATE teachers SET department='Full Stack Java',   specialization='Java, Spring Boot',        qualification='M.Tech CSE, 14 yrs'        WHERE id=1;
UPDATE teachers SET department='Data Analysis',     specialization='Statistics, Python for Data', qualification='M.Sc. Statistics, Ph.D.' WHERE id=2;
UPDATE teachers SET department='Full Stack Python', specialization='Django, FastAPI',          qualification='MCA, 9 yrs'               WHERE id=3;
UPDATE teachers SET department='UI/UX',             specialization='Interaction Design, Figma', qualification='M.Des Interaction Design'  WHERE id=4;
UPDATE teachers SET department='Full Stack Java',   specialization='Hibernate, JavaScript',    qualification='B.E. Computer Science'      WHERE id=5;
UPDATE teachers SET department='Data Analysis',     specialization='SQL, Data Visualisation',  qualification='M.Sc. Data Science'         WHERE id=6;
UPDATE teachers SET department='UI/UX',             specialization='UX Research, Design Foundations', qualification='M.A. Human-Computer Interaction' WHERE id=7;

-- 3. Each track owns four teaching slots; a section walks them in order, section B starting
--    two slots later so the two halves of a programme rarely need the same faculty member.
CREATE TEMPORARY TABLE track_slot (
    class_level VARCHAR(30), pos TINYINT, subject_id BIGINT, teacher_id BIGINT
);
INSERT INTO track_slot VALUES
 ('Full Stack Java',   0,  1, 1), ('Full Stack Java',   1,  3, 5), ('Full Stack Java',   2,  4, 5), ('Full Stack Java',   3,  2, 1),
 ('Full Stack Python', 0,  5, 3), ('Full Stack Python', 1,  6, 3), ('Full Stack Python', 2,  7, 3), ('Full Stack Python', 3,  8, 3),
 ('Data Analysis',     0,  9, 2), ('Data Analysis',     1, 10, 6), ('Data Analysis',     2, 12, 6), ('Data Analysis',     3, 11, 2),
 ('UI/UX',             0, 15, 4), ('UI/UX',             1, 13, 7), ('UI/UX',             2, 14, 7), ('UI/UX',             3, 16, 4);

UPDATE class_schedules cs
JOIN (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY class_level, section ORDER BY day_of_week, start_time) - 1 AS idx
    FROM class_schedules
) ranked ON ranked.id = cs.id
JOIN track_slot ts ON ts.class_level = cs.class_level
        AND ts.pos = (ranked.idx + IF(cs.section = 'A', 0, 2)) % 4
SET cs.subject_id = ts.subject_id, cs.teacher_id = ts.teacher_id;

-- 4. Enrol every student in three of their own track's four subjects.
DELETE FROM enrollments;
INSERT INTO enrollments (student_id, subject_id, enrolled_at)
SELECT st.id, ts.subject_id, st.admission_date
FROM students st
JOIN (SELECT id, ROW_NUMBER() OVER (PARTITION BY class_level ORDER BY id) - 1 AS seat FROM students) seat
  ON seat.id = st.id
JOIN track_slot ts ON ts.class_level = st.class_level
     AND ts.pos != (seat.seat + st.id) % 4;

-- 5. Two section-B slots landed on faculty who were already teaching section A in the
--    same hour; re-picking a different subject from the same track frees both faculty.
UPDATE class_schedules SET subject_id = 1, teacher_id = 1 WHERE id = 13;
UPDATE class_schedules SET subject_id = 3, teacher_id = 5 WHERE id = 14;

-- The two double-period slots were school games/assembly slots; they now carry a
-- lab block and a Data Analysis lecture, so they need real teaching rooms.
UPDATE class_schedules SET room = 'Lab 1'        WHERE id = 11;
UPDATE class_schedules SET room = 'Block B - 101' WHERE id = 35;

-- Sanity checks: every one of these must return zero.
SELECT COUNT(*) AS teacher_clashes FROM class_schedules a JOIN class_schedules b
  ON a.teacher_id = b.teacher_id AND a.id < b.id AND a.day_of_week = b.day_of_week
 AND a.start_time < b.end_time AND b.start_time < a.end_time;
SELECT COUNT(*) AS class_overlaps FROM class_schedules a JOIN class_schedules b
  ON a.class_level = b.class_level AND a.section = b.section AND a.id < b.id
 AND a.day_of_week = b.day_of_week AND a.start_time < b.end_time AND b.start_time < a.end_time;
SELECT COUNT(*) AS off_track FROM class_schedules cs JOIN subjects s ON s.id = cs.subject_id
 WHERE s.department <> cs.class_level;
SELECT COUNT(*) AS enrol_off_track FROM enrollments e JOIN students st ON st.id = e.student_id
 JOIN subjects s ON s.id = e.subject_id WHERE s.department <> st.class_level;

COMMIT;
