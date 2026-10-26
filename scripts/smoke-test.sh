#!/usr/bin/env bash
# End-to-end smoke test for the academy portal.
# Usage: bash scripts/smoke-test.sh            (tests admin, teacher and student)
# Requires the app running on :8080 with the demo seed data loaded.
set -u
BASE="${BASE:-http://localhost:8080}"
RUN_TAG="ACAD-TST-$(date +%H%M%S)"
PASS=0
FAIL=0

login() { # login JAR_FILE user pass
    local jar="$1" user="$2" pass="$3" tk
    rm -f "$jar"
    tk=$(curl -s -b "$jar" -c "$jar" "$BASE/api/auth/csrf" | sed -E 's/.*"token":"([^"]+)".*/\1/')
    curl -s -o /dev/null -w "%{http_code}" -b "$jar" -c "$jar" -X POST "$BASE/login" \
        -H "X-XSRF-TOKEN: $tk" -H 'Content-Type: application/x-www-form-urlencoded' \
        --data-urlencode "username=$user" --data-urlencode "password=$pass"
}

check_login() { # check_login LABEL USER PASS -> proves the credential opens a session for that username
    local label="$1" user="$2" pass="$3" jar code me
    jar=$(mktemp)
    code=$(login "$jar" "$user" "$pass")
    me=$(curl -s -o /tmp/portal-resp.json -w "%{http_code}" -b "$jar" -c "$jar" \
        "$BASE/api/auth/me" -H 'Accept: application/json')
    if [ "$code" = "302" ] && [ "$me" = "200" ] && grep -qF -- "\"username\":\"$user\"" /tmp/portal-resp.json; then
        PASS=$((PASS + 1)); printf '  ok   %-46s signed in\n' "$label"
    else
        FAIL=$((FAIL + 1)); printf '  FAIL %-46s login=%s me=%s\n' "$label" "$code" "$me"
    fi
    rm -f "$jar"
}

hit() { # hit JAR METHOD PATH [BODY] -> writes body to /tmp/portal-resp.json, prints status
    local jar="$1" method="$2" path="$3" body="${4:-}" tk
    tk=$(curl -s -b "$jar" -c "$jar" "$BASE/api/auth/csrf" | sed -E 's/.*"token":"([^"]+)".*/\1/')
    # Only API calls ask for JSON; a page request must keep the redirect-to-sign-in behaviour.
    local -a accept=()
    case "$path" in /api/*) accept=(-H 'Accept: application/json') ;; esac
    if [ -z "$body" ]; then
        curl -s -o /tmp/portal-resp.json -w "%{http_code}" -b "$jar" -c "$jar" -X "$method" "$BASE$path" \
            -H "X-XSRF-TOKEN: $tk" "${accept[@]}"
    else
        curl -s -o /tmp/portal-resp.json -w "%{http_code}" -b "$jar" -c "$jar" -X "$method" "$BASE$path" \
            -H "X-XSRF-TOKEN: $tk" -H 'Content-Type: application/json' "${accept[@]}" -d "$body"
    fi
}

check() { # check LABEL EXPECTED METHOD PATH [BODY]
    local label="$1" expected="$2" method="$3" path="$4" body="${5:-}" jar="$JAR" got detail
    got=$(hit "$jar" "$method" "$path" "$body")
    detail=$(sed -E 's/.*"detail":"([^"]*)".*/\1/' /tmp/portal-resp.json 2>/dev/null | head -1)
    [ "$detail" = "$(cat /tmp/portal-resp.json)" ] && detail=''
    if [ "$got" = "$expected" ]; then
        PASS=$((PASS + 1))
        printf '  ok   %-46s %s %s\n' "$label" "$got" "${detail:+($detail)}"
    else
        FAIL=$((FAIL + 1))
        printf '  FAIL %-46s got %s, want %s  %s\n' "$label" "$got" "$expected" "$(head -c 180 /tmp/portal-resp.json)"
    fi
}

first_id() { sed -E 's/.*"id":([0-9]+).*/\1/' /tmp/portal-resp.json | head -1; }

# The people list is scoped server-side, so asserting a status code is not enough - these two
# helpers check who is actually in the body of the request checked just above them.
expect_in() {
    if grep -qF -- "$2" /tmp/portal-resp.json; then
        PASS=$((PASS + 1)); printf '  ok   %-46s listed\n' "$1"
    else
        FAIL=$((FAIL + 1)); printf '  FAIL %-46s missing [%s]\n' "$1" "$2"
    fi
}

expect_out() {
    if grep -qF -- "$2" /tmp/portal-resp.json; then
        FAIL=$((FAIL + 1)); printf '  FAIL %-46s leaked [%s]\n' "$1" "$2"
    else
        PASS=$((PASS + 1)); printf '  ok   %-46s hidden\n' "$1"
    fi
}

# A certificate answer is a binary PDF, so it needs its own file and its own assertions:
# the server must say so, the bytes must open with the PDF magic and carry one page per graduate.
cert() { # cert LABEL BODY EXPECTED_PAGES EXPECTED_FILENAME_FRAGMENT
    local label="$1" body="$2" want_pages="$3" want_name="$4" tk status pages
    tk=$(curl -s -b "$JAR" -c "$JAR" "$BASE/api/auth/csrf" | sed -E 's/.*"token":"([^"]+)".*/\1/')
    status=$(curl -s -D /tmp/portal-headers.txt -o /tmp/portal-cert.pdf -w "%{http_code}" \
        -b "$JAR" -c "$JAR" -X POST "$BASE/api/certificates" \
        -H "X-XSRF-TOKEN: $tk" -H 'Content-Type: application/json' -d "$body")
    pages=$(grep -aoE '/Type[[:space:]]*/Page([^s]|$)' /tmp/portal-cert.pdf | wc -l | tr -d ' ')

    if [ "$status" = "200" ] && head -c 5 /tmp/portal-cert.pdf | grep -q '%PDF-' \
        && grep -qi 'application/pdf' /tmp/portal-headers.txt \
        && [ "$pages" = "$want_pages" ] && grep -qi "filename=.*$want_name" /tmp/portal-headers.txt; then
        PASS=$((PASS + 1))
        printf '  ok   %-46s 200 pdf, %s page(s)\n' "$label" "$pages"
    else
        FAIL=$((FAIL + 1))
        printf '  FAIL %-46s got %s, %s page(s), want %s and [%s]\n' "$label" "$status" "$pages" "$want_pages" "$want_name"
    fi
}

# A profile picture arrives as multipart, so it needs its own request helper. The answer is left
# in /tmp/portal-resp.json like the other helpers, for expect_in to read.
photo_upload() { # photo_upload LABEL PATH FILE EXPECTED_STATUS
    local label="$1" path="$2" file="$3" want="$4" tk got
    tk=$(curl -s -b "$JAR" -c "$JAR" "$BASE/api/auth/csrf" | sed -E 's/.*"token":"([^"]+)".*/\1/')
    got=$(curl -s -o /tmp/portal-resp.json -w "%{http_code}" -b "$JAR" -c "$JAR" \
        -X POST "$BASE$path" -H "X-XSRF-TOKEN: $tk" -H 'Accept: application/json' \
        -F "file=@$file;type=image/png")
    if [ "$got" = "$want" ]; then
        PASS=$((PASS + 1)); printf '  ok   %-46s %s\n' "$label" "$got"
    else
        FAIL=$((FAIL + 1)); printf '  FAIL %-46s got %s, want %s  %s\n' "$label" "$got" "$want" "$(head -c 180 /tmp/portal-resp.json)"
    fi
}

photo_get() { # photo_get LABEL USER_ID EXPECTED_STATUS
    local label="$1" id="$2" want="$3" got
    got=$(curl -s -D /tmp/portal-headers.txt -o /tmp/portal-photo.bin -w "%{http_code}" -b "$JAR" "$BASE/api/profile/photo/$id")
    if [ "$got" = "$want" ] && { [ "$want" != "200" ] || grep -qi 'content-type: image/png' /tmp/portal-headers.txt; }; then
        PASS=$((PASS + 1)); printf '  ok   %-46s %s\n' "$label" "$got"
    else
        FAIL=$((FAIL + 1)); printf '  FAIL %-46s got %s, want %s\n' "$label" "$got" "$want"
    fi
}

photo_url_get() { # photo_url_get LABEL RELATIVE_URL EXPECTED_STATUS -> any signed-in role may fetch it
    local label="$1" url="$2" want="$3" got
    got=$(curl -s -D /tmp/portal-headers.txt -o /tmp/portal-photo.bin -w "%{http_code}" -b "$JAR" "$BASE/$url")
    if [ "$got" = "$want" ] && { [ "$want" != "200" ] || grep -qi 'content-type: image/png' /tmp/portal-headers.txt; }; then
        PASS=$((PASS + 1)); printf '  ok   %-46s %s\n' "$label" "$got"
    else
        FAIL=$((FAIL + 1)); printf '  FAIL %-46s got %s, want %s\n' "$label" "$got" "$want"
    fi
}

first_photo_url() { sed -E 's/.*"photoUrl":"([^"]+)".*/\1/' /tmp/portal-resp.json | head -1; }

echo "== admin =="
JAR=$(mktemp)
[ "$(login "$JAR" admin admin123)" = "302" ] || { echo "admin login failed"; exit 1; }
check "session identity" 200 GET /api/auth/me
ADMIN_UID=$(first_id)
check "dashboard" 200 GET /api/dashboard
check "student directory" 200 GET '/api/students?classLevel=Full%20Stack%20Java'
check "student detail" 200 GET /api/students/1
check "student schedule" 200 GET /api/students/1/schedule
check "student subjects" 200 GET /api/students/1/subjects
check "student classmates" 200 GET /api/students/1/classmates
check "teacher directory" 200 GET /api/teachers
check "teacher schedule" 200 GET /api/teachers/1/schedule
check "teacher sign-in info" 200 GET /api/teachers/1/login
expect_in "teacher signs in as the email" 'suresh.babu@academy.edu'
check "teacher reset to default" 200 POST /api/teachers/1/login/reset '{}'
expect_in "teacher reset hands back the default" '"password":"PASSWORD"'
check "timetable filtered" 200 GET '/api/schedules?classLevel=Full%20Stack%20Java&section=A'
check "subject catalog" 200 GET /api/subjects
check "contact directory" 200 GET /api/contacts
check "contact categories" 200 GET /api/contacts/categories
check "people directory" 200 GET /api/contacts/people
expect_in "admin sees faculty" '"kind":"FACULTY"'
expect_in "admin sees students" '"kind":"STUDENT"'
expect_in "admin sees a roll number" 'ACAD-1101'
expect_in "admin sees a guardian line" '+91 90000 20101'
expect_in "rows carry a programme to filter by" '"group":"Data Analysis"'
expect_in "rows carry a division to filter by" '"division":"Section B"'
expect_out "admin list is not padded with admins" '"kind":"ADMINISTRATION"'

mkdir -p target/smoke
printf 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==' \
    | base64 -d > target/smoke/one.png
printf 'this is not an image\n' > target/smoke/fake.png
photo_upload "profile picture accepted" /api/profile/photo target/smoke/one.png 200
expect_in "picture url handed back" '"photoUrl":"api/profile/photo/'
photo_get "picture served back" "$ADMIN_UID" 200
photo_upload "non-image refused" /api/profile/photo target/smoke/fake.png 400
check "picture removed" 200 DELETE /api/profile/photo
photo_get "removed picture is gone" "$ADMIN_UID" 404

STUDENT='{"rollNumber":"'"$RUN_TAG"'","firstName":"Smoke","lastName":"Cadet","email":"'"$RUN_TAG"'@student.academy.edu","phone":"+91 90000 99999","dateOfBirth":"2009-02-02","gender":"OTHER","classLevel":"Full Stack Java","section":"A","city":"Bengaluru","guardianName":"Guardian Bot","guardianPhone":"+91 90000 88888","status":"ACTIVE"}'
check "admit student" 200 POST /api/students "$STUDENT"
NEW_ID=$(first_id)
check "duplicate roll rejected" 409 POST /api/students "$STUDENT"
check "update student" 200 PUT "/api/students/$NEW_ID" "$(echo "$STUDENT" | sed 's/"city":"Bengaluru"/"city":"Pune"/')"

# Administrators manage a student's picture from the record; the student can still replace it
# from Account settings. The picture lives on the sign-in row, so it needs one to exist.
photo_upload "admin sets a student picture" "/api/students/$NEW_ID/photo" target/smoke/one.png 200
expect_in "student record carries the picture" '"photoUrl":"api/profile/photo/'
photo_url_get "student picture readable by the admin" "$(first_photo_url)" 200
photo_upload "non-image student picture refused" "/api/students/$NEW_ID/photo" target/smoke/fake.png 400
photo_upload "picture for a ghost student" /api/students/999999/photo target/smoke/one.png 404
check "admin removes the student picture" 200 DELETE "/api/students/$NEW_ID/photo"
expect_in "student record drops the picture" '"photoUrl":null'

# The student login is the academy email on the shared default password. A freshly admitted
# student walks through sign-in, a self-service change, and an administrator reset.
check "sign-in is listed" 200 GET "/api/students/$NEW_ID/login"
expect_in "sign-in is the academy email" "$RUN_TAG@student.academy.edu"
expect_in "sign-in starts on the default" '"passwordIsDefault":true'
expect_in "default password is readable" '"defaultPassword":"PASSWORD"'

SJAR=$(mktemp)
[ "$(login "$SJAR" "$RUN_TAG@student.academy.edu" PASSWORD)" = "302" ] || { echo "student email login failed"; exit 1; }
ADMIN_JAR=$JAR
JAR=$SJAR
check "student portal after email login" 200 GET /api/students/me
check "wrong current password refused" 400 POST /api/auth/password '{"currentPassword":"not-the-password","newPassword":"FreshPass2026"}'
check "student changes own password" 200 POST /api/auth/password '{"currentPassword":"PASSWORD","newPassword":"FreshPass2026"}'
JAR=$ADMIN_JAR
check "change clears the default flag" 200 GET "/api/students/$NEW_ID/login"
expect_in "admin is told a password was chosen" '"passwordIsDefault":false'
check "admin resets the password" 200 POST "/api/students/$NEW_ID/login/reset" '{}'
expect_in "reset hands the password back once" '"password":"PASSWORD"'
expect_in "reset marks the default again" '"passwordIsDefault":true'
rm -f "$SJAR"

check "delete student" 200 DELETE "/api/students/$NEW_ID"
check "sign-in goes with the student" 404 GET "/api/students/$NEW_ID/login"

STUDENT2='{"rollNumber":"'"$RUN_TAG"'B","firstName":"Enroll","lastName":"Test","email":"'"$RUN_TAG"'b@student.academy.edu","classLevel":"Full Stack Python","section":"A","status":"ACTIVE"}'
check "admit second student" 200 POST /api/students "$STUDENT2"
ENROLL_ID=$(first_id)
check "enroll in subject" 200 POST "/api/students/$ENROLL_ID/subjects/1" '{}'
check "subjects show enrollment" 200 GET "/api/students/$ENROLL_ID/subjects"
check "drop subject" 200 DELETE "/api/students/$ENROLL_ID/subjects/1"

# Kept for the teacher and student sections: a picture set on this student must be readable
# by every signed-in role, the way it is rendered on the contacts page.
photo_upload "picture left on a student" "/api/students/$ENROLL_ID/photo" target/smoke/one.png 200
expect_in "second student carries the picture" '"photoUrl":"api/profile/photo/'
SHARED_PHOTO=$(first_photo_url)
check "missing student 404" 404 GET /api/students/999999
check "invalid payload 400" 400 POST /api/students '{"rollNumber":"","firstName":"","email":"nope","classLevel":"","status":"WEIRD"}'

CLASH='{"subjectId":3,"teacherId":5,"classLevel":"Full Stack Java","section":"A","room":"Block A - 201","dayOfWeek":1,"startTime":"08:10:00","endTime":"08:40:00","term":"2026-ODD"}'
check "class time clash rejected" 409 POST /api/schedules "$CLASH"
TCLASH='{"subjectId":6,"teacherId":3,"classLevel":"Full Stack Python","section":"B","room":"Lab 9","dayOfWeek":2,"startTime":"08:00:00","endTime":"08:45:00","term":"2026-ODD"}'
check "teacher double booking rejected" 409 POST /api/schedules "$TCLASH"
BADTIME='{"subjectId":13,"teacherId":7,"classLevel":"UI/UX","section":"A","room":"Room 1","dayOfWeek":3,"startTime":"11:00:00","endTime":"10:00:00","term":"2026-ODD"}'
check "end before start rejected" 400 POST /api/schedules "$BADTIME"
GOOD='{"subjectId":13,"teacherId":7,"classLevel":"UI/UX","section":"A","room":"Room 7","dayOfWeek":3,"startTime":"13:00:00","endTime":"13:45:00","term":"2026-ODD"}'
check "valid session created" 200 POST /api/schedules "$GOOD"
SESSION_ID=$(first_id)
check "session updated" 200 PUT "/api/schedules/$SESSION_ID" "$(echo "$GOOD" | sed 's/"room":"Room 7"/"room":"Room 8"/')"
check "session deleted" 200 DELETE "/api/schedules/$SESSION_ID"

TEACHER='{"employeeCode":"'"$RUN_TAG"'T","firstName":"Smoke","lastName":"Faculty","department":"QA","email":"'"$RUN_TAG"'t@academy.edu","phone":"+91 90000 77777","designation":"Guest Faculty","status":"ACTIVE"}'
check "add teacher" 200 POST /api/teachers "$TEACHER"
TEACHER_ID=$(first_id)
check "new teacher gets a sign-in" 200 GET "/api/teachers/$TEACHER_ID/login"
expect_in "teacher login is the academy email" "$RUN_TAG"'t@academy.edu'
check "teacher email updated" 200 PUT "/api/teachers/$TEACHER_ID" "$(echo "$TEACHER" | sed 's/t@academy\.edu/t2@academy.edu/')"
check "teacher login follows the email" 200 GET "/api/teachers/$TEACHER_ID/login"
expect_in "renamed teacher signs in as the new email" "$RUN_TAG"'t2@academy.edu'
check "set a teacher password" 200 POST "/api/teachers/$TEACHER_ID/login/reset" '{"password":"TempPass#2026"}'
expect_in "teacher password is no longer the default" '"passwordIsDefault":false'
check_login "teacher signs in with the new password" "$RUN_TAG"t2@academy.edu 'TempPass#2026'
check "too short a teacher password rejected" 400 POST "/api/teachers/$TEACHER_ID/login/reset" '{"password":"short"}'

photo_upload "admin sets a teacher picture" "/api/teachers/$TEACHER_ID/photo" target/smoke/one.png 200
expect_in "teacher record carries the picture" '"photoUrl":"api/profile/photo/'
TEACHER_PHOTO=$(first_photo_url)
photo_url_get "teacher picture readable by the admin" "$TEACHER_PHOTO" 200
check "faculty contact photos" 200 GET /api/contacts/people
expect_in "faculty contact rows carry it" "$TEACHER_PHOTO"
check "admin removes the teacher picture" 200 DELETE "/api/teachers/$TEACHER_ID/photo"
expect_in "teacher record drops the picture" '"photoUrl":null'
photo_upload "non-image teacher picture refused" "/api/teachers/$TEACHER_ID/photo" target/smoke/fake.png 400

CONTACT='{"category":"SMOKE","name":"Smoke Test Desk","department":"QA","phone":"+91 80 0000 0000","email":"smoke@academy.edu","location":"Basement","availableHours":"Never"}'
check "contact created" 200 POST /api/contacts "$CONTACT"
CONTACT_ID=$(first_id)
check "contact updated" 200 PUT "/api/contacts/$CONTACT_ID" "$(echo "$CONTACT" | sed 's/"name":"Smoke Test Desk"/"name":"Renamed Desk"/')"
check "contact deleted" 200 DELETE "/api/contacts/$CONTACT_ID"

cert "one certificate" '{"studentIds":[1],"conferredOn":"2026-10-30","term":"2026-ODD"}' 1 'graduation-certificate-ACAD-1101.pdf'
cert "one certificate per graduate" '{"studentIds":[1,3,9],"conferredOn":"2026-12-15","term":"2026-ODD"}' 3 'graduation-certificates-3-students.pdf'
check "certificate without a date" 400 POST /api/certificates '{"studentIds":[1]}'
check "certificate with no student" 400 POST /api/certificates '{"studentIds":[],"conferredOn":"2026-10-30"}'
check "certificate for a ghost" 404 POST /api/certificates '{"studentIds":[999999],"conferredOn":"2026-10-30"}'

check "certificate preview" 200 POST /api/certificates/preview '{"studentIds":[1,9],"conferredOn":"2026-10-30","term":"2026-ODD"}'
expect_in "preview carries the heading" '"heading":"CERTIFICATE OF GRADUATION"'
expect_in "preview carries each graduate" 'Roll number ACAD-1101'
expect_in "preview names the signing faculty" 'Kavita Anand'
expect_out "preview leaves out the class division" 'Section '
expect_out "preview leaves out the credit total" 'credits'
check "preview with no student" 400 POST /api/certificates/preview '{"studentIds":[],"conferredOn":"2026-10-30"}'

echo
echo "== teacher =="
JAR=$(mktemp)
[ "$(login "$JAR" suresh.babu@academy.edu PASSWORD)" = "302" ] || { echo "teacher login failed"; exit 1; }
check "own identity" 200 GET /api/auth/me
expect_in "teacher signs in as the email" 'suresh.babu@academy.edu'
check "own profile" 200 GET /api/teachers/me
check "own schedule" 200 GET /api/teachers/me/schedule
check "student directory allowed" 200 GET /api/students
check "full timetable allowed" 200 GET /api/schedules
check "class contacts" 200 GET /api/contacts/people
expect_in "teacher reaches division A" 'ACAD-1101'
expect_in "teacher reaches division B" 'ACAD-1103'
expect_in "teacher gets class division" 'Full Stack Java - Section B'
expect_in "teacher gets guardian number" '+91 90000 20103'
expect_in "teacher rows group by programme" '"group":"Full Stack Java"'
expect_in "teacher rows split by division" '"division":"Section A"'
expect_out "teacher cannot reach another programme" 'ACAD-1203'
expect_out "teacher list holds no faculty" '"kind":"FACULTY"'
check "cannot admit student" 403 POST /api/students "$STUDENT"
check "cannot edit session" 403 PUT "/api/schedules/1" "$GOOD"
check "cannot add contact" 403 POST /api/contacts "$CONTACT"
check "cannot delete student" 403 DELETE "/api/students/$ENROLL_ID"
check "cannot read a student sign-in" 403 GET /api/students/1/login
check "cannot reset a student password" 403 POST /api/students/1/login/reset '{}'
check "cannot issue certificates" 403 POST /api/certificates '{"studentIds":[1],"conferredOn":"2026-10-30"}'
check "cannot preview certificates" 403 POST /api/certificates/preview '{"studentIds":[1],"conferredOn":"2026-10-30"}'
check "cannot read teacher sign-ins" 403 GET /api/teachers/1/login
check "cannot reset a teacher password" 403 POST /api/teachers/1/login/reset '{}'
photo_url_get "student picture visible to a teacher" "$SHARED_PHOTO" 200
photo_upload "teacher cannot set a student picture" "/api/students/$ENROLL_ID/photo" target/smoke/one.png 403
check "cannot remove a student picture" 403 DELETE "/api/students/$ENROLL_ID/photo"

echo
echo "== student =="
JAR=$(mktemp)
[ "$(login "$JAR" aarav.menon@student.academy.edu PASSWORD)" = "302" ] || { echo "student login failed"; exit 1; }
check "own identity" 200 GET /api/auth/me
expect_in "signs in with the academy email" 'aarav.menon@student.academy.edu'
check "own profile" 200 GET /api/students/me
check "own schedule" 200 GET /api/students/me/schedule
check "own subjects" 200 GET /api/students/me/subjects
check "own classmates" 200 GET /api/students/me/classmates
check "own class timetable" 200 GET '/api/schedules/class?classLevel=Full%20Stack%20Java&section=A'
check "teacher directory readable" 200 GET /api/teachers
check "contacts readable" 200 GET /api/contacts
check "own contacts" 200 GET /api/contacts/people
expect_in "student reaches subject faculty" 'Suresh Babu'
expect_in "student gets faculty number" '+91 98450 11001'
expect_in "student sees what they teach" 'Java Core and OOP'
expect_in "student reaches admin mailbox" 'admin@academy.edu'
expect_out "student list is not fellow students" '"kind":"STUDENT"'
expect_out "student cannot reach another programme faculty" 'Neelam Kaur'
check "cannot read other student" 403 GET /api/students/2
check "cannot browse roster" 403 GET /api/students
check "cannot read other schedule" 403 GET /api/students/2/schedule
check "cannot edit own record" 403 PUT /api/students/1 "$STUDENT"
check "cannot read a sign-in" 403 GET /api/students/1/login
check "cannot reset a password" 403 POST /api/students/1/login/reset '{}'
check "cannot add teacher" 403 POST /api/teachers '{"employeeCode":"X","firstName":"a","lastName":"b","department":"c","email":"x@y.edu"}'
check "cannot issue a certificate" 403 POST /api/certificates '{"studentIds":[1],"conferredOn":"2026-10-30"}'
check "cannot preview a certificate" 403 POST /api/certificates/preview '{"studentIds":[1],"conferredOn":"2026-10-30"}'
check "cannot read teacher sign-ins" 403 GET /api/teachers/1/login
check "cannot reset a teacher password" 403 POST /api/teachers/1/login/reset '{}'
photo_url_get "student picture visible to a student" "$SHARED_PHOTO" 200
photo_upload "student cannot set another picture" "/api/students/$ENROLL_ID/photo" target/smoke/one.png 403
check "cannot remove another picture" 403 DELETE "/api/students/$ENROLL_ID/photo"

echo
echo "== access control without a session =="
rm -f "$JAR"
check "dashboard needs login" 401 GET /api/dashboard
check "students need login" 401 GET /api/students
check "people list needs login" 401 GET /api/contacts/people
check "teacher sign-in needs login" 401 GET /api/teachers/1/login
check "certificates need login" 401 POST /api/certificates '{"studentIds":[1],"conferredOn":"2026-10-30"}'
check "preview needs login" 401 POST /api/certificates/preview '{"studentIds":[1],"conferredOn":"2026-10-30"}'
check "sign-in info needs login" 401 GET /api/students/1/login
check "password change needs login" 401 POST /api/auth/password '{"currentPassword":"x","newPassword":"yyyyyyyy"}'
check "profile picture needs login" 401 GET /api/profile/photo/1
check "portal page redirects" 302 GET /index.html
check "sign-in page is public" 200 GET /login.html

echo
echo "cleanup"
JAR=$(mktemp); login "$JAR" admin admin123 > /dev/null
check "remove leftover student 2" 200 DELETE "/api/students/$ENROLL_ID"
[ -n "${TEACHER_ID:-}" ] && check "remove leftover teacher" 200 DELETE "/api/teachers/$TEACHER_ID"
rm -f "$JAR"

echo
echo "passed=$PASS failed=$FAIL"
[ "$FAIL" -eq 0 ]
