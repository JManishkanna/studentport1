package com.academy.portal.service;

import com.academy.portal.domain.AppUser;
import com.academy.portal.domain.ProfilePhoto;
import com.academy.portal.domain.Role;
import com.academy.portal.repository.AppUserRepository;
import com.academy.portal.repository.ProfilePhotoRepository;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProfilePhotoService {

    private static final long MAX_BYTES = 2L * 1024 * 1024;

    private final ProfilePhotoRepository photoRepository;
    private final AppUserRepository userRepository;

    public ProfilePhotoService(ProfilePhotoRepository photoRepository, AppUserRepository userRepository) {
        this.photoRepository = photoRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Optional<ProfilePhoto> find(Long userId) {
        return photoRepository.findById(userId);
    }

    /** The version suffix makes a replaced picture bypass the browser cache. */
    @Transactional(readOnly = true)
    public Optional<String> urlFor(Long userId) {
        return photoRepository.findById(userId)
                .map(photo -> "api/profile/photo/" + userId + "?v=" + photo.getUpdatedAt().toEpochMilli());
    }

    /** The picture shown beside a student or teacher record, wherever that person is listed. */
    @Transactional(readOnly = true)
    public Optional<String> urlForPerson(Role role, Long linkedId) {
        return accountId(role, linkedId).flatMap(this::urlFor);
    }

    @Transactional
    public void storeFor(Role role, Long linkedId, MultipartFile file) {
        String label = role == Role.STUDENT ? "student" : "teacher";
        Long userId = accountId(role, linkedId)
                .orElseThrow(() -> new NotFoundException("No sign-in exists for this " + label + " yet"));
        store(userId, file);
    }

    @Transactional
    public void removeFor(Role role, Long linkedId) {
        accountId(role, linkedId).ifPresent(this::remove);
    }

    @Transactional
    public void store(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Choose an image to upload");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException("Profile pictures are limited to 2 MB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException failure) {
            throw new BadRequestException("The upload could not be read: " + failure.getMessage());
        }
        String contentType = sniff(bytes);
        if (contentType == null) {
            throw new BadRequestException("Profile pictures must be a PNG, JPEG or WebP image");
        }

        ProfilePhoto photo = photoRepository.findById(userId).orElseGet(() -> {
            ProfilePhoto fresh = new ProfilePhoto();
            fresh.setUserId(userId);
            return fresh;
        });
        photo.setContentType(contentType);
        photo.setBytes(bytes);
        // Pin to whole milliseconds: TIMESTAMP(3) rounds a finer value, so the URL handed back by
        // the upload would carry a different version than the row other reads serve.
        photo.setUpdatedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        photoRepository.save(photo);
    }

    @Transactional
    public void remove(Long userId) {
        photoRepository.findById(userId).ifPresent(photoRepository::delete);
    }

    private Optional<Long> accountId(Role role, Long linkedId) {
        Optional<AppUser> user = role == Role.STUDENT
                ? userRepository.findFirstByStudentId(linkedId)
                : userRepository.findFirstByTeacherId(linkedId);
        return user.map(AppUser::getId);
    }

    /** Trusts the bytes, not the browser-supplied part headers, so an HTML payload is refused. */
    private static String sniff(byte[] data) {
        if (data.length > 3 && (data[0] & 0xff) == 0xff && (data[1] & 0xff) == 0xd8 && (data[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        if (data.length > 8 && (data[0] & 0xff) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return "image/png";
        }
        if (data.length > 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
