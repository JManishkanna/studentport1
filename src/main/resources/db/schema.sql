-- =====================================================================
-- Academy Student Management Portal :: MySQL 8 schema
-- Idempotent: safe to run repeatedly.
-- =====================================================================

CREATE TABLE IF NOT EXISTS students (
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY,
    roll_number     VARCHAR(30)  NOT NULL,
    first_name      VARCHAR(60)  NOT NULL,
    last_name       VARCHAR(60)  NOT NULL,
    email           VARCHAR(120) NOT NULL,
    phone           VARCHAR(20),
    date_of_birth   DATE,
    gender          VARCHAR(10),
    class_level     VARCHAR(30)  NOT NULL,
    section         VARCHAR(10),
    address_line    VARCHAR(200),
    city            VARCHAR(60),
    guardian_name   VARCHAR(120),
    guardian_phone  VARCHAR(20),
    guardian_email  VARCHAR(120),
    admission_date  DATE,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_students_roll UNIQUE (roll_number),
    CONSTRAINT uq_students_email UNIQUE (email),
    INDEX idx_students_class (class_level, section),
    INDEX idx_students_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS teachers (
    id             BIGINT       AUTO_INCREMENT PRIMARY KEY,
    employee_code  VARCHAR(30)  NOT NULL,
    first_name     VARCHAR(60)  NOT NULL,
    last_name      VARCHAR(60)  NOT NULL,
    designation    VARCHAR(60),
    department     VARCHAR(60)  NOT NULL,
    email          VARCHAR(120) NOT NULL,
    phone          VARCHAR(20),
    specialization VARCHAR(120),
    qualification  VARCHAR(120),
    office_room    VARCHAR(40),
    joining_date   DATE,
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uq_teachers_code UNIQUE (employee_code),
    CONSTRAINT uq_teachers_email UNIQUE (email),
    INDEX idx_teachers_department (department)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS subjects (
    id         BIGINT      AUTO_INCREMENT PRIMARY KEY,
    code       VARCHAR(20) NOT NULL,
    name       VARCHAR(100) NOT NULL,
    department VARCHAR(60) NOT NULL,
    credits    INT         NOT NULL DEFAULT 4,
    CONSTRAINT uq_subjects_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS class_schedules (
    id          BIGINT      AUTO_INCREMENT PRIMARY KEY,
    subject_id  BIGINT      NOT NULL,
    teacher_id  BIGINT      NOT NULL,
    class_level VARCHAR(30) NOT NULL,
    section     VARCHAR(10) NOT NULL,
    room        VARCHAR(40),
    day_of_week TINYINT     NOT NULL COMMENT '1 = Monday .. 7 = Sunday',
    start_time  TIME        NOT NULL,
    end_time    TIME        NOT NULL,
    term        VARCHAR(30) NOT NULL DEFAULT '2026-ODD',
    CONSTRAINT fk_schedule_subject FOREIGN KEY (subject_id) REFERENCES subjects (id) ON DELETE CASCADE,
    CONSTRAINT fk_schedule_teacher FOREIGN KEY (teacher_id) REFERENCES teachers (id) ON DELETE CASCADE,
    CONSTRAINT uq_schedule_slot UNIQUE (class_level, section, day_of_week, start_time),
    INDEX idx_schedule_teacher (teacher_id),
    INDEX idx_schedule_day (day_of_week)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS enrollments (
    id          BIGINT   AUTO_INCREMENT PRIMARY KEY,
    student_id  BIGINT   NOT NULL,
    subject_id  BIGINT   NOT NULL,
    enrolled_at DATE,
    CONSTRAINT fk_enroll_student FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE CASCADE,
    CONSTRAINT fk_enroll_subject FOREIGN KEY (subject_id) REFERENCES subjects (id) ON DELETE CASCADE,
    CONSTRAINT uq_enrollment UNIQUE (student_id, subject_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- One row per student, session and date. The class, subject and start time are copied from the
-- timetable at the moment attendance is taken, so a later timetable edit cannot rewrite history.
CREATE TABLE IF NOT EXISTS attendance (
    id           BIGINT      AUTO_INCREMENT PRIMARY KEY,
    student_id   BIGINT      NOT NULL,
    subject_id   BIGINT      NOT NULL,
    class_level  VARCHAR(30) NOT NULL,
    section      VARCHAR(10),
    session_date DATE        NOT NULL,
    start_time   TIME        NOT NULL,
    status       VARCHAR(10) NOT NULL COMMENT 'PRESENT | ABSENT | LATE',
    marked_by    BIGINT,
    marked_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_attendance_student FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE CASCADE,
    CONSTRAINT fk_attendance_subject FOREIGN KEY (subject_id) REFERENCES subjects (id) ON DELETE CASCADE,
    CONSTRAINT uq_attendance_session UNIQUE (student_id, subject_id, session_date, start_time),
    INDEX idx_attendance_class (class_level, section, session_date),
    INDEX idx_attendance_date (session_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS contacts (
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY,
    category        VARCHAR(30)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    department      VARCHAR(80),
    phone           VARCHAR(30)  NOT NULL,
    alternate_phone VARCHAR(30),
    email           VARCHAR(120),
    location        VARCHAR(120),
    available_hours VARCHAR(60),
    INDEX idx_contacts_category (category)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS app_users (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(60)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    email         VARCHAR(120),
    phone         VARCHAR(20),
    role          VARCHAR(20)  NOT NULL COMMENT 'ADMIN | TEACHER | STUDENT',
    student_id    BIGINT,
    teacher_id    BIGINT,
    enabled       TINYINT(1)   NOT NULL DEFAULT 1,
    CONSTRAINT uq_app_users_username UNIQUE (username),
    CONSTRAINT fk_app_users_student FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE SET NULL,
    CONSTRAINT fk_app_users_teacher FOREIGN KEY (teacher_id) REFERENCES teachers (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS profile_photos (
    user_id      BIGINT      NOT NULL PRIMARY KEY,
    content_type VARCHAR(40) NOT NULL,
    bytes        LONGBLOB    NOT NULL,
    updated_at   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_photo_user FOREIGN KEY (user_id) REFERENCES app_users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- The cache-busting token in a photo URL carries milliseconds. Databases created before the
-- column had sub-second precision get widened once, otherwise a fresh upload and the next
-- page load disagree on the version.
SET @add_photo_millis = (
    SELECT IF(DATETIME_PRECISION = 0 OR DATETIME_PRECISION IS NULL,
              'ALTER TABLE profile_photos MODIFY COLUMN updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)',
              'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'profile_photos'
      AND COLUMN_NAME = 'updated_at'
);
PREPARE add_photo_millis FROM @add_photo_millis;
EXECUTE add_photo_millis;
DEALLOCATE PREPARE add_photo_millis;

-- MySQL 8 has no ADD COLUMN IF NOT EXISTS, so look the column up and prepare the ALTER only
-- on databases that predate it. Accounts that existed before this migration were seeded with
-- the demo password, hence the 1.
SET @add_password_flag = (
    SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
                     WHERE TABLE_SCHEMA = DATABASE()
                       AND TABLE_NAME = 'app_users'
                       AND COLUMN_NAME = 'password_is_default'),
              'DO 0',
              'ALTER TABLE app_users ADD COLUMN password_is_default TINYINT(1) NOT NULL DEFAULT 1 AFTER enabled')
);
PREPARE add_password_flag FROM @add_password_flag;
EXECUTE add_password_flag;
DEALLOCATE PREPARE add_password_flag;

-- The administration line on the contacts page publishes a phone as well as a name and email,
-- so accounts gained a phone column after the first release.
SET @add_user_phone = (
    SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
                     WHERE TABLE_SCHEMA = DATABASE()
                       AND TABLE_NAME = 'app_users'
                       AND COLUMN_NAME = 'phone'),
              'DO 0',
              'ALTER TABLE app_users ADD COLUMN phone VARCHAR(20) AFTER email')
);
PREPARE add_user_phone FROM @add_user_phone;
EXECUTE add_user_phone;
DEALLOCATE PREPARE add_user_phone;

-- Coursework one class division is marked on. A title is unique per subject and division, which
-- also keeps the seeded rows from being inserted twice.
CREATE TABLE IF NOT EXISTS assessments (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    subject_id  BIGINT       NOT NULL,
    class_level VARCHAR(30)  NOT NULL,
    section     VARCHAR(10),
    title       VARCHAR(120) NOT NULL,
    kind        VARCHAR(12)  NOT NULL COMMENT 'ASSIGNMENT | QUIZ | EXAM',
    due_date    DATE         NOT NULL,
    max_marks   INT          NOT NULL,
    description VARCHAR(500),
    created_by  BIGINT,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_assessment_subject FOREIGN KEY (subject_id) REFERENCES subjects (id) ON DELETE CASCADE,
    CONSTRAINT fk_assessment_author FOREIGN KEY (created_by) REFERENCES app_users (id) ON DELETE SET NULL,
    CONSTRAINT uq_assessment_title UNIQUE (subject_id, class_level, section, title),
    INDEX idx_assessment_class (class_level, section, due_date),
    INDEX idx_assessment_due (due_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS grades (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    assessment_id BIGINT       NOT NULL,
    student_id    BIGINT       NOT NULL,
    marks         DECIMAL(6,2) NOT NULL,
    remark        VARCHAR(200),
    graded_by     BIGINT,
    graded_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_grade_assessment FOREIGN KEY (assessment_id) REFERENCES assessments (id) ON DELETE CASCADE,
    CONSTRAINT fk_grade_student FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE CASCADE,
    CONSTRAINT fk_grade_marker FOREIGN KEY (graded_by) REFERENCES app_users (id) ON DELETE SET NULL,
    CONSTRAINT uq_grade_student UNIQUE (assessment_id, student_id),
    INDEX idx_grade_student (student_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Notes faculty and administrators leave on a student's record. The student reads every note
-- about them. teacher_id records the author's faculty record so seeded notes stay owned by the
-- teacher even before the sign-ins exist.
CREATE TABLE IF NOT EXISTS student_comments (
    id          BIGINT        AUTO_INCREMENT PRIMARY KEY,
    student_id  BIGINT        NOT NULL,
    subject_id  BIGINT,
    teacher_id  BIGINT,
    author_id   BIGINT,
    author_name VARCHAR(120)  NOT NULL,
    author_role VARCHAR(20)   NOT NULL COMMENT 'ADMIN | TEACHER',
    body        VARCHAR(1000) NOT NULL,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_comment_student FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_subject FOREIGN KEY (subject_id) REFERENCES subjects (id) ON DELETE SET NULL,
    CONSTRAINT fk_comment_teacher FOREIGN KEY (teacher_id) REFERENCES teachers (id) ON DELETE SET NULL,
    CONSTRAINT fk_comment_author FOREIGN KEY (author_id) REFERENCES app_users (id) ON DELETE SET NULL,
    INDEX idx_comment_student (student_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
