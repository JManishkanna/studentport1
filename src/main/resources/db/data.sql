-- =====================================================================
-- Academy Student Management Portal :: demo seed data
-- Idempotent: uses INSERT IGNORE so re-running does not duplicate rows.
-- =====================================================================

INSERT IGNORE INTO teachers (id, employee_code, first_name, last_name, designation, department, email, phone, specialization, qualification, office_room, joining_date, status) VALUES
 (1, 'ACAD-T-001', 'Suresh',  'Babu',   'Head of Department', 'Mathematics',      'suresh.babu@academy.edu',     '+91 98450 11001', 'Calculus, Vectors',            'M.Sc. Mathematics, B.Ed.',      'Block A - 101', '2011-06-01', 'ACTIVE'),
 (2, 'ACAD-T-002', 'Kavita',  'Anand',  'Senior Faculty',     'Physics',          'kavita.anand@academy.edu',    '+91 98450 11002', 'Mechanics, Optics',            'M.Sc. Physics, Ph.D.',        'Block A - 104', '2014-04-15', 'ACTIVE'),
 (3, 'ACAD-T-003', 'Neelam',  'Kaur',   'Faculty',            'Chemistry',        'neelam.kaur@academy.edu',     '+91 98450 11003', 'Organic Chemistry',            'M.Sc. Chemistry',             'Block B - 202', '2016-07-20', 'ACTIVE'),
 (4, 'ACAD-T-004', 'Thomas',  'Mathew', 'Faculty',            'Computer Science', 'thomas.mathew@academy.edu',   '+91 98450 11004', 'Java, Data Structures',        'MCA',                         'Block C - 305', '2018-01-08', 'ACTIVE'),
 (5, 'ACAD-T-005', 'Priya',   'Desai',  'Faculty',            'English',          'priya.desai@academy.edu',     '+91 98450 11005', 'Literature, Communication',    'M.A. English',                'Block B - 210', '2015-09-01', 'ACTIVE'),
 (6, 'ACAD-T-006', 'Ramesh',  'Naik',   'Faculty',            'Biology',          'ramesh.naik@academy.edu',     '+91 98450 11006', 'Genetics, Human Physiology',   'M.Sc. Botany',                'Block B - 215', '2017-03-12', 'ACTIVE'),
 (7, 'ACAD-T-007', 'Farida',  'Khan',   'Faculty',            'Social Science',   'farida.khan@academy.edu',     '+91 98450 11007', 'Modern History, Economics',    'M.A. History',                'Block A - 108', '2019-06-24', 'ACTIVE');

INSERT IGNORE INTO subjects (id, code, name, department, credits) VALUES
 (1, 'MTH', 'Mathematics',       'Mathematics',      5),
 (2, 'PHY', 'Physics',           'Physics',          5),
 (3, 'CHE', 'Chemistry',         'Chemistry',        5),
 (4, 'BIO', 'Biology',           'Biology',          4),
 (5, 'ENG', 'English',           'English',          3),
 (6, 'CSC', 'Computer Science',  'Computer Science', 4),
 (7, 'SOS', 'Social Science',    'Social Science',   3),
 (8, 'PED', 'Physical Education','Sports',           2);

INSERT IGNORE INTO students (id, roll_number, first_name, last_name, email, phone, date_of_birth, gender, class_level, section, address_line, city, guardian_name, guardian_phone, guardian_email, admission_date, status) VALUES
 (1,  'ACAD-1101', 'Aarav',     'Menon',    'aarav.menon@student.academy.edu',   '+91 90000 10101', '2009-04-12', 'MALE',   'Full Stack Java', 'A', '12 Palm Grove, Indiranagar',    'Bengaluru',  'Ravi Menon',      '+91 90000 20101', 'ravi.menon@gmail.com',   '2024-06-10', 'ACTIVE'),
 (2,  'ACAD-1102', 'Diya',      'Sharma',   'diya.sharma@student.academy.edu',   '+91 90000 10102', '2009-08-03', 'FEMALE', 'Full Stack Java', 'A', '45 MG Road, Sector 2',          'Bengaluru',  'Nikhil Sharma',   '+91 90000 20102', 'nikhil.sharma@gmail.com','2024-06-10', 'ACTIVE'),
 (3,  'ACAD-1103', 'Rohan',     'Iyer',     'rohan.iyer@student.academy.edu',    '+91 90000 10103', '2009-01-25', 'MALE',   'Full Stack Java', 'B', '7 Temple Street, Jayanagar',    'Bengaluru',  'Ganesh Iyer',     '+91 90000 20103', 'ganesh.iyer@gmail.com',  '2024-06-12', 'ACTIVE'),
 (4,  'ACAD-1104', 'Ananya',    'Nair',     'ananya.nair@student.academy.edu',   '+91 90000 10104', '2009-11-30', 'FEMALE', 'Full Stack Java', 'B', '88 Coral Ridge, Koramangala',   'Bengaluru',  'Vinod Nair',      '+91 90000 20104', 'vinod.nair@gmail.com',   '2024-06-12', 'ACTIVE'),
 (5,  'ACAD-1201', 'Vivaan',    'Gupta',    'vivaan.gupta@student.academy.edu',  '+91 90000 10105', '2008-03-17', 'MALE',   'Full Stack Python', 'A', '3 Lake View, Whitefield',       'Bengaluru',  'Amit Gupta',      '+91 90000 20105', 'amit.gupta@gmail.com',   '2023-06-05', 'ACTIVE'),
 (6,  'ACAD-1202', 'Ishita',    'Rao',      'ishita.rao@student.academy.edu',    '+91 90000 10106', '2008-07-08', 'FEMALE', 'Full Stack Python', 'A', '21 Ashoka Nagar, HSR Layout',   'Bengaluru',  'Sudha Rao',       '+91 90000 20106', 'sudha.rao@gmail.com',    '2023-06-05', 'ACTIVE'),
 (7,  'ACAD-1203', 'Kabir',     'Singh',    'kabir.singh@student.academy.edu',   '+91 90000 10107', '2008-02-21', 'MALE',   'Full Stack Python', 'B', '9 Hillcrest, Hebbal',           'Bengaluru',  'Harpreet Singh',  '+91 90000 20107', 'harpreet.singh@gmail.com','2023-06-08', 'ACTIVE'),
 (8,  'ACAD-1204', 'Meera',     'Pillai',   'meera.pillai@student.academy.edu',  '+91 90000 10108', '2008-09-14', 'FEMALE', 'Full Stack Python', 'B', '33 Spice Garden, Banashankari', 'Bengaluru',  'Deepa Pillai',    '+91 90000 20108', 'deepa.pillai@gmail.com', '2023-06-08', 'SUSPENDED'),
 (9,  'ACAD-1001', 'Siddharth', 'Verma',    'siddharth.verma@student.academy.edu','+91 90000 10109','2010-12-01', 'MALE',   'Data Analysis', 'A', '5 Green Park, Kengeri',         'Bengaluru',  'Anil Verma',      '+91 90000 20109', 'anil.verma@gmail.com',   '2025-06-02', 'ACTIVE'),
 (10, 'ACAD-1002', 'Aditi',     'Joshi',    'aditi.joshi@student.academy.edu',   '+91 90000 10110', '2010-05-19', 'FEMALE', 'Data Analysis', 'A', '17 River Road, Yelahanka',      'Bengaluru',  'Mahesh Joshi',    '+91 90000 20110', 'mahesh.joshi@gmail.com', '2025-06-02', 'ACTIVE'),
 (11, 'ACAD-0901', 'Arjun',     'Reddy',    'arjun.reddy@student.academy.edu',   '+91 90000 10111', '2011-06-27', 'MALE',   'UI/UX',  'B', '62 Orchid Enclave, Electronic City','Bengaluru','Latha Reddy',   '+91 90000 20111', 'latha.reddy@gmail.com',  '2025-06-04', 'ACTIVE'),
 (12, 'ACAD-0902', 'Sneha',     'Kulkarni', 'sneha.kulkarni@student.academy.edu','+91 90000 10112', '2011-10-09', 'FEMALE', 'UI/UX',  'B', '4 Garden Row, Rajajinagar',     'Bengaluru',  'Prakash Kulkarni','+91 90000 20112', 'prakash.k@gmail.com',    '2025-06-04', 'ACTIVE');

-- Full Stack Java section A (mon..fri)
INSERT IGNORE INTO class_schedules (id, subject_id, teacher_id, class_level, section, room, day_of_week, start_time, end_time, term) VALUES
 (1,  1, 1, 'Full Stack Java', 'A', 'Block A - 201', 1, '08:00', '08:45', '2026-ODD'),
 (2,  2, 2, 'Full Stack Java', 'A', 'Block A - 201', 1, '08:50', '09:35', '2026-ODD'),
 (3,  3, 3, 'Full Stack Java', 'A', 'Lab 1',         1, '10:00', '10:45', '2026-ODD'),
 (4,  5, 5, 'Full Stack Java', 'A', 'Block B - 110', 1, '11:00', '11:45', '2026-ODD'),
 (5,  1, 1, 'Full Stack Java', 'A', 'Block A - 201', 2, '08:00', '08:45', '2026-ODD'),
 (6,  6, 4, 'Full Stack Java', 'A', 'Lab 3',         2, '09:40', '10:25', '2026-ODD'),
 (7,  4, 6, 'Full Stack Java', 'A', 'Lab 2',         3, '08:00', '08:45', '2026-ODD'),
 (8,  2, 2, 'Full Stack Java', 'A', 'Block A - 201', 3, '11:00', '11:45', '2026-ODD'),
 (9,  3, 3, 'Full Stack Java', 'A', 'Lab 1',         4, '08:50', '09:35', '2026-ODD'),
 (10, 1, 1, 'Full Stack Java', 'A', 'Block A - 201', 4, '10:00', '10:45', '2026-ODD'),
 (11, 8, 7, 'Full Stack Java', 'A', 'Sports Ground', 5, '08:00', '09:30', '2026-ODD'),
 (12, 7, 7, 'Full Stack Java', 'A', 'Block A - 201', 5, '09:40', '10:25', '2026-ODD');

-- Full Stack Java section B
INSERT IGNORE INTO class_schedules (id, subject_id, teacher_id, class_level, section, room, day_of_week, start_time, end_time, term) VALUES
 (13, 1, 1, 'Full Stack Java', 'B', 'Block A - 202', 1, '09:40', '10:25', '2026-ODD'),
 (14, 2, 2, 'Full Stack Java', 'B', 'Block A - 202', 1, '11:00', '11:45', '2026-ODD'),
 (15, 3, 3, 'Full Stack Java', 'B', 'Lab 1',         2, '11:00', '11:45', '2026-ODD'),
 (16, 5, 5, 'Full Stack Java', 'B', 'Block B - 110', 3, '08:50', '09:35', '2026-ODD'),
 (17, 6, 4, 'Full Stack Java', 'B', 'Lab 3',         3, '10:00', '10:45', '2026-ODD'),
 (18, 4, 6, 'Full Stack Java', 'B', 'Lab 2',         4, '11:00', '11:45', '2026-ODD'),
 (19, 7, 7, 'Full Stack Java', 'B', 'Block A - 202', 5, '08:00', '08:45', '2026-ODD');

-- Full Stack Python section A
INSERT IGNORE INTO class_schedules (id, subject_id, teacher_id, class_level, section, room, day_of_week, start_time, end_time, term) VALUES
 (20, 1, 1, 'Full Stack Python', 'A', 'Block A - 301', 1, '10:00', '10:45', '2026-ODD'),
 (21, 2, 2, 'Full Stack Python', 'A', 'Block A - 301', 2, '08:00', '08:45', '2026-ODD'),
 (22, 3, 3, 'Full Stack Python', 'A', 'Lab 1',         2, '10:00', '10:45', '2026-ODD'),
 (23, 6, 4, 'Full Stack Python', 'A', 'Lab 3',         3, '08:00', '08:45', '2026-ODD'),
 (24, 5, 5, 'Full Stack Python', 'A', 'Block B - 110', 4, '08:00', '08:45', '2026-ODD'),
 (25, 4, 6, 'Full Stack Python', 'A', 'Lab 2',         4, '10:00', '10:45', '2026-ODD'),
 (26, 7, 7, 'Full Stack Python', 'A', 'Block A - 301', 5, '10:00', '10:45', '2026-ODD');

-- Full Stack Python section B
INSERT IGNORE INTO class_schedules (id, subject_id, teacher_id, class_level, section, room, day_of_week, start_time, end_time, term) VALUES
 (27, 1, 1, 'Full Stack Python', 'B', 'Block A - 302', 1, '11:00', '11:45', '2026-ODD'),
 (28, 2, 2, 'Full Stack Python', 'B', 'Block A - 302', 3, '09:40', '10:25', '2026-ODD'),
 (29, 3, 3, 'Full Stack Python', 'B', 'Lab 1',         4, '11:00', '11:45', '2026-ODD'),
 (30, 6, 4, 'Full Stack Python', 'B', 'Lab 3',         5, '11:00', '11:45', '2026-ODD');

-- Data Analysis section A
INSERT IGNORE INTO class_schedules (id, subject_id, teacher_id, class_level, section, room, day_of_week, start_time, end_time, term) VALUES
 (31, 1, 1, 'Data Analysis', 'A', 'Block B - 101', 1, '08:00', '08:45', '2026-ODD'),
 (32, 2, 2, 'Data Analysis', 'A', 'Block B - 101', 2, '08:50', '09:35', '2026-ODD'),
 (33, 7, 7, 'Data Analysis', 'A', 'Block B - 101', 3, '08:00', '08:45', '2026-ODD'),
 (34, 5, 5, 'Data Analysis', 'A', 'Block B - 110', 4, '08:50', '09:35', '2026-ODD'),
 (35, 8, 7, 'Data Analysis', 'A', 'Sports Ground', 5, '11:30', '12:30', '2026-ODD');

-- UI/UX section B
INSERT IGNORE INTO class_schedules (id, subject_id, teacher_id, class_level, section, room, day_of_week, start_time, end_time, term) VALUES
 (36, 1, 1, 'UI/UX',  'B', 'Block B - 102', 1, '08:50', '09:35', '2026-ODD'),
 (37, 4, 6, 'UI/UX',  'B', 'Lab 2',         2, '08:00', '08:45', '2026-ODD'),
 (38, 6, 4, 'UI/UX',  'B', 'Lab 3',         3, '11:00', '11:45', '2026-ODD'),
 (39, 5, 5, 'UI/UX',  'B', 'Block B - 110', 4, '09:40', '10:25', '2026-ODD'),
 (40, 7, 7, 'UI/UX',  'B', 'Block B - 102', 5, '08:50', '09:35', '2026-ODD');

INSERT IGNORE INTO enrollments (id, student_id, subject_id, enrolled_at) VALUES
 (1, 1, 1, '2024-06-10'), (2, 1, 2, '2024-06-10'), (3, 1, 3, '2024-06-10'), (4, 1, 5, '2024-06-10'), (5, 1, 6, '2024-06-10'),
 (6, 2, 1, '2024-06-10'), (7, 2, 2, '2024-06-10'), (8, 2, 4, '2024-06-10'), (9, 2, 5, '2024-06-10'),
 (10, 3, 1, '2024-06-12'), (11, 3, 3, '2024-06-12'), (12, 3, 6, '2024-06-12'), (13, 3, 7, '2024-06-12'),
 (14, 4, 2, '2024-06-12'), (15, 4, 5, '2024-06-12'), (16, 4, 4, '2024-06-12'),
 (17, 5, 1, '2023-06-05'), (18, 5, 2, '2023-06-05'), (19, 5, 6, '2023-06-05'),
 (20, 6, 1, '2023-06-05'), (21, 6, 3, '2023-06-05'), (22, 6, 4, '2023-06-05'),
 (23, 7, 2, '2023-06-08'), (24, 7, 1, '2023-06-08'),
 (25, 8, 3, '2023-06-08'), (26, 8, 5, '2023-06-08'),
 (27, 9, 1, '2025-06-02'), (28, 9, 7, '2025-06-02'),
 (29, 10, 2, '2025-06-02'), (30, 10, 5, '2025-06-02'),
 (31, 11, 6, '2025-06-04'), (32, 11, 4, '2025-06-04'),
 (33, 12, 1, '2025-06-04'), (34, 12, 7, '2025-06-04');

INSERT IGNORE INTO contacts (id, category, name, department, phone, alternate_phone, email, location, available_hours) VALUES
 (1, 'ADMISSIONS', 'Admissions Desk',      'Front Office',  '+91 80 4000 1000', '+91 80 4000 1001', 'admissions@academy.edu',    'Main Gate, Block A',   'Mon-Sat 08:30 - 16:30'),
 (2, 'OFFICE',     'Academy Office',       'Administration','+91 80 4000 1010', NULL,               'office@academy.edu',        'Block A - Ground',   'Mon-Fri 09:00 - 17:00'),
 (3, 'EXAM_CELL',  'Examination Cell',     'Exams',         '+91 80 4000 1020', NULL,               'exams@academy.edu',         'Block B - 218',      'Mon-Fri 10:00 - 16:00'),
 (4, 'LIBRARY',    'Central Library',      'Library',       '+91 80 4000 1030', NULL,               'library@academy.edu',       'Block C - 1st floor','Mon-Sat 08:00 - 20:00'),
 (5, 'TRANSPORT',  'Transport Coordinator','Operations',    '+91 80 4000 1040', '+91 98450 55040',  'transport@academy.edu',     'Bus Bay',            'Mon-Fri 07:00 - 18:00'),
 (6, 'SPORTS',     'Sports & Wellness',    'Sports',        '+91 80 4000 1050', NULL,               'sports@academy.edu',        'Sports Complex',     'Mon-Sat 06:30 - 19:00'),
 (7, 'HELPDESK',   'Student Helpdesk',     'Student Support','+91 80 4000 1060', '+91 98450 55060',  'helpdesk@academy.edu',      'Block A - 105',      'Mon-Fri 08:00 - 18:00');
