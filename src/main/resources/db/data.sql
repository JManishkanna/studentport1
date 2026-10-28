-- =====================================================================
-- Academy Student Management Portal :: demo seed data
-- Subjects, faculty, timetable and enrolments are aligned to the four
-- programmes: UI/UX, Data Analysis, Full Stack Java, Full Stack Python.
-- Idempotent: uses INSERT IGNORE so re-running does not duplicate rows.
-- =====================================================================

INSERT IGNORE INTO teachers (id, employee_code, first_name, last_name, designation, department, email, phone, specialization, qualification, office_room, joining_date, status) VALUES
 (1, 'ACAD-T-001', 'Suresh', 'Babu', 'Head of Department', 'Full Stack Java', 'suresh.babu@academy.edu', '+91 98450 11001', 'Java, Spring Boot', 'M.Tech CSE, 14 yrs', 'Block A - 101', '2011-06-01', 'ACTIVE'),
 (2, 'ACAD-T-002', 'Kavita', 'Anand', 'Senior Faculty', 'Data Analysis', 'kavita.anand@academy.edu', '+91 98450 11002', 'Statistics, Python for Data', 'M.Sc. Statistics, Ph.D.', 'Block A - 104', '2014-04-15', 'ACTIVE'),
 (3, 'ACAD-T-003', 'Neelam', 'Kaur', 'Faculty', 'Full Stack Python', 'neelam.kaur@academy.edu', '+91 98450 11003', 'Django, FastAPI', 'MCA, 9 yrs', 'Block B - 202', '2016-07-20', 'ACTIVE'),
 (4, 'ACAD-T-004', 'Thomas', 'Mathew', 'Faculty', 'UI/UX', 'thomas.mathew@academy.edu', '+91 98450 11004', 'Interaction Design, Figma', 'M.Des Interaction Design', 'Block C - 305', '2018-01-08', 'ACTIVE'),
 (5, 'ACAD-T-005', 'Priya', 'Desai', 'Faculty', 'Full Stack Java', 'priya.desai@academy.edu', '+91 98450 11005', 'Hibernate, JavaScript', 'B.E. Computer Science', 'Block B - 210', '2015-09-01', 'ACTIVE'),
 (6, 'ACAD-T-006', 'Ramesh', 'Naik', 'Faculty', 'Data Analysis', 'ramesh.naik@academy.edu', '+91 98450 11006', 'SQL, Data Visualisation', 'M.Sc. Data Science', 'Block B - 215', '2017-03-12', 'ACTIVE'),
 (7, 'ACAD-T-007', 'Farida', 'Khan', 'Faculty', 'UI/UX', 'farida.khan@academy.edu', '+91 98450 11007', 'UX Research, Design Foundations', 'M.A. Human-Computer Interaction', 'Block A - 108', '2019-06-24', 'ACTIVE');

INSERT IGNORE INTO subjects (id, code, name, department, credits) VALUES
 (1, 'JAVC', 'Java Core and OOP', 'Full Stack Java', 5),
 (2, 'SPRB', 'Spring Boot REST APIs', 'Full Stack Java', 5),
 (3, 'HNBT', 'Hibernate and JPA', 'Full Stack Java', 4),
 (4, 'JSFW', 'JavaScript Frontend', 'Full Stack Java', 4),
 (5, 'PYTF', 'Python Foundations', 'Full Stack Python', 5),
 (6, 'DJNG', 'Django Web Development', 'Full Stack Python', 5),
 (7, 'FSTA', 'FastAPI Microservices', 'Full Stack Python', 4),
 (8, 'REAC', 'React Frontend Basics', 'Full Stack Python', 4),
 (9, 'STAP', 'Applied Statistics', 'Data Analysis', 5),
 (10, 'SQLA', 'SQL for Analysts', 'Data Analysis', 4),
 (11, 'PYDT', 'Python for Data', 'Data Analysis', 5),
 (12, 'DAVZ', 'Data Visualisation', 'Data Analysis', 4),
 (13, 'DFND', 'Design Foundations', 'UI/UX', 4),
 (14, 'UXRS', 'UX Research Methods', 'UI/UX', 4),
 (15, 'IDGN', 'Interaction Design', 'UI/UX', 5),
 (16, 'PFTP', 'Prototyping with Figma', 'UI/UX', 4);

INSERT IGNORE INTO students (id, roll_number, first_name, last_name, email, phone, date_of_birth, gender, class_level, section, address_line, city, guardian_name, guardian_phone, guardian_email, admission_date, status) VALUES
 (1, 'ACAD-1101', 'Aarav', 'Menon', 'aarav.menon@student.academy.edu', '+91 90000 10101', '2009-04-12', 'MALE', 'Full Stack Java', 'A', '12 Palm Grove, Indiranagar', 'Bengaluru', 'Ravi Menon', '+91 90000 20101', 'ravi.menon@gmail.com', '2024-06-10', 'ACTIVE'),
 (2, 'ACAD-1102', 'Diya', 'Sharma', 'diya.sharma@student.academy.edu', '+91 90000 10102', '2009-08-03', 'FEMALE', 'Full Stack Java', 'A', '45 MG Road, Sector 2', 'Bengaluru', 'Nikhil Sharma', '+91 90000 20102', 'nikhil.sharma@gmail.com', '2024-06-10', 'ACTIVE'),
 (3, 'ACAD-1103', 'Rohan', 'Iyer', 'rohan.iyer@student.academy.edu', '+91 90000 10103', '2009-01-25', 'MALE', 'Full Stack Java', 'B', '7 Temple Street, Jayanagar', 'Bengaluru', 'Ganesh Iyer', '+91 90000 20103', 'ganesh.iyer@gmail.com', '2024-06-12', 'ACTIVE'),
 (4, 'ACAD-1104', 'Ananya', 'Nair', 'ananya.nair@student.academy.edu', '+91 90000 10104', '2009-11-30', 'FEMALE', 'Full Stack Java', 'B', '88 Coral Ridge, Koramangala', 'Bengaluru', 'Vinod Nair', '+91 90000 20104', 'vinod.nair@gmail.com', '2024-06-12', 'ACTIVE'),
 (5, 'ACAD-1201', 'Vivaan', 'Gupta', 'vivaan.gupta@student.academy.edu', '+91 90000 10105', '2008-03-17', 'MALE', 'Full Stack Python', 'A', '3 Lake View, Whitefield', 'Bengaluru', 'Amit Gupta', '+91 90000 20105', 'amit.gupta@gmail.com', '2023-06-05', 'ACTIVE'),
 (6, 'ACAD-1202', 'Ishita', 'Rao', 'ishita.rao@student.academy.edu', '+91 90000 10106', '2008-07-08', 'FEMALE', 'Full Stack Python', 'A', '21 Ashoka Nagar, HSR Layout', 'Bengaluru', 'Sudha Rao', '+91 90000 20106', 'sudha.rao@gmail.com', '2023-06-05', 'ACTIVE'),
 (7, 'ACAD-1203', 'Kabir', 'Singh', 'kabir.singh@student.academy.edu', '+91 90000 10107', '2008-02-21', 'MALE', 'Full Stack Python', 'B', '9 Hillcrest, Hebbal', 'Bengaluru', 'Harpreet Singh', '+91 90000 20107', 'harpreet.singh@gmail.com', '2023-06-08', 'ACTIVE'),
 (8, 'ACAD-1204', 'Meera', 'Pillai', 'meera.pillai@student.academy.edu', '+91 90000 10108', '2008-09-14', 'FEMALE', 'Full Stack Python', 'B', '33 Spice Garden, Banashankari', 'Bengaluru', 'Deepa Pillai', '+91 90000 20108', 'deepa.pillai@gmail.com', '2023-06-08', 'SUSPENDED'),
 (9, 'ACAD-1001', 'Siddharth', 'Verma', 'siddharth.verma@student.academy.edu', '+91 90000 10109', '2010-12-01', 'MALE', 'Data Analysis', 'A', '5 Green Park, Kengeri', 'Bengaluru', 'Anil Verma', '+91 90000 20109', 'anil.verma@gmail.com', '2025-06-02', 'ACTIVE'),
 (10, 'ACAD-1002', 'Aditi', 'Joshi', 'aditi.joshi@student.academy.edu', '+91 90000 10110', '2010-05-19', 'FEMALE', 'Data Analysis', 'A', '17 River Road, Yelahanka', 'Bengaluru', 'Mahesh Joshi', '+91 90000 20110', 'mahesh.joshi@gmail.com', '2025-06-02', 'ACTIVE'),
 (11, 'ACAD-0901', 'Arjun', 'Reddy', 'arjun.reddy@student.academy.edu', '+91 90000 10111', '2011-06-27', 'MALE', 'UI/UX', 'B', '62 Orchid Enclave, Electronic City', 'Bengaluru', 'Latha Reddy', '+91 90000 20111', 'latha.reddy@gmail.com', '2025-06-04', 'ACTIVE'),
 (12, 'ACAD-0902', 'Sneha', 'Kulkarni', 'sneha.kulkarni@student.academy.edu', '+91 90000 10112', '2011-10-09', 'FEMALE', 'UI/UX', 'B', '4 Garden Row, Rajajinagar', 'Bengaluru', 'Prakash Kulkarni', '+91 90000 20112', 'prakash.k@gmail.com', '2025-06-04', 'ACTIVE');

-- Data Analysis, section A
INSERT IGNORE INTO class_schedules (id, subject_id, teacher_id, class_level, section, room, day_of_week, start_time, end_time, term) VALUES
(31,9,2, 'Data Analysis', 'A', 'Block B - 101',1, '08:00', '08:45', '2026-ODD'),
 (32,10,6, 'Data Analysis', 'A', 'Block B - 101',2, '08:50', '09:35', '2026-ODD'),
 (33,12,6, 'Data Analysis', 'A', 'Block B - 101',3, '08:00', '08:45', '2026-ODD'),
 (34,11,2, 'Data Analysis', 'A', 'Block B - 110',4, '08:50', '09:35', '2026-ODD'),
 (35,9,2, 'Data Analysis', 'A', 'Block B - 101',5, '11:30', '12:30', '2026-ODD');

-- Full Stack Java, section A
INSERT IGNORE INTO class_schedules (id, subject_id, teacher_id, class_level, section, room, day_of_week, start_time, end_time, term) VALUES
(1,1,1, 'Full Stack Java', 'A', 'Block A - 201',1, '08:00', '08:45', '2026-ODD'),
 (2,3,5, 'Full Stack Java', 'A', 'Block A - 201',1, '08:50', '09:35', '2026-ODD'),
 (3,4,5, 'Full Stack Java', 'A', 'Lab 1',1, '10:00', '10:45', '2026-ODD'),
 (4,2,1, 'Full Stack Java', 'A', 'Block B - 110',1, '11:00', '11:45', '2026-ODD'),
 (5,1,1, 'Full Stack Java', 'A', 'Block A - 201',2, '08:00', '08:45', '2026-ODD'),
 (6,3,5, 'Full Stack Java', 'A', 'Lab 3',2, '09:40', '10:25', '2026-ODD'),
 (7,4,5, 'Full Stack Java', 'A', 'Lab 2',3, '08:00', '08:45', '2026-ODD'),
 (8,2,1, 'Full Stack Java', 'A', 'Block A - 201',3, '11:00', '11:45', '2026-ODD'),
 (9,1,1, 'Full Stack Java', 'A', 'Lab 1',4, '08:50', '09:35', '2026-ODD'),
 (10,3,5, 'Full Stack Java', 'A', 'Block A - 201',4, '10:00', '10:45', '2026-ODD'),
 (11,4,5, 'Full Stack Java', 'A', 'Lab 1',5, '08:00', '09:30', '2026-ODD'),
 (12,2,1, 'Full Stack Java', 'A', 'Block A - 201',5, '09:40', '10:25', '2026-ODD');

-- Full Stack Java, section B
INSERT IGNORE INTO class_schedules (id, subject_id, teacher_id, class_level, section, room, day_of_week, start_time, end_time, term) VALUES
(13,1,1, 'Full Stack Java', 'B', 'Block A - 202',1, '09:40', '10:25', '2026-ODD'),
 (14,3,5, 'Full Stack Java', 'B', 'Block A - 202',1, '11:00', '11:45', '2026-ODD'),
 (15,1,1, 'Full Stack Java', 'B', 'Lab 1',2, '11:00', '11:45', '2026-ODD'),
 (16,3,5, 'Full Stack Java', 'B', 'Block B - 110',3, '08:50', '09:35', '2026-ODD'),
 (17,4,5, 'Full Stack Java', 'B', 'Lab 3',3, '10:00', '10:45', '2026-ODD'),
 (18,2,1, 'Full Stack Java', 'B', 'Lab 2',4, '11:00', '11:45', '2026-ODD'),
 (19,1,1, 'Full Stack Java', 'B', 'Block A - 202',5, '08:00', '08:45', '2026-ODD');

-- Full Stack Python, section A
INSERT IGNORE INTO class_schedules (id, subject_id, teacher_id, class_level, section, room, day_of_week, start_time, end_time, term) VALUES
(20,5,3, 'Full Stack Python', 'A', 'Block A - 301',1, '10:00', '10:45', '2026-ODD'),
 (21,6,3, 'Full Stack Python', 'A', 'Block A - 301',2, '08:00', '08:45', '2026-ODD'),
 (22,7,3, 'Full Stack Python', 'A', 'Lab 1',2, '10:00', '10:45', '2026-ODD'),
 (23,8,3, 'Full Stack Python', 'A', 'Lab 3',3, '08:00', '08:45', '2026-ODD'),
 (24,5,3, 'Full Stack Python', 'A', 'Block B - 110',4, '08:00', '08:45', '2026-ODD'),
 (25,6,3, 'Full Stack Python', 'A', 'Lab 2',4, '10:00', '10:45', '2026-ODD'),
 (26,7,3, 'Full Stack Python', 'A', 'Block A - 301',5, '10:00', '10:45', '2026-ODD');

-- Full Stack Python, section B
INSERT IGNORE INTO class_schedules (id, subject_id, teacher_id, class_level, section, room, day_of_week, start_time, end_time, term) VALUES
(27,7,3, 'Full Stack Python', 'B', 'Block A - 302',1, '11:00', '11:45', '2026-ODD'),
 (28,8,3, 'Full Stack Python', 'B', 'Block A - 302',3, '09:40', '10:25', '2026-ODD'),
 (29,5,3, 'Full Stack Python', 'B', 'Lab 1',4, '11:00', '11:45', '2026-ODD'),
 (30,6,3, 'Full Stack Python', 'B', 'Lab 3',5, '11:00', '11:45', '2026-ODD');

-- UI/UX, section B
INSERT IGNORE INTO class_schedules (id, subject_id, teacher_id, class_level, section, room, day_of_week, start_time, end_time, term) VALUES
(36,14,7, 'UI/UX', 'B', 'Block B - 102',1, '08:50', '09:35', '2026-ODD'),
 (37,16,4, 'UI/UX', 'B', 'Lab 2',2, '08:00', '08:45', '2026-ODD'),
 (38,15,4, 'UI/UX', 'B', 'Lab 3',3, '11:00', '11:45', '2026-ODD'),
 (39,13,7, 'UI/UX', 'B', 'Block B - 110',4, '09:40', '10:25', '2026-ODD'),
 (40,14,7, 'UI/UX', 'B', 'Block B - 102',5, '08:50', '09:35', '2026-ODD');

INSERT IGNORE INTO enrollments (id, student_id, subject_id, enrolled_at) VALUES
 (1,1,1,'2024-06-10'),(2,1,2,'2024-06-10'),(3,1,4,'2024-06-10'),(4,2,1,'2024-06-10'),(5,2,3,'2024-06-10'),
 (6,2,4,'2024-06-10'),(7,3,1,'2024-06-12'),(8,3,2,'2024-06-12'),(9,3,4,'2024-06-12'),(10,4,1,'2024-06-12'),
 (11,4,3,'2024-06-12'),(12,4,4,'2024-06-12'),(13,5,5,'2023-06-05'),(14,5,7,'2023-06-05'),(15,5,8,'2023-06-05'),
 (16,6,5,'2023-06-05'),(17,6,6,'2023-06-05'),(18,6,7,'2023-06-05'),(19,7,5,'2023-06-08'),(20,7,7,'2023-06-08'),
 (21,7,8,'2023-06-08'),(22,8,5,'2023-06-08'),(23,8,6,'2023-06-08'),(24,8,7,'2023-06-08'),(25,9,9,'2025-06-02'),
 (26,9,11,'2025-06-02'),(27,9,12,'2025-06-02'),(28,10,9,'2025-06-02'),(29,10,10,'2025-06-02'),(30,10,12,'2025-06-02'),
 (31,11,13,'2025-06-04'),(32,11,14,'2025-06-04'),(33,11,15,'2025-06-04'),(34,12,14,'2025-06-04'),(35,12,15,'2025-06-04'),
 (36,12,16,'2025-06-04');
-- Attendance for the two school weeks before the demo date. The timetable decides which sessions
-- ran on each date, and the marks are derived from the ids so every boot produces the same history
-- instead of a fresh random one. Today is deliberately left unmarked so it can be taken in the UI.
INSERT IGNORE INTO attendance (student_id, subject_id, class_level, section, session_date, start_time, status)
SELECT s.id, cs.subject_id, cs.class_level, cs.section, d.session_date, cs.start_time,
       CASE
           WHEN MOD(s.id * 5 + cs.id * 3 + DAYOFYEAR(d.session_date), 19) = 0 THEN 'ABSENT'
           WHEN MOD(s.id * 7 + cs.id * 2 + DAYOFYEAR(d.session_date), 13) = 0 THEN 'LATE'
           ELSE 'PRESENT'
       END
FROM class_schedules cs
JOIN students s
  ON s.class_level = cs.class_level
 AND s.section <=> cs.section
 AND s.status = 'ACTIVE'
JOIN (
    SELECT DATE '2026-10-12' AS session_date
    UNION ALL SELECT DATE '2026-10-13'
    UNION ALL SELECT DATE '2026-10-14'
    UNION ALL SELECT DATE '2026-10-15'
    UNION ALL SELECT DATE '2026-10-16'
    UNION ALL SELECT DATE '2026-10-19'
    UNION ALL SELECT DATE '2026-10-20'
    UNION ALL SELECT DATE '2026-10-21'
    UNION ALL SELECT DATE '2026-10-22'
    UNION ALL SELECT DATE '2026-10-23'
) d
WHERE cs.day_of_week = WEEKDAY(d.session_date) + 1;

INSERT IGNORE INTO contacts (id, category, name, department, phone, alternate_phone, email, location, available_hours) VALUES
 (1, 'ADMISSIONS', 'Admissions Desk', 'Front Office', '+91 80 4000 1000', '+91 80 4000 1001', 'admissions@academy.edu', 'Main Gate, Block A', 'Mon-Sat 08:30 - 16:30'),
 (2, 'OFFICE', 'Academy Office', 'Administration', '+91 80 4000 1010', NULL, 'office@academy.edu', 'Block A - Ground', 'Mon-Fri 09:00 - 17:00'),
 (3, 'EXAM_CELL', 'Examination Cell', 'Exams', '+91 80 4000 1020', NULL, 'exams@academy.edu', 'Block B - 218', 'Mon-Fri 10:00 - 16:00'),
 (4, 'LIBRARY', 'Central Library', 'Library', '+91 80 4000 1030', NULL, 'library@academy.edu', 'Block C - 1st floor', 'Mon-Sat 08:00 - 20:00'),
 (5, 'TRANSPORT', 'Transport Coordinator', 'Operations', '+91 80 4000 1040', '+91 98450 55040', 'transport@academy.edu', 'Bus Bay', 'Mon-Fri 07:00 - 18:00'),
 (6, 'SPORTS', 'Sports & Wellness', 'Sports', '+91 80 4000 1050', NULL, 'sports@academy.edu', 'Sports Complex', 'Mon-Sat 06:30 - 19:00'),
 (7, 'HELPDESK', 'Student Helpdesk', 'Student Support', '+91 80 4000 1060', '+91 98450 55060', 'helpdesk@academy.edu', 'Block A - 105', 'Mon-Fri 08:00 - 18:00');
