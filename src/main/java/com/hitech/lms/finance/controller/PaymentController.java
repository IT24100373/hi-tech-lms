package com.hitech.lms.finance.controller;
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


import com.hitech.lms.auth.model.*;
import com.hitech.lms.course.model.*;
import com.hitech.lms.exam.model.*;
import com.hitech.lms.schedule.model.*;
import com.hitech.lms.finance.model.*;
import com.hitech.lms.support.model.*;


import com.hitech.lms.dto.ApiResponse;
import com.hitech.lms.auth.dto.*;
import com.hitech.lms.user.dto.*;
import com.hitech.lms.course.dto.*;
import com.hitech.lms.exam.dto.*;
import com.hitech.lms.schedule.dto.*;
import com.hitech.lms.finance.dto.*;
import com.hitech.lms.support.dto.*;
import com.hitech.lms.auth.model.User;
import com.hitech.lms.finance.service.InvoiceService;
import com.hitech.lms.finance.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * PaymentController — student-facing payment endpoints.
 * Base path: /api/payments
 *
 * FR-3.1: Handles checkout initiation and mock card submission.
 * FR-3.2: Invoice retrieval for students.
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    @Autowired private PaymentService paymentService;
    @Autowired private InvoiceService invoiceService;

    /**
     * POST /api/payments/initiate
     * Student initiates checkout for a course.
     * Returns PaymentIntent with locked fee amount.
     */
    @PostMapping("/initiate")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<PaymentIntentResponse>> initiatePayment(
            @Valid @RequestBody InitiatePaymentRequest request,
            @AuthenticationPrincipal User student) {

        PaymentIntentResponse intent = paymentService.initiatePayment(request.getCourseId(), student);
        return ResponseEntity.ok(
                ApiResponse.success("Checkout session created.", intent));
    }

    /**
     * POST /api/payments/mock-pay
     * Student submits mock card details.
     * Returns PaymentResponse (SUCCESS or FAILED).
     */
    @PostMapping("/mock-pay")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<PaymentResponse>> mockPay(
            @Valid @RequestBody MockPayRequest request,
            @AuthenticationPrincipal User student) {

        PaymentResponse result = paymentService.processMockPayment(request, student);
        if ("SUCCESS".equals(result.getStatus())) {
            return ResponseEntity.ok(
                    ApiResponse.success("Payment successful. Enrollment activated.", result));
        } else {
            return ResponseEntity.ok(
                    ApiResponse.success("Payment processed.", result));
        }
    }

    /**
     * GET /api/payments/my-payments?page=0&size=10
     * Student views their own payment history.
     */
    @GetMapping("/my-payments")
    @PreAuthorize("hasAnyRole('STUDENT')")
    public ResponseEntity<ApiResponse<Page<PaymentResponse>>> getMyPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal User student) {

        return ResponseEntity.ok(
                ApiResponse.success("Payments retrieved.", paymentService.getMyPayments(student, page, size)));
    }

    /**
     * GET /api/payments/{paymentId}
     * Get a single payment record (student: own only).
     */
    @GetMapping("/{paymentId}")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(
            @PathVariable Long paymentId,
            @AuthenticationPrincipal User viewer) {

        return ResponseEntity.ok(
                ApiResponse.success("Payment retrieved.", paymentService.getPaymentById(paymentId, viewer)));
    }

    /**
     * GET /api/payments/invoices/{invoiceId}
     * Student retrieves their invoice (HTML for browser preview).
     */
    @GetMapping("/invoices/{invoiceId}")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    public ResponseEntity<ApiResponse<InvoiceResponse>> getInvoice(
            @PathVariable Long invoiceId,
            @AuthenticationPrincipal User viewer) {

        return ResponseEntity.ok(
                ApiResponse.success("Invoice retrieved.", invoiceService.getInvoiceById(invoiceId, viewer)));
    }
}