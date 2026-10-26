# Academy Student Management Portal

A full-stack student management portal for an academy: student profiles, class schedules, teacher
records and contact information, with role-based sign-in.

| Layer | Technology |
| --- | --- |
| Backend | Java 17+, Spring Boot 3.5 (web, data-jpa, security, validation) |
| Database | MySQL 8.0, plain SQL schema + seed scripts |
| Frontend | Static HTML, CSS, vanilla JavaScript (no framework, no build step) |
| Build | Maven (`pom.xml`), Spring Boot executable jar |

---

## 1. Prerequisites

- JDK 17 or newer (`java -version`)
- Maven 3.9+ (`mvn -v`) — if Maven is missing, `C:\Users\<you>\tools\apache-maven-3.9.11\bin\mvn`
  was installed while building this project
- MySQL 8.0 running with an account that can create a database

## 2. Point the app at MySQL

Credentials are read from environment variables; `src/main/resources/application.properties` holds
the defaults. Nothing secret needs to be committed.

```powershell
# Windows PowerShell
$env:MYSQL_HOST     = "localhost"
$env:MYSQL_PORT     = "3306"
$env:MYSQL_DATABASE = "academy_portal"
$env:MYSQL_USER     = "root"
$env:MYSQL_PASSWORD = "your-password"
```

```bash
# Linux / macOS / Git Bash
export MYSQL_USER=root
export MYSQL_PASSWORD=your-password
```

The JDBC URL uses `createDatabaseIfNotExist=true`, so the schema is created for you on first boot.
On every start the app also runs:

- `src/main/resources/db/schema.sql` — `CREATE TABLE IF NOT EXISTS` for all seven tables
- `src/main/resources/db/data.sql` — demo students, faculty, subjects, timetable slots and contacts
  (`INSERT IGNORE`, so restarting never duplicates rows)

The demo catalogue is organised as four programmes. `class_level` holds the programme name, and each
programme owns one track of four subjects and its own faculty:

| Programme | Subjects | Faculty |
| --- | --- | --- |
| Full Stack Java | Java Core and OOP, Spring Boot REST APIs, Hibernate and JPA, JavaScript Frontend | Suresh Babu, Priya Desai |
| Full Stack Python | Python Foundations, Django Web Development, FastAPI Microservices, React Frontend Basics | Neelam Kaur |
| Data Analysis | Applied Statistics, SQL for Analysts, Python for Data, Data Visualisation | Kavita Anand, Ramesh Naik |
| UI/UX | Design Foundations, UX Research Methods, Interaction Design, Prototyping with Figma | Thomas Mathew, Farida Khan |

Timetable sessions, enrolments and teacher departments all stay inside one track, so a student's
profile never shows a subject from another programme.

To load the database by hand instead:

```bash
mysql -u root -p academy_portal < src/main/resources/db/schema.sql
mysql -u root -p academy_portal < src/main/resources/db/data.sql
```

## 3. Run it

```bash
mvn spring-boot:run
# or
mvn clean package -DskipTests
java -jar target/academy-portal-1.0.0.jar
```

Open <http://localhost:8080> — you are redirected to `login.html`. Unsigned calls to `/api/**` answer
`401` instead of a redirect, so `fetch()` can tell an expired session from a successful response.

## 4. Demo accounts

Created automatically on first boot by `UserSeeder` (BCrypt-hashed; disable with
`portal.seed.users.enabled=false` and insert your own `app_users` rows).

| Username | Password | Role | What they see |
| --- | --- | --- | --- |
| `admin` | `admin123` | ADMIN | Everything, plus create/edit/delete |
| `teacher` | `teacher123` | TEACHER | Student directory, all timetables, own profile |
| `student` | `student123` | STUDENT | Own profile, own timetable, own subjects only |

**Change these before deploying anywhere real.** The student account is linked to roll `ACAD-1101`
(Aarav Menon) and the teacher account to `ACAD-T-001` (Suresh Babu).

## 5. Pages

| Page | File | Purpose |
| --- | --- | --- |
| Sign in | `static/login.html` | Session login, CSRF-aware |
| Dashboard | `static/index.html` | Role-aware overview: academy stats and today's classes, or a student's own day |
| Students | `static/students.html` | Search / filter roster, admit, edit, remove (admin) |
| Student profile | `static/student-profile.html` | Profile, guardian + contact details, weekly timetable, enrolled subjects, classmates |
| Teachers | `static/teachers.html` | Faculty directory cards with contact details |
| Teacher profile | `static/teacher-profile.html` | Contact info, qualifications, weekly timetable, subjects and classes taught |
| Timetable | `static/schedule.html` | Week grid with class/section/teacher/subject filters; admin builds sessions |
| Contacts | `static/contacts.html` | Role-scoped people directory (see below) plus department desks and helplines grouped by category |

## 6. REST API

All endpoints require a session cookie. Writes need the `X-XSRF-TOKEN` header (the client gets it
from `GET /api/auth/csrf`).

| Method | Path | Access |
| --- | --- | --- |
| `GET` | `/api/auth/csrf` | public |
| `GET` | `/api/auth/me` | any signed-in user |
| `GET` | `/api/dashboard` | any signed-in user |
| `GET` | `/api/students?q=&classLevel=&status=` | ADMIN, TEACHER |
| `GET` | `/api/students/{id}` · `/{id}/schedule` · `/{id}/subjects` · `/{id}/classmates` | ADMIN, TEACHER |
| `GET` | `/api/students/me` · `/me/schedule` · `/me/subjects` · `/me/classmates` | signed-in student |
| `POST` `PUT` `DELETE` | `/api/students/**`, `/api/students/{id}/subjects/{subjectId}` | ADMIN |
| `GET` | `/api/teachers`, `/api/teachers/{id}`, `/api/teachers/{id}/schedule` | any signed-in user |
| `GET` | `/api/teachers/me`, `/api/teachers/me/schedule` | signed-in teacher |
| `POST` `PUT` `DELETE` | `/api/teachers/**` | ADMIN |
| `GET` | `/api/schedules?dayOfWeek=&classLevel=&section=&teacherId=&subjectId=` | ADMIN, TEACHER |
| `GET` | `/api/schedules/class?classLevel=&section=` | any signed-in user |
| `POST` `PUT` `DELETE` | `/api/schedules/**` | ADMIN |
| `GET` | `/api/contacts`, `/api/contacts/categories`, `/api/subjects` | any signed-in user |
| `GET` | `/api/contacts/people` | any signed-in user, scoped to their own classes |
| `POST` `PUT` `DELETE` | `/api/contacts/**` | ADMIN |

### Who the contacts page shows

`/api/contacts/people` works out the answerable list from the session and the timetable, so a client
cannot widen it:

| Signed-in as | Sees |
| --- | --- |
| STUDENT | The faculty who teach their class, with each teacher's phone, email and the subjects they are responsible for, plus the administration mailbox from `app_users` |
| TEACHER | The students in every class and division on their own timetable, with roll number, student phone, guardian phone and email |
| ADMIN | All faculty and all students |

The manually maintained desk rows (`/api/contacts`) stay below that list for every role and are
editable by ADMIN only.

Quick check from the shell:

```bash
curl -s -c cookies.txt -d "username=admin&password=admin123" -X POST http://localhost:8080/login
curl -s -b cookies.txt http://localhost:8080/api/dashboard
```

## 7. Data model

```
students ──┐
           ├── enrollments (student ↔ subject)
subjects ──┤
           ├── class_schedules (subject + teacher + class/section + day + time + room)
teachers ──┘
contacts        desk directory, grouped by category; the people list is derived from
                class_schedules + students + teachers instead of this table
app_users       login + role, points at a student or teacher row
```

Business rules enforced in the service layer, not just the UI:

- Roll numbers and emails are unique per student; employee codes and emails per teacher.
- A class session must start before it ends.
- No two sessions can overlap for the same class/section, and a teacher cannot be double-booked —
  the API returns `409 Conflict` with the clashing slot named.
- Deleting a student removes their enrollments; deleting a teacher removes their sessions.
- Students cannot read another student's record: `/api/students/{id}` is ADMIN/TEACHER only, and
  self-service always resolves the id from the session, never from the request.

## 8. Layout

```
academy-portal/
├── pom.xml
└── src/main/
    ├── java/com/academy/portal/
    │   ├── AcademyPortalApplication.java
    │   ├── config/     SecurityConfig, PortalUserDetailsService, UserSeeder
    │   ├── domain/     JPA entities + Role enum
    │   ├── dto/      request/response records (StudentDto, ScheduleRow, ...)
    │   ├── repository/ Spring Data JPA interfaces
    │   ├── service/    business rules, validation, conflict detection
    │   └── web/        REST controllers + error advice
    └── resources/
        ├── application.properties
        ├── db/schema.sql, db/data.sql
        └── static/     HTML pages, css/main.css, js/*.js
```

## 9. Troubleshooting

| Symptom | Cause / fix |
| --- | --- |
| `Access denied for user 'root'@'localhost'` | Set `MYSQL_USER` / `MYSQL_PASSWORD` correctly |
| `Communications link failure` | MySQL service is stopped, or the port is wrong (`MYSQL_PORT`) |
| Login posts return `403` | The CSRF cookie/header is missing — sign in through `login.html`, and make sure the app is served over `http://localhost` (cookies are scoped) |
| Empty dashboard but app started | `db/data.sql` did not run: check the boot log for `Executing SQL script from class path resource` |
| Port 8080 busy | `SERVER_PORT=9090` (the app reads `PORT`), then open `http://localhost:9090` |
