package com.academy.portal.service;

import com.academy.portal.domain.Enrollment;
import com.academy.portal.domain.Student;
import com.academy.portal.domain.Subject;
import com.academy.portal.domain.Teacher;
import com.academy.portal.dto.CertificatePageDto;
import com.academy.portal.dto.CertificatePdf;
import com.academy.portal.dto.CertificatePreviewDto;
import com.academy.portal.dto.CertificateRequest;
import com.academy.portal.repository.EnrollmentRepository;
import com.academy.portal.repository.StudentRepository;
import com.academy.portal.repository.SubjectRepository;
import com.academy.portal.repository.TeacherRepository;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
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

    /** Navy and gold, pinned here and mirrored in the preview sheet, so print and screen agree. */
    private static final Color NAVY = new Color(0x0E2A47);
    private static final Color GOLD = new Color(0xB08D2E);
    private static final Color GOLD_BRIGHT = new Color(0xD9B45B);
    private static final Color INK = new Color(0x14263C);
    private static final Color MUTED = new Color(0x54637E);

    private static final String HEADING = "CERTIFICATE OF GRADUATION";
    private static final String CERTIFY_LINE = "This is to certify that";

    private static final Font ACADEMY = new Font(Font.TIMES_ROMAN, 11, Font.BOLD, GOLD);
    private static final Font TITLE = new Font(Font.TIMES_ROMAN, 30, Font.BOLD, NAVY);
    private static final Font LEAD_IN = new Font(Font.TIMES_ROMAN, 13, Font.ITALIC, MUTED);
    private static final Font NAME = new Font(Font.TIMES_ROMAN, 34, Font.BOLD, NAVY);
    private static final Font LABEL = new Font(Font.TIMES_ROMAN, 12, Font.NORMAL, MUTED);
    private static final Font BODY = new Font(Font.TIMES_ROMAN, 14, Font.NORMAL, INK);
    private static final Font STRONG = new Font(Font.TIMES_ROMAN, 16, Font.BOLD, NAVY);
    private static final Font DATE_LINE = new Font(Font.TIMES_ROMAN, 12, Font.BOLD, GOLD);
    private static final Font SMALL = new Font(Font.TIMES_ROMAN, 11, Font.NORMAL, MUTED);

    /** Diameter of the pressed seal that sits between the two signature blocks. */
    private static final float SEAL = 78f;

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
        List<Student> students = selected(request);
        List<CertificatePageDto> pages = pagesOf(students, request);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4.rotate(), 68, 68, 54, 58);
        document.addTitle("Graduation certificate");
        document.addCreator(academyName);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, buffer);
            document.open();
            for (int index = 0; index < pages.size(); index += 1) {
                if (index > 0) {
                    document.newPage();
                }
                frame(writer, document);
                render(document, writer, pages.get(index));
            }
            document.close();
        } catch (DocumentException | IOException failure) {
            throw new IllegalStateException("Could not render the certificates: " + failure.getMessage(), failure);
        }

        String filename = students.size() == 1
                ? "graduation-certificate-" + slug(students.get(0).getRollNumber()) + ".pdf"
                : "graduation-certificates-" + students.size() + "-students.pdf";
        return new CertificatePdf(filename, students.size(), buffer.toByteArray());
    }

    /** The same pages the PDF will carry, so the admin reads the real wording before downloading. */
    @Transactional(readOnly = true)
    public CertificatePreviewDto preview(CertificateRequest request) {
        return new CertificatePreviewDto(HEADING, CERTIFY_LINE, pagesOf(selected(request), request));
    }

    private List<Student> selected(CertificateRequest request) {
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
        return students;
    }

    private List<CertificatePageDto> pagesOf(List<Student> students, CertificateRequest request) {
        String issuer = currentUserService.me().fullName();
        return students.stream().map(student -> page(student, request, issuer)).toList();
    }

    private CertificatePageDto page(Student student, CertificateRequest request, String issuer) {
        String term = request.term() == null || request.term().isBlank()
                ? "" : "  ·  Term " + request.term().trim();
        return new CertificatePageDto(
                academyName.toUpperCase(Locale.ENGLISH),
                sealLines(academyName),
                studentFullName(student),
                "Roll number " + student.getRollNumber() + "  ·  " + student.getClassLevel(),
                "has successfully completed the programme of study in " + student.getClassLevel()
                        + " and is hereby awarded the certificate of graduation, together with all the"
                        + " rights and privileges pertaining thereto.",
                coursesOf(student),
                "Conferred on " + request.conferredOn().format(LONG_DATE) + term,
                List.of(new CertificatePageDto.Signature(leadOf(student), "Programme Lead, " + student.getClassLevel()),
                        new CertificatePageDto.Signature(issuer, "Head of Academy")),
                "Serial CERT-" + student.getRollNumber() + "-" + request.conferredOn().format(COMPACT_DATE)
                        + "  ·  Issued by " + issuer + " through the " + academyName + " on "
                        + LocalDate.now().format(LONG_DATE));
    }

    private void render(Document document, PdfWriter writer, CertificatePageDto view)
            throws DocumentException, IOException {
        add(document, spaced(view.academyName(), ACADEMY), 0f, 6f);
        add(document, spaced(HEADING, TITLE), 6f, 2f);
        add(document, centeredRule(GOLD, 1.1f, 26f), 0f, 9f);
        add(document, spaced(CERTIFY_LINE, LEAD_IN), 0f, 8f);
        add(document, spaced(view.studentName(), NAME), 0f, 4f);
        add(document, centeredRule(NAVY, 0.9f, 36f), 0f, 8f);
        add(document, spaced(view.rollLine(), LABEL), 0f, 12f);
        add(document, spaced(view.statement(), BODY), 0f, 12f);
        add(document, spaced(view.subjects(), STRONG), 0f, 3f);
        add(document, spaced(view.conferredLine(), DATE_LINE), 0f, 14f);
        add(document, signatures(view.sealLines(), view.signatures(), writer), 0f, 8f);
        add(document, spaced(view.serialLine(), SMALL), 2f, 0f);
    }

    /** A short hairline that sits under the heading and under the graduate's name. */
    private PdfPTable centeredRule(Color colour, float weight, float widthPercent) {
        PdfPCell cell = new PdfPCell(new Phrase(" ", new Font(Font.TIMES_ROMAN, 2, Font.NORMAL, colour)));
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderWidth(weight);
        cell.setBorderColor(colour);
        cell.setPaddingBottom(0.5f);
        cell.setPaddingTop(2f);
        PdfPTable rule = new PdfPTable(1);
        rule.setWidthPercentage(widthPercent);
        rule.setHorizontalAlignment(Element.ALIGN_CENTER);
        rule.addCell(cell);
        return rule;
    }

    private PdfPCell rule() {
        PdfPCell cell = new PdfPCell(new Phrase(" ", new Font(Font.TIMES_ROMAN, 4, Font.NORMAL, NAVY)));
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderWidth(1f);
        cell.setBorderColor(NAVY);
        cell.setPaddingBottom(12f);
        cell.setPaddingTop(4f);
        return cell;
    }

    private PdfPCell label(String top, String bottom) {
        PdfPCell cell = new PdfPCell(new Phrase(" "));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingTop(6f);
        cell.addElement(new Paragraph(top, new Font(Font.TIMES_ROMAN, 13, Font.BOLD, NAVY)));
        cell.addElement(new Paragraph(bottom, SMALL));
        return cell;
    }

    /** Two signature blocks with the academy seal pressed between them. */
    private PdfPTable signatures(List<String> sealLines, List<CertificatePageDto.Signature> signatories,
                                 PdfWriter writer) throws DocumentException, IOException {
        PdfPTable table = new PdfPTable(3);
        table.setWidths(new float[] {0.37f, 0.26f, 0.37f});
        table.setWidthPercentage(90);
        table.setHorizontalAlignment(Element.ALIGN_CENTER);

        PdfPCell seal = new PdfPCell(sealImage(writer, sealLines));
        seal.setBorder(Rectangle.NO_BORDER);
        seal.setPadding(0f);
        seal.setRowspan(2);
        seal.setHorizontalAlignment(Element.ALIGN_CENTER);
        seal.setVerticalAlignment(Element.ALIGN_MIDDLE);

        table.addCell(rule());
        table.addCell(seal);
        table.addCell(rule());
        signatories.forEach(signatory -> table.addCell(label(signatory.name(), signatory.role())));
        return table;
    }

    /** A gold scalloped disc with a navy face, a double ring and the academy name pressed in gold. */
    private Image sealImage(PdfWriter writer, List<String> lines) throws DocumentException, IOException {
        PdfContentByte canvas = writer.getDirectContent();
        PdfTemplate plate = canvas.createTemplate(SEAL, SEAL);
        float centre = SEAL / 2f;
        float rim = centre - 1.5f;

        stroke(plate, GOLD, 1f);
        plate.setRGBColorFill(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue());
        int teeth = 72;
        for (int index = 0; index < teeth; index += 1) {
            double angle = Math.PI * 2 * index / teeth;
            double radius = index % 2 == 0 ? rim : rim * 0.935;
            float x = (float) (centre + radius * Math.cos(angle));
            float y = (float) (centre + radius * Math.sin(angle));
            if (index == 0) {
                plate.moveTo(x, y);
            } else {
                plate.lineTo(x, y);
            }
        }
        plate.closePath();
        plate.fillStroke();

        plate.setRGBColorFill(NAVY.getRed(), NAVY.getGreen(), NAVY.getBlue());
        plate.circle(centre, centre, rim * 0.79f);
        plate.fill();
        stroke(plate, GOLD_BRIGHT, 1.2f);
        plate.circle(centre, centre, rim * 0.79f);
        plate.stroke();
        stroke(plate, GOLD, 0.6f);
        plate.circle(centre, centre, rim * 0.63f);
        plate.stroke();

        BaseFont face = BaseFont.createFont(BaseFont.TIMES_BOLD, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        float limit = rim;                       // the widest chord the pressed name may span
        float widest = 0f;
        for (String line : lines) {
            widest = Math.max(widest, face.getWidthPoint(line, 1f));
        }
        float size = widest * 7f > limit ? Math.max(4f, limit / widest) : 7f;
        plate.beginText();
        plate.setRGBColorFill(GOLD_BRIGHT.getRed(), GOLD_BRIGHT.getGreen(), GOLD_BRIGHT.getBlue());
        plate.setFontAndSize(face, size);
        for (int index = 0; index < lines.size(); index += 1) {
            String line = lines.get(index);
            float width = face.getWidthPoint(line, size);
            float baseline = lines.size() == 1 ? centre - size * 0.34f : centre + (index == 0 ? 2f : -size - 1f);
            plate.setTextMatrix(centre - width / 2f, baseline);
            plate.showText(line);
        }
        plate.endText();

        Image pressed = Image.getInstance(plate);
        pressed.scaleAbsolute(SEAL, SEAL);
        pressed.setAlignment(Element.ALIGN_CENTER);
        return pressed;
    }

    /** The seal carries the academy's own name, split across two lines at most. */
    static List<String> sealLines(String academyName) {
        String[] words = (academyName == null ? "" : academyName.trim()).toUpperCase(Locale.ENGLISH).split("\\s+");
        if (words.length == 0 || words[0].isBlank()) {
            return List.of("SEAL");
        }
        if (words.length == 1) {
            return List.of(words[0]);
        }
        int split = (words.length + 1) / 2;
        return List.of(String.join(" ", Arrays.copyOfRange(words, 0, split)),
                String.join(" ", Arrays.copyOfRange(words, split, words.length)));
    }

    private static void stroke(PdfContentByte canvas, Color colour, float width) {
        canvas.setLineWidth(width);
        canvas.setRGBColorStroke(colour.getRed(), colour.getGreen(), colour.getBlue());
    }

    /** The programme's head of department, or the first of its faculty by surname when there is none. */
    private String leadOf(Student student) {
        List<Teacher> faculty = teacherRepository.search(null, student.getClassLevel());
        return faculty.stream()
                .filter(candidate -> "Head of Department".equalsIgnoreCase(candidate.getDesignation()))
                .findFirst()
                .or(() -> faculty.stream().min(Comparator.comparing(Teacher::getLastName)))
                .map(teacher -> teacher.getFirstName() + " " + teacher.getLastName())
                .orElse("Programme office");
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

    /** Navy band, gold band, navy hairline, with a gold bead pressed at each corner. */
    private void frame(PdfWriter writer, Document document) {
        var canvas = writer.getDirectContent();
        Rectangle page = document.getPageSize();
        float width = page.getWidth();
        float height = page.getHeight();
        canvas.saveState();
        stroke(canvas, NAVY, 3f);
        canvas.rectangle(22, 22, width - 44, height - 44);
        canvas.stroke();
        stroke(canvas, GOLD, 1.1f);
        canvas.rectangle(30, 30, width - 60, height - 60);
        canvas.stroke();
        stroke(canvas, NAVY, 0.6f);
        canvas.rectangle(37, 37, width - 74, height - 74);
        canvas.stroke();
        canvas.setRGBColorFill(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue());
        for (float[] corner : List.of(new float[] {30, 30}, new float[] {width - 30, 30},
                new float[] {30, height - 30}, new float[] {width - 30, height - 30})) {
            bead(canvas, corner[0], corner[1], 5f);
        }
        canvas.restoreState();
    }

    private static void bead(PdfContentByte canvas, float x, float y, float radius) {
        canvas.moveTo(x, y + radius);
        canvas.lineTo(x + radius, y);
        canvas.lineTo(x, y - radius);
        canvas.lineTo(x - radius, y);
        canvas.closePath();
        canvas.fill();
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
