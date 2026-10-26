package com.academy.portal.service;

import com.academy.portal.domain.AppUser;
import com.academy.portal.domain.Contact;
import com.academy.portal.domain.Role;
import com.academy.portal.domain.Student;
import com.academy.portal.domain.Teacher;
import com.academy.portal.dto.ContactDto;
import com.academy.portal.dto.PersonContactDto;
import com.academy.portal.dto.ScheduleRow;
import com.academy.portal.repository.AppUserRepository;
import com.academy.portal.repository.ClassScheduleRepository;
import com.academy.portal.repository.ContactRepository;
import com.academy.portal.repository.StudentRepository;
import com.academy.portal.repository.TeacherRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContactService {

    private final ContactRepository contactRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final ClassScheduleRepository scheduleRepository;
    private final AppUserRepository userRepository;
    private final CurrentUserService currentUserService;

    public ContactService(ContactRepository contactRepository,
                          StudentRepository studentRepository,
                          TeacherRepository teacherRepository,
                          ClassScheduleRepository scheduleRepository,
                          AppUserRepository userRepository,
                          CurrentUserService currentUserService) {
        this.contactRepository = contactRepository;
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.scheduleRepository = scheduleRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
    }

    /**
     * Who the signed-in user may reach, worked out from their own classes so the list cannot be
     * widened by the client. A student gets the faculty responsible for their subjects plus the
     * administration mailbox; a teacher gets the students in the divisions they teach.
     */
    @Transactional(readOnly = true)
    public List<PersonContactDto> people() {
        AppUser user = currentUserService.requireCurrentUser();
        return switch (user.getRole()) {
            case ADMIN -> everyone();
            case TEACHER -> studentsOfMyClasses(user);
            case STUDENT -> teachersOfMyClasses(user);
        };
    }

    private List<PersonContactDto> everyone() {
        List<PersonContactDto> people = new ArrayList<>(faculty(teacherRepository.findAll().stream().toList(), null));
        studentRepository.findAllByOrderByClassLevelAscSectionAscRollNumberAsc().forEach(student ->
                people.add(toPerson(student)));
        return people;
    }

    private List<PersonContactDto> studentsOfMyClasses(AppUser user) {
        Teacher teacher = teacherRepository.findById(teacherIdOrThrow(user))
                .orElseThrow(() -> new NotFoundException("No teacher found with id " + teacherIdOrThrow(user)));
        Set<String> divisions = new LinkedHashSet<>();
        for (ScheduleRow row : scheduleRepository.rowsForTeacher(teacher.getId())) {
            divisions.add(row.classLevel() + "|" + row.section());
        }
        List<PersonContactDto> people = new ArrayList<>();
        divisions.stream().sorted().forEach(divison -> {
            int split = divison.indexOf('|');
            studentRepository.findByClassLevelAndSectionOrderByRollNumberAsc(
                            divison.substring(0, split), divison.substring(split + 1))
                    .forEach(student -> people.add(toPerson(student)));
        });
        if (people.isEmpty()) {
            throw new BadRequestException(fullName(teacher.getFirstName(), teacher.getLastName())
                    + " has no classes on the timetable yet");
        }
        return people;
    }

    private List<PersonContactDto> teachersOfMyClasses(AppUser user) {
        Student student = studentRepository.findById(studentIdOrThrow(user))
                .orElseThrow(() -> new NotFoundException("No student found with id " + studentIdOrThrow(user)));
        Map<Long, Set<String>> subjectsByTeacher = new LinkedHashMap<>();
        for (ScheduleRow row : scheduleRepository.rowsForClass(student.getClassLevel(), student.getSection())) {
            subjectsByTeacher.computeIfAbsent(row.teacherId(), key -> new LinkedHashSet<>()).add(row.subjectName());
        }
        List<PersonContactDto> people = new ArrayList<>(
                faculty(teacherRepository.findAllById(subjectsByTeacher.keySet()).stream().toList(), subjectsByTeacher));
        userRepository.findByRole(Role.ADMIN).stream()
                .filter(AppUser::isEnabled)
                .filter(admin -> admin.getEmail() != null && !admin.getEmail().isBlank())
                .sorted(Comparator.comparing(AppUser::getFullName))
                .forEach(admin -> people.add(new PersonContactDto(admin.getId(), PersonContactDto.ADMINISTRATION,
                        admin.getFullName(), admin.getUsername(), null, null, admin.getEmail(),
                        "Academy administration")));
        return people;
    }

    private List<PersonContactDto> faculty(List<Teacher> teachers, Map<Long, Set<String>> subjectsByTeacher) {
        return teachers.stream()
                .sorted(Comparator.comparing((Teacher t) -> t.getDepartment() == null ? "" : t.getDepartment())
                        .thenComparing(Teacher::getLastName))
                .map(teacher -> {
                    String detail = subjectsByTeacher == null
                            ? teacher.getDepartment()
                            : String.join(", ", subjectsByTeacher.getOrDefault(teacher.getId(), Set.of()));
                    return new PersonContactDto(teacher.getId(), PersonContactDto.FACULTY,
                            fullName(teacher.getFirstName(), teacher.getLastName()),
                            teacher.getEmployeeCode(), teacher.getPhone(), null, teacher.getEmail(), detail);
                })
                .toList();
    }

    private PersonContactDto toPerson(Student student) {
        return new PersonContactDto(student.getId(), PersonContactDto.STUDENT,
                fullName(student.getFirstName(), student.getLastName()),
                student.getRollNumber(), student.getPhone(), student.getGuardianPhone(), student.getEmail(),
                student.getClassLevel() + " - Section " + student.getSection());
    }

    private static String fullName(String firstName, String lastName) {
        return (firstName + " " + lastName).trim();
    }

    private Long teacherIdOrThrow(AppUser user) {
        if (user.getTeacherId() == null) {
            throw new BadRequestException("This sign-in is not linked to a teacher profile.");
        }
        return user.getTeacherId();
    }

    private Long studentIdOrThrow(AppUser user) {
        if (user.getStudentId() == null) {
            throw new BadRequestException("This sign-in is not linked to a student profile.");
        }
        return user.getStudentId();
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
