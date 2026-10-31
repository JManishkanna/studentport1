package com.academy.portal.web;

import com.academy.portal.dto.CommentDto;
import com.academy.portal.dto.CommentRequest;
import com.academy.portal.dto.ProgressDto;
import com.academy.portal.service.CommentService;
import com.academy.portal.service.ProgressService;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Grades, progress and the notes left on a student's record. */
@RestController
@RequestMapping("/api")
public class ProgressController {

    private final ProgressService progressService;
    private final CommentService commentService;

    public ProgressController(ProgressService progressService, CommentService commentService) {
        this.progressService = progressService;
        this.commentService = commentService;
    }

    @GetMapping("/progress/me")
    @PreAuthorize("hasRole('STUDENT')")
    public ProgressDto mine() {
        return progressService.mine();
    }

    @GetMapping("/students/{id}/progress")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ProgressDto progress(@PathVariable Long id) {
        return progressService.forStudent(id);
    }

    @GetMapping("/students/me/comments")
    @PreAuthorize("hasRole('STUDENT')")
    public List<CommentDto> myComments() {
        return commentService.mine();
    }

    @GetMapping("/students/{id}/comments")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public List<CommentDto> comments(@PathVariable Long id) {
        return commentService.forStudent(id);
    }

    @PostMapping("/students/{id}/comments")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public CommentDto addComment(@PathVariable Long id, @RequestBody CommentRequest request) {
        return commentService.add(id, request);
    }

    @DeleteMapping("/comments/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public Map<String, String> deleteComment(@PathVariable Long id) {
        commentService.delete(id);
        return Map.of("status", "deleted", "id", String.valueOf(id));
    }
}
