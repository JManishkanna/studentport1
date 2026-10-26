package com.academy.portal.web;

import com.academy.portal.dto.ContactDto;
import com.academy.portal.service.ContactService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contacts")
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @GetMapping
    public List<ContactDto> list(@RequestParam(required = false) String category) {
        return contactService.list(category);
    }

    @GetMapping("/categories")
    public List<String> categories() {
        return contactService.categories();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ContactDto create(@Valid @RequestBody ContactDto dto) {
        return contactService.create(dto);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ContactDto update(@PathVariable Long id, @Valid @RequestBody ContactDto dto) {
        return contactService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> delete(@PathVariable Long id) {
        contactService.delete(id);
        return Map.of("status", "deleted", "id", String.valueOf(id));
    }
}
