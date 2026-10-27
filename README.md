# Academy Student Management Portal

A full-stack student management portal for an academy: student profiles, class schedules, teacher
records and contact information, with role-based sign-in.

| Layer | Technology |
| --- | --- |
| Backend | Java 17+, Spring Boot 3.5 (web, data-jpa, security, validation) |
| Documents | OpenPDF — graduation certificates are drawn server-side as real PDFs |
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

- `src/main/resources/db/schema.sql` — `CREATE TABLE IF NOT EXISTS` for all eight tables, plus
  guarded `ALTER TABLE` migrations that add `app_users.password_is_default`, widen
  `profile_photos.updated_at` to milliseconds and add `app_users.phone` to databases created before
  those columns existed
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
| any teacher email, e.g. `suresh.babu@academy.edu` | `PASSWORD` | TEACHER | Student directory, all timetables, own profile |
| any student email, e.g. `aarav.menon@student.academy.edu` | `PASSWORD` | STUDENT | Own profile, own timetable, own subjects only |

**Change these before deploying anywhere real.**

### Student and teacher sign-ins

Every student and teacher row gets a login of its own, keyed on the academy email: admitting a
student or teacher opens the account, changing the email moves the login with it, and deleting the
row removes both. The first password is the shared default from `portal.default-password` (override
with `ACADEMY_DEFAULT_PASSWORD`; it defaults to `PASSWORD`). The seeder also adopts the older single
`student` and `teacher` demo logins, renaming them to `aarav.menon@student.academy.edu` and
`suresh.babu@academy.edu` and resetting them to the default, so the hint on the sign-in page stays
accurate.

A one-time migration flag, `app_users.password_is_default`, records whether an account still carries
the password the academy handed out. Students and teachers change it themselves on **Account
settings** (current password + a new one of at least 8 characters); the flag clears, and from then
on only a BCrypt hash exists. The change takes effect at the next sign-in — the current session
stays signed in.

Administrators open a student's **Login** dialog from the register, or a teacher's from the faculty
directory. It shows the username and either the default password or "chosen by the student/teacher".
Because chosen passwords are only stored as hashes, they cannot be read back — the dialog can
instead **Reset to default** (revealing the value once) or **Set a new password** and hand it over.
Both write a fresh hash and update the flag. Administrators change their own password from the same
**Account settings** page every other role uses.

## 5. Pages

| Page | File | Purpose |
| --- | --- | --- |
| Sign in | `static/login.html` | Session login, CSRF-aware; takes a username, a student email or a teacher email |
| Dashboard | `static/index.html` | Role-aware overview: academy stats and today's classes, or a student's own day |
| Students | `static/students.html` | Search / filter roster, admit, edit, manage the student's sign-in, remove (admin) |
| Student profile | `static/student-profile.html` | Profile, guardian + contact details, weekly timetable, enrolled subjects, classmates; admins change the picture here |
| Teachers | `static/teachers.html` | Faculty directory cards with contact details and pictures; admin adds, edits, manages sign-ins, removes |
| Teacher profile | `static/teacher-profile.html` | Contact info, qualifications, weekly timetable, subjects and classes taught; admins change the picture here |
| Timetable | `static/schedule.html` | Week grid with class/section/teacher/subject filters; admin builds sessions |
| Contacts | `static/contacts.html` | Role-scoped people directory (see below) plus department desks and helplines grouped by category |
| Certificates | `static/certificates.html` | ADMIN only: tick one or many graduates, preview the wording, then download the certificate PDFs |
| Account settings | `static/account.html` | Every role: profile picture, sign-in details and a password change |
| Privacy policy | `static/privacy.html` | Public: what the portal stores, who can see it, cookies, retention and how to ask for removal |
| Terms and conditions | `static/terms.html` | Public: acceptable use, accounts, records and availability |

The public pages, the sign-in page and every authenticated page share the same stylesheet and the
six colour themes described in section 8. `favicon.svg` is declared in the head of every page.

## 6. REST API

All endpoints require a session cookie. Writes need the `X-XSRF-TOKEN` header (the client gets it
from `GET /api/auth/csrf`).

| Method | Path | Access |
| --- | --- | --- |
| `GET` | `/api/auth/csrf` | public |
| `GET` | `/api/auth/me` | any signed-in user |
| `POST` | `/api/auth/password` (`{currentPassword,newPassword}`) | any signed-in user |
| `GET` | `/api/profile/photo/{userId}` → the image bytes | any signed-in user |
| `POST` `DELETE` | `/api/profile/photo` (multipart `file`) | any signed-in user, own picture |
| `POST` `DELETE` | `/api/students/{id}/photo` · `/api/teachers/{id}/photo` (multipart `file`) | ADMIN |
| `GET` | `/api/dashboard` | any signed-in user |
| `GET` | `/api/students?q=&classLevel=&status=` | ADMIN, TEACHER |
| `GET` | `/api/students/{id}` · `/{id}/schedule` · `/{id}/subjects` · `/{id}/classmates` | ADMIN, TEACHER |
| `GET` | `/api/students/me` · `/me/schedule` · `/me/subjects` · `/me/classmates` | signed-in student |
| `POST` `PUT` `DELETE` | `/api/students/**`, `/api/students/{id}/subjects/{subjectId}` | ADMIN |
| `GET` | `/api/students/{id}/login` → login id + default-password flag | ADMIN |
| `POST` | `/api/students/{id}/login/reset` (`{password}`, or `{}` for the default) | ADMIN |
| `GET` | `/api/teachers`, `/api/teachers/{id}`, `/api/teachers/{id}/schedule` | any signed-in user |
| `GET` | `/api/teachers/me`, `/api/teachers/me/schedule` | signed-in teacher |
| `POST` `PUT` `DELETE` | `/api/teachers/**` | ADMIN |
| `GET` | `/api/teachers/{id}/login` → login id + default-password flag | ADMIN |
| `POST` | `/api/teachers/{id}/login/reset` (`{password}`, or `{}` for the default) | ADMIN |
| `GET` | `/api/schedules?dayOfWeek=&classLevel=&section=&teacherId=&subjectId=` | ADMIN, TEACHER |
| `GET` | `/api/schedules/class?classLevel=&section=` | any signed-in user |
| `POST` `PUT` `DELETE` | `/api/schedules/**` | ADMIN |
| `GET` | `/api/contacts`, `/api/contacts/categories`, `/api/subjects` | any signed-in user |
| `GET` | `/api/contacts/people` | any signed-in user, scoped to their own classes |
| `PUT` | `/api/contacts/administration` (`{fullName,email,phone}`) | ADMIN, own details |
| `POST` `PUT` `DELETE` | `/api/contacts/**` | ADMIN |
| `POST` | `/api/certificates` → `application/pdf` | ADMIN |
| `POST` | `/api/certificates/preview` → the same pages as JSON | ADMIN |

### Graduation certificates

`POST /api/certificates` takes `{"studentIds":[1,3],"conferredOn":"2026-12-15","term":"2026-ODD"}`
and answers with one landscape A4 page per graduate (`Content-Disposition` names the file,
`X-Certificate-Count` states how many pages were drawn). The picker in `certificates.html` is the
only caller: search or filter the register, tick students (or **All listed**), set the conferral date
and term, then **Generate certificates**. A row's **Certify only** button issues a single certificate
for that student. Selections are held by id, so filtering the list never drops a pick, and one run
covers up to 200 graduates.

`POST /api/certificates/preview` takes the same body and returns the same pages as JSON instead of a
PDF. **Preview first** opens them as certificate sheets in a dialog, and a row's **Preview** shows
just that graduate without touching the selection, so the wording can be read before anything is
saved. The dialog's **Generate** button runs the download for the same selection. Because both
answers come from the one `CertificatePageDto` the PDF is drawn from, the preview cannot drift from
the file.

Each page prints the academy name from `portal.certificate.academy-name` (override with
`CERTIFICATE_ACADEMY_NAME`), the graduate's roll number and programme, the subjects they were
enrolled in, the conferral date and term, and two signature rules — the
programme's Head of Department, or the first of that programme's faculty by surname when no HoD is on
staff, beside the administrator who issued the run. A `CERT-<roll>-<yyyyMMdd>` serial line closes the
page.

### Profile pictures

`POST /api/profile/photo` takes a multipart `file` — PNG, JPEG or WebP, up to 2 MB. The type is
sniffed from the bytes rather than trusted from the part headers, so a renamed HTML file is refused
with `400`. `GET /api/profile/photo/{userId}` answers with the stored bytes and `DELETE` clears the
picture. Every `/api/auth/me` answer carries a versioned `photoUrl` (or `null`), so a replaced
picture bypasses the browser cache and the sidebar updates without a reload. Upload and removal live
on **Account settings**, which every role can open.

A student's or teacher's picture is stored on their sign-in row, so their record carries it too:
`StudentDto`, `TeacherDto` and the `/api/contacts/people` rows each expose a `photoUrl`. The roster,
faculty cards, contact lists and both profile pages render it for every signed-in role, with
initials filling in where no picture exists. Administrators manage it from either profile page with
**Change photo** / **Remove photo**:

| Method | Path | Access |
| --- | --- | --- |
| `POST` | `/api/students/{id}/photo` (multipart `file`) | ADMIN |
| `DELETE` | `/api/students/{id}/photo` | ADMIN |
| `POST` | `/api/teachers/{id}/photo` (multipart `file`) | ADMIN |
| `DELETE` | `/api/teachers/{id}/photo` | ADMIN |

Every upload and removal answers with the updated record. A person with no sign-in row has nowhere
to keep a picture; each student and teacher is given one when they are admitted or added.

### Who the contacts page shows

`/api/contacts/people` works out the answerable list from the session and the timetable, so a client
cannot widen it:

| Signed-in as | Sees |
| --- | --- |
| STUDENT | The faculty who teach their class, with each teacher's phone, email and the subjects they are responsible for, plus the administration contact |
| TEACHER | The students in every class and division on their own timetable (roll number, student phone, guardian phone and email), plus the administration contact |
| ADMIN | All faculty and all students, plus the administration contact |

The administration contact is the enabled `ADMIN` account: name, sign-in id, phone and email. Every
role sees it, and it is the only person row in the directory with an **Edit details** button — shown
to the signed-in administrator on their own row and nowhere else. Saving writes `app_users` through
`PUT /api/contacts/administration`, which is `hasRole('ADMIN')`; a teacher or student calling it gets
`403`.

The manually maintained desk rows (`/api/contacts`) stay below that list for every role and are
editable by ADMIN only. Roll numbers and every other student field are likewise writable only
through `PUT /api/students/{id}`, which is ADMIN-only — a student's own profile page has no edit
control, and a teacher's roster shows the record read-only.

Controls above the list, all applied in the browser to whatever the session is allowed to see:

- **Search** — matches name, roll or staff number, phone, guardian number, email, programme, class
  division, desk name, department, location and opening hours
- **Sort by** — programme then name (default), name A–Z, name Z–A, roll / staff number, phone number
- **Show** — Everyone, Subject faculty, Students, Administration, Office desks
- **Programme** and **Class division** — only rendered when the scoped list actually holds more than
  one of them, so a student with a single track sees neither
- **Desk category** chips — filtered server-side through `/api/contacts?category=`
- **Clear** — resets search, chips, selects and sorting

The header subtitle reports `12 of 26 contact(s) match the current filters` while anything is active.

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
app_users       login + role, points at a student or teacher row; phone is the administrator's
                published contact line; password_is_default marks a password the academy handed out
profile_photos  one picture per app_users row, removed with the account
```

Business rules enforced in the service layer, not just the UI:

- Roll numbers and emails are unique per student; employee codes and emails per teacher.
- A class session must start before it ends.
- No two sessions can overlap for the same class/section, and a teacher cannot be double-booked —
  the API returns `409 Conflict` with the clashing slot named.
- Deleting a student removes their enrollments; deleting a teacher removes their sessions.
- A student's or teacher's sign-in is opened, renamed and removed with the record. It starts on the
  academy default password; changing it from **Account settings** clears `password_is_default`, and
  only the hash is kept from then on.
- A picture is attached to that sign-in, so it is deleted with it. Pictures are readable by every
  signed-in role; setting or clearing one for a student or teacher is ADMIN only.
- Students cannot read another student's record: `/api/students/{id}` is ADMIN/TEACHER only, and
  self-service always resolves the id from the session, never from the request.
- A student's record — roll number, programme, section, status and the rest — is writable only
  through the ADMIN-only `POST`/`PUT`/`DELETE /api/students/**`. Students read their own record but
  cannot edit it; teachers can read the roster they are allowed to see and cannot edit it either.
- The administration contact is writable only by an administrator, and only that administrator's own
  row. Desk rows in `contacts` are ADMIN-only for the same reason.

## 8. Themes, legal pages and launch

**Six colour themes.** The palette lives in `css/main.css` as custom properties: `slate` (the
default), `evergreen`, `marine`, `terracotta`, `sandstone` and `graphite`. Each one recolours the
accent, sidebar, badges and surfaces; no theme uses a gradient. `js/theme.js` runs before the
stylesheet, applies the stored choice to `<html data-theme="…">`, and fills every
`select[data-theme-picker]` — the sidebar footer and the sign-in and legal pages. The choice is kept
in `localStorage` under `academy.theme`, so it follows the browser through a sign-out and back.

**Favicon.** `favicon.svg` (navy tile, white A) is declared in the head of every page and served
publicly.

**Legal pages.** `privacy.html` and `terms.html` are public (no session) and linked from the footer
of every page. They describe what the portal stores, who can see it, the two cookies it sets
(`JSESSIONID`, `XSRF-TOKEN`), retention and the office contact for removal. Update the dates and the
office address before publishing.

**Custom domain and HTTPS.** The app itself does not own a domain; DNS and certificates belong to
whoever hosts it. The intended layout is a TLS-terminating reverse proxy (Caddy, nginx or the
hosting platform's load balancer) in front of the jar:

1. Point an `A`/`AAAA` (or `CNAME`) record for the chosen name at the host.
2. Terminate TLS at the proxy and forward `X-Forwarded-Proto` and `X-Forwarded-Host`.
3. Run the jar behind it on `PORT` — `server.forward-headers-strategy=framework` is already set, so
   redirects and generated links use the public `https://` address.
4. Re-check `/login.html`, `/privacy.html`, `/terms.html` and the write endpoints over the real
   hostname; CSRF cookies are same-origin, so the portal must be reached at one name only.

The domain record and certificate must be created in the DNS and hosting accounts for the site;
they cannot be connected from this build environment.

## 9. Layout

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

## 10. Troubleshooting

| Symptom | Cause / fix |
| --- | --- |
| `Access denied for user 'root'@'localhost'` | Set `MYSQL_USER` / `MYSQL_PASSWORD` correctly |
| `Communications link failure` | MySQL service is stopped, or the port is wrong (`MYSQL_PORT`) |
| Login posts return `403` | The CSRF cookie/header is missing — sign in through `login.html`, and make sure the app is served over `http://localhost` (cookies are scoped) |
| Empty dashboard but app started | `db/data.sql` did not run: check the boot log for `Executing SQL script from class path resource` |
| Profile picture upload rejected | It must be a PNG, JPEG or WebP under 2 MB — the server checks the bytes, not the file name |
| Port 8080 busy | `SERVER_PORT=9090` (the app reads `PORT`), then open `http://localhost:9090` |
