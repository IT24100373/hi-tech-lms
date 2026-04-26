package com.hitech.lms.finance.service;
import com.hitech.lms.auth.service.*;
import com.hitech.lms.auth.repository.*;
import com.hitech.lms.auth.dto.*;
import com.hitech.lms.auth.model.*;
import com.hitech.lms.user.service.*;
import com.hitech.lms.user.dto.*;
import com.hitech.lms.course.service.*;
import com.hitech.lms.course.repository.*;
import com.hitech.lms.course.dto.*;
import com.hitech.lms.course.model.*;
import com.hitech.lms.exam.service.*;
import com.hitech.lms.exam.repository.*;
import com.hitech.lms.exam.dto.*;
import com.hitech.lms.exam.model.*;
import com.hitech.lms.schedule.service.*;
import com.hitech.lms.schedule.repository.*;
import com.hitech.lms.schedule.dto.*;
import com.hitech.lms.schedule.model.*;
import com.hitech.lms.finance.service.*;
import com.hitech.lms.finance.repository.*;
import com.hitech.lms.finance.dto.*;
import com.hitech.lms.finance.model.*;
import com.hitech.lms.support.service.*;
import com.hitech.lms.support.repository.*;
import com.hitech.lms.support.dto.*;
import com.hitech.lms.support.model.*;


import com.hitech.lms.dto.ApiResponse;
import com.hitech.lms.auth.dto.*;
import com.hitech.lms.user.dto.*;
import com.hitech.lms.course.dto.*;
import com.hitech.lms.exam.dto.*;
import com.hitech.lms.schedule.dto.*;
import com.hitech.lms.finance.dto.*;
import com.hitech.lms.support.dto.*;
import com.hitech.lms.auth.model.*;
import com.hitech.lms.course.model.*;
import com.hitech.lms.exam.model.*;
import com.hitech.lms.schedule.model.*;
import com.hitech.lms.finance.model.*;
import com.hitech.lms.support.model.*;
import com.hitech.lms.auth.repository.*;
import com.hitech.lms.course.repository.*;
import com.hitech.lms.exam.repository.*;
import com.hitech.lms.schedule.repository.*;
import com.hitech.lms.finance.repository.*;
import com.hitech.lms.support.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * PaymentService — FR-3.1: Mock Payment Gateway (localhost demo).
 *
 * MOCK flow:
 *  1. Student calls /api/payments/initiate → PaymentIntent created, fee locked server-side
 *  2. Frontend shows mock card form (no real gateway widget needed)
 *  3. Student calls /api/payments/mock-pay with card number
 *  4. Card 4000000000000002 → FAILED; any other 16-digit → SUCCESS
 *  5. On SUCCESS → Invoice generated, Enrollment activated, Email sent
 *
 * To swap in real Stripe:
 *  - Replace initiatePayment() to call Stripe.PaymentIntents.create()
 *  - Replace processMockPayment() with a /webhook endpoint verifying HMAC
 */
@Service
@Transactional
public class PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);
    private static final String MOCK_DECLINE_CARD = "4000000000000002";

    @Autowired private PaymentIntentRepository intentRepository;
    @Autowired private PaymentRepository       paymentRepository;
    @Autowired private EnrollmentRepository    enrollmentRepository;
    @Autowired private CourseService           courseService;
    @Autowired private InvoiceService          invoiceService;
    @Autowired private EmailService            emailService;

    // ---- INITIATE CHECKOUT ----
    public PaymentIntentResponse initiatePayment(Long courseId, User student) {
        Course course = courseService.findCourseById(courseId);

        if (course.getStatus() != Course.CourseStatus.ACTIVE)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This course is not available for enrollment.");

        if (course.getCourseFee().compareTo(BigDecimal.ZERO) == 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This course is free. Contact the administrator to enroll.");

        if (enrollmentRepository.existsByStudentIdAndCourseIdAndStatus(
                student.getId(), courseId, Enrollment.EnrollmentStatus.ACTIVE))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You are already enrolled in this course.");

        if (course.getMaxStudents() != null) {
            long enrolled = enrollmentRepository.countByCourseIdAndStatus(courseId, Enrollment.EnrollmentStatus.ACTIVE);
            if (enrolled >= course.getMaxStudents())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "This course is full. Maximum capacity (" + course.getMaxStudents() + ") reached.");
        }

        String idempotencyKey = "pi_" + student.getId() + "_" + courseId + "_" + System.currentTimeMillis();
        PaymentIntent intent = PaymentIntent.builder()
                .student(student).course(course)
                .amount(course.getCourseFee()).currency("LKR")
                .status(PaymentIntent.IntentStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build();

        intent = intentRepository.save(intent);
        logger.info("PaymentIntent {} created for {} course {}", intent.getId(), student.getEmail(), course.getCourseCode());
        return mapToIntentResponse(intent);
    }

    // ---- MOCK CARD SUBMISSION ----
    public PaymentResponse processMockPayment(MockPayRequest request, User student) {
        PaymentIntent intent = intentRepository.findById(request.getIntentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment session not found."));

        if (!intent.getStudent().getId().equals(student.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied.");

        if (intent.getStatus() != PaymentIntent.IntentStatus.PENDING)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This payment session is no longer valid. Please start a new checkout.");

        if (intent.isExpired()) {
            intent.setStatus(PaymentIntent.IntentStatus.EXPIRED);
            intentRepository.save(intent);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Payment session expired. Please start a new checkout.");
        }

        String card = request.getCardNumber();
        String brand = detectCardBrand(card);
        String last4 = card.substring(card.length() - 4);
        boolean declined = MOCK_DECLINE_CARD.equals(card);
        String ref = "MOCK_" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();

        if (declined) {
            intent.setStatus(PaymentIntent.IntentStatus.FAILED);
            intentRepository.save(intent);
            Payment failed = paymentRepository.save(Payment.builder()
                    .paymentIntent(intent).student(student).course(intent.getCourse())
                    .amount(intent.getAmount()).currency(intent.getCurrency())
                    .status(Payment.PaymentStatus.FAILED).paymentMethod("MOCK_CARD")
                    .cardLastFour(last4).cardBrand(brand).gatewayReference(ref)
                    .failureReason("Card declined. Please check your card details and try again.")
                    .build());
            logger.info("Mock payment DECLINED intent={}", intent.getId());
            return mapToPaymentResponse(failed, null);
        }

        // SUCCESS
        intent.setStatus(PaymentIntent.IntentStatus.CONFIRMED);
        intentRepository.save(intent);
        Payment payment = paymentRepository.save(Payment.builder()
                .paymentIntent(intent).student(student).course(intent.getCourse())
                .amount(intent.getAmount()).currency(intent.getCurrency())
                .status(Payment.PaymentStatus.SUCCESS).paymentMethod("MOCK_CARD")
                .cardLastFour(last4).cardBrand(brand).gatewayReference(ref)
                .build());
        logger.info("Mock payment SUCCESS intent={} ref={}", intent.getId(), ref);

        Invoice invoice = invoiceService.generateInvoice(payment);
        activateEnrollment(payment);
        sendConfirmationEmail(payment, invoice);
        return mapToPaymentResponse(payment, invoice);
    }

    // ---- STUDENT: MY PAYMENTS ----
    @Transactional(readOnly = true)
    public Page<PaymentResponse> getMyPayments(User student, int page, int size) {
        Pageable pg = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "paidAt"));
        return paymentRepository.findByStudentIdOrderByPaidAtDesc(student.getId(), pg)
                .map(p -> mapToPaymentResponse(p, invoiceService.findByPaymentId(p.getId()).orElse(null)));
    }

    // ---- ADMIN: TRANSACTION LEDGER ----
    @Transactional(readOnly = true)
    public Page<PaymentResponse> getTransactions(
            String search, String status, Long courseId,
            String fromDate, String toDate, int page, int size) {

        Payment.PaymentStatus st = (status != null && !status.isBlank())
                ? Payment.PaymentStatus.valueOf(status) : null;
        LocalDateTime from = (fromDate != null && !fromDate.isBlank())
                ? LocalDateTime.parse(fromDate + "T00:00:00") : null;
        LocalDateTime to = (toDate != null && !toDate.isBlank())
                ? LocalDateTime.parse(toDate + "T23:59:59") : null;

        Pageable pg = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "paidAt"));
        return paymentRepository.searchTransactions(search, st, courseId, from, to, pg)
                .map(p -> mapToPaymentResponse(p, invoiceService.findByPaymentId(p.getId()).orElse(null)));
    }

    // ---- ADMIN: DASHBOARD STATS ----
    @Transactional(readOnly = true)
    public FinanceDashboardResponse getDashboardStats() {
        List<FinanceDashboardResponse.MonthlyRevenue> monthly =
                paymentRepository.getMonthlyRevenueLast12Months().stream().map(row -> {
                    int yr = ((Number) row[0]).intValue();
                    int mo = ((Number) row[1]).intValue();
                    String[] months = {"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};
                    return FinanceDashboardResponse.MonthlyRevenue.builder()
                            .year(yr).month(mo).monthLabel(months[mo-1]+" "+yr)
                            .amount(new BigDecimal(row[2].toString())).build();
                }).collect(Collectors.toList());

        List<FinanceDashboardResponse.CourseRevenue> topCourses =
                paymentRepository.getTopCoursesByRevenue().stream()
                        .map(row -> FinanceDashboardResponse.CourseRevenue.builder()
                                .courseTitle((String) row[0])
                                .revenue(new BigDecimal(row[1].toString())).build())
                        .collect(Collectors.toList());

        return FinanceDashboardResponse.builder()
                .totalRevenueAllTime(paymentRepository.sumTotalRevenue())
                .totalRevenueThisMonth(paymentRepository.sumRevenueCurrentMonth())
                .totalSuccessfulTransactions(paymentRepository.countByStatus(Payment.PaymentStatus.SUCCESS))
                .totalFailedTransactions(paymentRepository.countByStatus(Payment.PaymentStatus.FAILED))
                .monthlyRevenue(monthly).topCoursesByRevenue(topCourses).build();
    }

    // ---- GET SINGLE PAYMENT ----
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Long paymentId, User viewer) {
        Payment p = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found."));
        if (viewer.getRole() == User.Role.STUDENT && !p.getStudent().getId().equals(viewer.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied.");
        return mapToPaymentResponse(p, invoiceService.findByPaymentId(paymentId).orElse(null));
    }

    // ---- PRIVATE HELPERS ----

    private void activateEnrollment(Payment payment) {
        // BUGFIX: The enrollments table has UNIQUE(student_id, course_id).
        // When a student is removed from a course, the row stays with status=REMOVED.
        // We must UPDATE that row instead of trying to INSERT a duplicate.
        Optional<Enrollment> existing = enrollmentRepository.findByStudentIdAndCourseId(
                payment.getStudent().getId(), payment.getCourse().getId());

        if (existing.isPresent()) {
            Enrollment enrollment = existing.get();
            if (enrollment.getStatus() == Enrollment.EnrollmentStatus.ACTIVE) {
                logger.warn("Enrollment already ACTIVE for student {} course {} — skipping",
                        payment.getStudent().getEmail(), payment.getCourse().getCourseCode());
                return;
            }
            // Re-activate the REMOVED / COMPLETED enrollment
            Enrollment.EnrollmentStatus prev = enrollment.getStatus();
            enrollment.setStatus(Enrollment.EnrollmentStatus.ACTIVE);
            enrollment.setPaymentReference(payment.getGatewayReference());
            enrollment.setEnrolledBy(null);
            enrollmentRepository.save(enrollment);
            logger.info("Enrollment RE-ACTIVATED (was {}) via payment for {} in {}",
                    prev, payment.getStudent().getEmail(), payment.getCourse().getCourseCode());
        } else {
            enrollmentRepository.save(Enrollment.builder()
                    .student(payment.getStudent()).course(payment.getCourse())
                    .enrolledBy(null)
                    .paymentReference(payment.getGatewayReference())
                    .status(Enrollment.EnrollmentStatus.ACTIVE).build());
            logger.info("Enrollment CREATED via payment for {} in {}",
                    payment.getStudent().getEmail(), payment.getCourse().getCourseCode());
        }
    }

    private void sendConfirmationEmail(Payment payment, Invoice invoice) {
        try {
            emailService.sendPaymentConfirmationEmail(
                    payment.getStudent().getEmail(),
                    payment.getStudent().getFullName(),
                    payment.getCourse().getTitle(),
                    payment.getAmount(), payment.getCurrency(),
                    invoice.getInvoiceNumber(), payment.getGatewayReference());
        } catch (Exception e) {
            logger.error("Payment confirmation email failed: {}", e.getMessage());
        }
    }

    private String detectCardBrand(String n) {
        if (n.startsWith("4"))  return "VISA";
        if (n.startsWith("5"))  return "MASTERCARD";
        if (n.startsWith("34") || n.startsWith("37")) return "AMEX";
        return "CARD";
    }

    private PaymentIntentResponse mapToIntentResponse(PaymentIntent i) {
        return PaymentIntentResponse.builder()
                .intentId(i.getId()).courseId(i.getCourse().getId())
                .courseTitle(i.getCourse().getTitle()).courseCode(i.getCourse().getCourseCode())
                .amount(i.getAmount()).currency(i.getCurrency())
                .status(i.getStatus().name()).expiresAt(i.getExpiresAt()).build();
    }

    private PaymentResponse mapToPaymentResponse(Payment p, Invoice inv) {
        return PaymentResponse.builder()
                .id(p.getId()).intentId(p.getPaymentIntent().getId())
                .courseId(p.getCourse().getId()).courseTitle(p.getCourse().getTitle())
                .courseCode(p.getCourse().getCourseCode())
                .studentId(p.getStudent().getId()).studentName(p.getStudent().getFullName())
                .studentEmail(p.getStudent().getEmail())
                .amount(p.getAmount()).currency(p.getCurrency())
                .status(p.getStatus().name()).paymentMethod(p.getPaymentMethod())
                .cardLastFour(p.getCardLastFour()).cardBrand(p.getCardBrand())
                .gatewayReference(p.getGatewayReference()).failureReason(p.getFailureReason())
                .paidAt(p.getPaidAt())
                .invoiceId(inv != null ? inv.getId() : null)
                .invoiceNumber(inv != null ? inv.getInvoiceNumber() : null).build();
    }
}