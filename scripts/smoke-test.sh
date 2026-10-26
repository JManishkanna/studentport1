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

echo "== admin =="
JAR=$(mktemp)
[ "$(login "$JAR" admin admin123)" = "302" ] || { echo "admin login failed"; exit 1; }
check "session identity" 200 GET /api/auth/me
check "dashboard" 200 GET /api/dashboard
check "student directory" 200 GET '/api/students?classLevel=Grade%2011'
check "student detail" 200 GET /api/students/1
check "student schedule" 200 GET /api/students/1/schedule
check "student subjects" 200 GET /api/students/1/subjects
check "student classmates" 200 GET /api/students/1/classmates
check "teacher directory" 200 GET /api/teachers
check "teacher schedule" 200 GET /api/teachers/1/schedule
check "timetable filtered" 200 GET '/api/schedules?classLevel=Grade%2011&section=A'
check "subject catalog" 200 GET /api/subjects
check "contact directory" 200 GET /api/contacts
check "contact categories" 200 GET /api/contacts/categories

STUDENT='{"rollNumber":"'"$RUN_TAG"'","firstName":"Smoke","lastName":"Cadet","email":"'"$RUN_TAG"'@student.academy.edu","phone":"+91 90000 99999","dateOfBirth":"2009-02-02","gender":"OTHER","classLevel":"Grade 11","section":"A","city":"Bengaluru","guardianName":"Guardian Bot","guardianPhone":"+91 90000 88888","status":"ACTIVE"}'
check "admit student" 200 POST /api/students "$STUDENT"
NEW_ID=$(first_id)
check "duplicate roll rejected" 409 POST /api/students "$STUDENT"
check "update student" 200 PUT "/api/students/$NEW_ID" "$(echo "$STUDENT" | sed 's/"city":"Bengaluru"/"city":"Pune"/')"
check "delete student" 200 DELETE "/api/students/$NEW_ID"

STUDENT2='{"rollNumber":"'"$RUN_TAG"'B","firstName":"Enroll","lastName":"Test","email":"'"$RUN_TAG"'b@student.academy.edu","classLevel":"Grade 12","section":"A","status":"ACTIVE"}'
check "admit second student" 200 POST /api/students "$STUDENT2"
ENROLL_ID=$(first_id)
check "enroll in subject" 200 POST "/api/students/$ENROLL_ID/subjects/1" '{}'
check "subjects show enrollment" 200 GET "/api/students/$ENROLL_ID/subjects"
check "drop subject" 200 DELETE "/api/students/$ENROLL_ID/subjects/1"
check "missing student 404" 404 GET /api/students/999999
check "invalid payload 400" 400 POST /api/students '{"rollNumber":"","firstName":"","email":"nope","classLevel":"","status":"WEIRD"}'

CLASH='{"subjectId":2,"teacherId":2,"classLevel":"Grade 11","section":"A","room":"Block A - 201","dayOfWeek":1,"startTime":"08:10:00","endTime":"08:40:00","term":"2026-ODD"}'
check "class time clash rejected" 409 POST /api/schedules "$CLASH"
TCLASH='{"subjectId":5,"teacherId":2,"classLevel":"Grade 12","section":"B","room":"Lab 9","dayOfWeek":2,"startTime":"08:00:00","endTime":"08:45:00","term":"2026-ODD"}'
check "teacher double booking rejected" 409 POST /api/schedules "$TCLASH"
BADTIME='{"subjectId":5,"teacherId":5,"classLevel":"Grade 9","section":"A","room":"Room 1","dayOfWeek":3,"startTime":"11:00:00","endTime":"10:00:00","term":"2026-ODD"}'
check "end before start rejected" 400 POST /api/schedules "$BADTIME"
GOOD='{"subjectId":8,"teacherId":5,"classLevel":"Grade 9","section":"A","room":"Room 7","dayOfWeek":3,"startTime":"13:00:00","endTime":"13:45:00","term":"2026-ODD"}'
check "valid session created" 200 POST /api/schedules "$GOOD"
SESSION_ID=$(first_id)
check "session updated" 200 PUT "/api/schedules/$SESSION_ID" "$(echo "$GOOD" | sed 's/"room":"Room 7"/"room":"Room 8"/')"
check "session deleted" 200 DELETE "/api/schedules/$SESSION_ID"

CONTACT='{"category":"SMOKE","name":"Smoke Test Desk","department":"QA","phone":"+91 80 0000 0000","email":"smoke@academy.edu","location":"Basement","availableHours":"Never"}'
check "contact created" 200 POST /api/contacts "$CONTACT"
CONTACT_ID=$(first_id)
check "contact updated" 200 PUT "/api/contacts/$CONTACT_ID" "$(echo "$CONTACT" | sed 's/"name":"Smoke Test Desk"/"name":"Renamed Desk"/')"
check "contact deleted" 200 DELETE "/api/contacts/$CONTACT_ID"

echo
echo "== teacher =="
JAR=$(mktemp)
[ "$(login "$JAR" teacher teacher123)" = "302" ] || { echo "teacher login failed"; exit 1; }
check "own identity" 200 GET /api/auth/me
check "own profile" 200 GET /api/teachers/me
check "own schedule" 200 GET /api/teachers/me/schedule
check "student directory allowed" 200 GET /api/students
check "full timetable allowed" 200 GET /api/schedules
check "cannot admit student" 403 POST /api/students "$STUDENT"
check "cannot edit session" 403 PUT "/api/schedules/1" "$GOOD"
check "cannot add contact" 403 POST /api/contacts "$CONTACT"
check "cannot delete student" 403 DELETE "/api/students/$ENROLL_ID"

echo
echo "== student =="
JAR=$(mktemp)
[ "$(login "$JAR" student student123)" = "302" ] || { echo "student login failed"; exit 1; }
check "own profile" 200 GET /api/students/me
check "own schedule" 200 GET /api/students/me/schedule
check "own subjects" 200 GET /api/students/me/subjects
check "own classmates" 200 GET /api/students/me/classmates
check "own class timetable" 200 GET '/api/schedules/class?classLevel=Grade%2011&section=A'
check "teacher directory readable" 200 GET /api/teachers
check "contacts readable" 200 GET /api/contacts
check "cannot read other student" 403 GET /api/students/2
check "cannot browse roster" 403 GET /api/students
check "cannot read other schedule" 403 GET /api/students/2/schedule
check "cannot edit own record" 403 PUT /api/students/1 "$STUDENT"
check "cannot add teacher" 403 POST /api/teachers '{"employeeCode":"X","firstName":"a","lastName":"b","department":"c","email":"x@y.edu"}'

echo
echo "== access control without a session =="
rm -f "$JAR"
check "dashboard needs login" 401 GET /api/dashboard
check "students need login" 401 GET /api/students
check "portal page redirects" 302 GET /index.html
check "sign-in page is public" 200 GET /login.html

echo
echo "cleanup"
JAR=$(mktemp); login "$JAR" admin admin123 > /dev/null
check "remove leftover student 2" 200 DELETE "/api/students/$ENROLL_ID"
rm -f "$JAR"

echo
echo "passed=$PASS failed=$FAIL"
[ "$FAIL" -eq 0 ]
