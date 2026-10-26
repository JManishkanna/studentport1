package com.academy.portal.web;

import com.academy.portal.domain.AppUser;
import com.academy.portal.domain.ProfilePhoto;
import com.academy.portal.dto.MeDto;
import com.academy.portal.service.CurrentUserService;
import com.academy.portal.service.NotFoundException;
import com.academy.portal.service.ProfilePhotoService;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfilePhotoService photoService;
    private final CurrentUserService currentUserService;

    public ProfileController(ProfilePhotoService photoService, CurrentUserService currentUserService) {
        this.photoService = photoService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/photo")
    public MeDto upload(@RequestParam("file") MultipartFile file) {
        AppUser user = currentUserService.requireCurrentUser();
        photoService.store(user.getId(), file);
        return currentUserService.me();
    }

    @DeleteMapping("/photo")
    public MeDto remove() {
        photoService.remove(currentUserService.requireCurrentUser().getId());
        return currentUserService.me();
    }

    @GetMapping("/photo/{userId}")
    public ResponseEntity<byte[]> photo(@PathVariable Long userId) {
        ProfilePhoto photo = photoService.find(userId)
                .orElseThrow(() -> new NotFoundException("No profile picture has been uploaded for this account"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(photo.getBytes());
    }
}
