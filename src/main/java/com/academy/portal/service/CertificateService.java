package com.academy.portal.service;

import com.academy.portal.domain.Enrollment;
import com.academy.portal.domain.Student;
import com.academy.portal.domain.Subject;
import com.academy.portal.domain.Teacher;
import com.academy.portal.dto.CertificatePdf;
import com.academy.portal.dto.CertificateRequest;
import com.academy.portal.dto.MeDto;
import com.academy.portal.repository.EnrollmentRepository;
import com.academy.portal.repository.StudentRepository;
import com.academy.portal.repository.SubjectRepository;
import com.academy.portal.repository.TeacherRepository;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Draws the graduation certificate as a real PDF: one landscape A4 page per graduate. */
@Service
public class CertificateService {

    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH);
    private static final DateTimeFormatter COMPACT_DATE = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ENGLISH);
    private static final Color INK = new Color(0x1f2a44);
    private static final Color MUTED = new Color(0x5a6478);

    private static final Font ACADEMY = new Font(Font.TIMES_ROMAN, 12, Font.BOLD, MUTED);
    private static final Font TITLE = new Font(Font.TIMES_ROMAN, 30, Font.BOLD, INK);
    private static final Font LEAD_IN = new Font(Font.TIMES_ROMAN, 13, Font.ITALIC, MUTED);
    private static final Font NAME = new Font(Font.TIMES_ROMAN, 34, Font.BOLD, INK);
    private static final Font LABEL = new Font(Font.TIMES_ROMAN, 12, Font.NORMAL, MUTED);
    private static final Font BODY = new Font(Font.TIMES_ROMAN, 14, Font.NORMAL, Color.BLACK);
    private static final Font STRONG = new Font(Font.TIMES_ROMAN, 17, Font.BOLD, INK);
    private static final Font SMALL = new Font(Font.TIMES_ROMAN, 11, Font.NORMAL, MUTED);

    private final StudentRepository studentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final SubjectRepository subjectRepository;
    private final TeacherRepository teacherRepository;
    private final CurrentUserService currentUserService;
    private final String academyName;

    public CertificateService(StudentRepository studentRepository,
                              EnrollmentRepository enrollmentRepository,
                              SubjectRepository subjectRepository,
                              TeacherRepository teacherRepository,
                              CurrentUserService currentUserService,
                              @Value("${portal.certificate.academy-name:Academy Portal}") String academyName) {
        this.studentRepository = studentRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.subjectRepository = subjectRepository;
        this.teacherRepository = teacherRepository;
        this.currentUserService = currentUserService;
        this.academyName = academyName;
    }

    @Transactional(readOnly = true)
    public CertificatePdf issue(CertificateRequest request) {
        List<Long> ids = request.studentIds() == null ? List.of() : request.studentIds();
        if (ids.isEmpty()) {
            throw new BadRequestException("Select at least one student to certify");
        }
        if (ids.size() > 200) {
            throw new BadRequestException("A single run certifies up to 200 students - narrow the selection");
        }
        List<Student> students = studentRepository.findAllById(ids).stream()
                .sorted(Comparator.comparing(Student::getClassLevel)
                        .thenComparing(Student::getSection)
                        .thenComparing(Student::getRollNumber))
                .toList();
        if (students.size() != ids.stream().distinct().count()) {
            List<Long> missing = new ArrayList<>(ids);
            students.forEach(student -> missing.remove(student.getId()));
            throw new NotFoundException("No student found with id " + missing.get(0));
        }

        MeDto issuer = currentUserService.me();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4.rotate(), 68, 68, 54, 58);
        document.addTitle("Graduation certificate");
        document.addCreator(academyName);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, buffer);
            document.open();
            for (int index = 0; index < students.size(); index += 1) {
                if (index > 0) {
                    document.newPage();
                }
                frame(writer, document);
                page(document, students.get(index), request, issuer.fullName());
            }
            document.close();
        } catch (DocumentException failure) {
            throw new IllegalStateException("Could not render the certificates: " + failure.getMessage(), failure);
        }

        String filename = students.size() == 1
                ? "graduation-certificate-" + slug(students.get(0).getRollNumber()) + ".pdf"
                : "graduation-certificates-" + students.size() + "-students.pdf";
        return new CertificatePdf(filename, students.size(), buffer.toByteArray());
    }

    private void page(Document document, Student student, CertificateRequest request, String issuer)
            throws DocumentException {
        String serial = "CERT-" + student.getRollNumber() + "-" + request.conferredOn().format(COMPACT_DATE);
        String term = request.term() == null || request.term().isBlank()
                ? "" : "  ·  Term " + request.term().trim();

        add(document, spaced(academyName.toUpperCase(Locale.ENGLISH), ACADEMY), 0f, 6f);
        add(document, spaced("CERTIFICATE OF GRADUATION", TITLE), 6f, 2f);
        add(document, spaced("This is to certify that", LEAD_IN), 12f, 8f);
        add(document, spaced(studentFullName(student), NAME), 0f, 4f);
        add(document, spaced("Roll number " + student.getRollNumber()
                + "  ·  " + student.getClassLevel(), LABEL), 0f, 18f);

        Paragraph statement = new Paragraph(
                "has successfully completed the programme of study in " + student.getClassLevel()
                        + " and is hereby awarded the certificate of graduation, together with all the"
                        + " rights and privileges pertaining thereto.", BODY);
        statement.setAlignment(Element.ALIGN_CENTER);
        add(document, statement, 0f, 16f);

        add(document, spaced(coursesOf(student), STRONG), 0f, 3f);
        add(document, spaced("Conferred on "
                + request.conferredOn().format(LONG_DATE) + term, LABEL), 0f, 30f);

        add(document, signatures(student, issuer), 0f, 8f);
        add(document, spaced("Serial " + serial + "  ·  Issued by " + issuer
                + " through the " + academyName + " on " + LocalDate.now().format(LONG_DATE), SMALL), 6f, 0f);
    }

    private PdfPCell rule() {
        PdfPCell cell = new PdfPCell(new Phrase(" "));
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderWidth(0.9f);
        cell.setBorderColor(INK);
        cell.setPaddingBottom(12f);
        cell.setPaddingTop(4f);
        return cell;
    }

    private PdfPCell label(String top, String bottom) {
        PdfPCell cell = new PdfPCell(new Phrase(" "));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingTop(6f);
        cell.addElement(new Paragraph(top, new Font(Font.TIMES_ROMAN, 13, Font.BOLD, INK)));
        cell.addElement(new Paragraph(bottom, SMALL));
        return cell;
    }

    private PdfPTable signatures(Student student, String issuer) {
        Teacher lead = teacherRepository.search(null, student.getClassLevel()).stream()
                .filter(candidate -> "Head of Department".equalsIgnoreCase(candidate.getDesignation()))
                .findFirst()
                .orElseGet(() -> teacherRepository.search(null, student.getClassLevel()).stream()
                        .min(Comparator.comparing(Teacher::getLastName))
                        .orElse(null));

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(82);
        table.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(rule());
        table.addCell(rule());
        table.addCell(label(lead == null ? "Programme office" : lead.getFirstName() + " " + lead.getLastName(),
                "Programme Lead, " + student.getClassLevel()));
        table.addCell(label(issuer, "Head of Academy"));
        return table;
    }

    private String coursesOf(Student student) {
        List<Long> subjectIds = enrollmentRepository.findByStudentIdOrderByIdAsc(student.getId()).stream()
                .map(Enrollment::getSubjectId)
                .toList();
        if (subjectIds.isEmpty()) {
            return "Programme requirements as recorded in the academy register";
        }
        Map<Long, Subject> byId = subjectRepository.findAllById(subjectIds).stream()
                .collect(Collectors.toMap(Subject::getId, Function.identity()));
        return subjectIds.stream().map(byId::get).filter(Objects::nonNull)
                .sorted(Comparator.comparing(Subject::getCode))
                .map(Subject::getName)
                .collect(Collectors.joining("  ·  "));
    }

    /** A double frame around the page, drawn under the text of the page the writer is on. */
    private void frame(PdfWriter writer, Document document) {
        var canvas = writer.getDirectContent();
        Rectangle page = document.getPageSize();
        canvas.saveState();
        canvas.setLineWidth(2f);
        canvas.setRGBColorStroke(INK.getRed(), INK.getGreen(), INK.getBlue());
        canvas.rectangle(26, 26, page.getWidth() - 52, page.getHeight() - 52);
        canvas.stroke();
        canvas.setLineWidth(0.6f);
        canvas.rectangle(34, 34, page.getWidth() - 68, page.getHeight() - 68);
        canvas.stroke();
        canvas.restoreState();
    }

    private static Paragraph spaced(String text, Font font) {
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setAlignment(Element.ALIGN_CENTER);
        return paragraph;
    }

    private static String studentFullName(Student student) {
        return (student.getFirstName() + " " + student.getLastName()).trim();
    }

    private static void add(Document document, com.lowagie.text.Element element, float before, float after)
            throws DocumentException {
        if (element instanceof PdfPTable table) {
            table.setSpacingBefore(before);
            table.setSpacingAfter(after);
        } else if (element instanceof Paragraph paragraph) {
            paragraph.setSpacingBefore(before);
            paragraph.setSpacingAfter(after);
        }
        document.add(element);
    }

    private static String slug(String rollNumber) {
        return rollNumber.replaceAll("[^A-Za-z0-9._-]", "-");
    }
}
