package com.academy.portal.service;

import com.academy.portal.domain.AppUser;
import com.academy.portal.domain.Role;
import com.academy.portal.domain.Student;
import com.academy.portal.domain.StudentComment;
import com.academy.portal.domain.Subject;
import com.academy.portal.dto.CommentDto;
import com.academy.portal.dto.CommentRequest;
import com.academy.portal.repository.StudentCommentRepository;
import com.academy.portal.repository.StudentRepository;
import com.academy.portal.repository.SubjectRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Notes on a student's record: written by faculty and administrators, read by the student too. */
@Service
public class CommentService {

    private static final int MAX_BODY = 1000;

    private final StudentCommentRepository commentRepository;
    private final StudentRepository studentRepository;
    private final SubjectRepository subjectRepository;
    private final CurrentUserService currentUserService;
    private final TeachingScope scope;

    public CommentService(StudentCommentRepository commentRepository,
                          StudentRepository studentRepository,
                          SubjectRepository subjectRepository,
                          CurrentUserService currentUserService,
                          TeachingScope scope) {
        this.commentRepository = commentRepository;
        this.studentRepository = studentRepository;
        this.subjectRepository = subjectRepository;
        this.currentUserService = currentUserService;
        this.scope = scope;
    }

    @Transactional(readOnly = true)
    public List<CommentDto> mine() {
        AppUser me = currentUserService.requireCurrentUser();
        Student student = scope.requireStudent(me);
        return toDtos(commentRepository.findByStudentIdOrderByCreatedAtDescIdDesc(student.getId()), me);
    }

    @Transactional(readOnly = true)
    public List<CommentDto> forStudent(Long studentId) {
        AppUser me = currentUserService.requireCurrentUser();
        Student student = loadStudent(studentId);
        assertCanRead(student, me);
        return toDtos(commentRepository.findByStudentIdOrderByCreatedAtDescIdDesc(student.getId()), me);
    }

    @Transactional
    public CommentDto add(Long studentId, CommentRequest request) {
        AppUser me = currentUserService.requireCurrentUser();
        Student student = loadStudent(studentId);
        assertCanWrite(student, me);
        String body = request == null || request.body() == null ? "" : request.body().trim();
        if (body.isEmpty()) {
            throw new BadRequestException("Write the note before saving it");
        }
        if (body.length() > MAX_BODY) {
            throw new BadRequestException("Keep the note to " + MAX_BODY + " characters or fewer");
        }
        Long subjectId = request.subjectId();
        if (subjectId != null) {
            Subject subject = subjectRepository.findById(subjectId)
                    .orElseThrow(() -> new NotFoundException("No subject found with id " + subjectId));
            if (!scope.onTimetable(subject.getId(), student.getClassLevel(), student.getSection())) {
                throw new BadRequestException(subject.getName() + " is not taught in "
                        + student.getClassLevel() + (student.getSection() == null ? "" : " section "
                        + student.getSection()) + ".");
            }
            if (me.getRole() == Role.TEACHER
                    && !scope.teaches(me, subject.getId(), student.getClassLevel(), student.getSection())) {
                throw new AccessDeniedException("You can only tag subjects you teach in that class");
            }
        }

        StudentComment comment = new StudentComment();
        comment.setStudentId(student.getId());
        comment.setSubjectId(subjectId);
        comment.setTeacherId(me.getRole() == Role.TEACHER ? me.getTeacherId() : null);
        comment.setAuthorId(me.getId());
        comment.setAuthorName(me.getFullName());
        comment.setAuthorRole(me.getRole().name());
        comment.setBody(body);
        return toDtos(List.of(commentRepository.save(comment)), me).get(0);
    }

    @Transactional
    public void delete(Long commentId) {
        AppUser me = currentUserService.requireCurrentUser();
        StudentComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("No note found with id " + commentId));
        boolean allowed = me.getRole() == Role.ADMIN
                || (me.getRole() == Role.TEACHER && me.getTeacherId() != null
                && me.getTeacherId().equals(comment.getTeacherId()));
        if (!allowed) {
            throw new AccessDeniedException("Only the author or an administrator can remove this note");
        }
        commentRepository.delete(comment);
    }

    private void assertCanRead(Student student, AppUser me) {
        boolean allowed = switch (me.getRole()) {
            case ADMIN -> true;
            case TEACHER -> scope.teachesClass(me, student.getClassLevel(), student.getSection());
            case STUDENT -> Objects.equals(me.getStudentId(), student.getId());
        };
        if (!allowed) {
            throw new AccessDeniedException("You can only read notes for students you teach");
        }
    }

    private void assertCanWrite(Student student, AppUser me) {
        if (me.getRole() == Role.STUDENT) {
            throw new AccessDeniedException("Only faculty and administrators write notes on a student record");
        }
        if (me.getRole() == Role.TEACHER && !scope.teachesClass(me, student.getClassLevel(), student.getSection())) {
            throw new AccessDeniedException("You can only write notes for students you teach");
        }
    }

    private List<CommentDto> toDtos(List<StudentComment> comments, AppUser me) {
        Map<Long, Subject> catalog = subjectRepository.findAllByOrderByCodeAsc().stream()
                .collect(Collectors.toMap(Subject::getId, Function.identity()));
        return comments.stream()
                .map(comment -> {
                    Subject subject = comment.getSubjectId() == null ? null : catalog.get(comment.getSubjectId());
                    boolean canDelete = me.getRole() == Role.ADMIN
                            || (me.getRole() == Role.TEACHER && me.getTeacherId() != null
                            && me.getTeacherId().equals(comment.getTeacherId()));
                    return new CommentDto(comment.getId(), comment.getStudentId(), comment.getSubjectId(),
                            subject == null ? null : subject.getCode(),
                            subject == null ? null : subject.getName(),
                            comment.getAuthorName(), comment.getAuthorRole(), comment.getBody(),
                            comment.getCreatedAt(), canDelete);
                })
                .toList();
    }

    private Student loadStudent(Long studentId) {
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new NotFoundException("No student found with id " + studentId));
    }
}
