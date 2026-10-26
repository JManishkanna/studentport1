package com.academy.portal.service;

import com.academy.portal.domain.Contact;
import com.academy.portal.dto.ContactDto;
import com.academy.portal.repository.ContactRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContactService {

    private final ContactRepository contactRepository;

    public ContactService(ContactRepository contactRepository) {
        this.contactRepository = contactRepository;
    }

    @Transactional(readOnly = true)
    public List<ContactDto> list(String category) {
        List<Contact> contacts = category == null || category.isBlank()
                ? contactRepository.findAllByOrderByCategoryAscNameAsc()
                : contactRepository.findByCategory(category.trim().toUpperCase());
        return contacts.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<String> categories() {
        return contactRepository.findAllByOrderByCategoryAscNameAsc().stream()
                .map(Contact::getCategory)
                .distinct()
                .toList();
    }

    @Transactional
    public ContactDto create(ContactDto dto) {
        if (dto.id() != null) {
            throw new BadRequestException("id must be empty when adding a contact");
        }
        Contact contact = new Contact();
        apply(contact, dto);
        return toDto(contactRepository.save(contact));
    }

    @Transactional
    public ContactDto update(Long id, ContactDto dto) {
        Contact contact = load(id);
        apply(contact, dto);
        return toDto(contactRepository.save(contact));
    }

    @Transactional
    public void delete(Long id) {
        contactRepository.delete(load(id));
    }

    private Contact load(Long id) {
        return contactRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("No contact found with id " + id));
    }

    private void apply(Contact contact, ContactDto dto) {
        contact.setCategory(dto.category().trim().toUpperCase().replace(' ', '_'));
        contact.setName(dto.name().trim());
        contact.setDepartment(trimOrNull(dto.department()));
        contact.setPhone(dto.phone().trim());
        contact.setAlternatePhone(trimOrNull(dto.alternatePhone()));
        contact.setEmail(trimOrNull(dto.email()));
        contact.setLocation(trimOrNull(dto.location()));
        contact.setAvailableHours(trimOrNull(dto.availableHours()));
    }

    private String trimOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ContactDto toDto(Contact c) {
        return new ContactDto(c.getId(), c.getCategory(), c.getName(), c.getDepartment(), c.getPhone(),
                c.getAlternatePhone(), c.getEmail(), c.getLocation(), c.getAvailableHours());
    }
}
