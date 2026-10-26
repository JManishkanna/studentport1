package com.academy.portal.repository;

import com.academy.portal.domain.Contact;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactRepository extends JpaRepository<Contact, Long> {

    List<Contact> findAllByOrderByCategoryAscNameAsc();

    List<Contact> findByCategory(String category);

    Optional<Contact> findFirstByCategory(String category);
}
