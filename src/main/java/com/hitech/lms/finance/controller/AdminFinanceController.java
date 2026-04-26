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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * AdminFinanceController — admin-only finance management.
 * Base path: /api/admin/finance
 *
 * FR-3.1 / FR-3.2: Admin monitors all transactions and invoices.
 */
@RestController
@RequestMapping("/api/admin/finance")
@PreAuthorize("hasRole('ADMIN')")
public class AdminFinanceController {

    @Autowired private PaymentService paymentService;
    @Autowired private InvoiceService invoiceService;

    /**
     * GET /api/admin/finance/dashboard
     * KPI stats, monthly revenue chart, top courses.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<FinanceDashboardResponse>> getDashboard() {
        return ResponseEntity.ok(
            ApiResponse.success("Finance dashboard loaded.", paymentService.getDashboardStats()));
    }

    /**
     * GET /api/admin/finance/transactions
     * Paginated, searchable transaction ledger.
     */
    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<Page<PaymentResponse>>> getTransactions(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(ApiResponse.success("Transactions retrieved.",
            paymentService.getTransactions(search, status, courseId, from, to, page, size)));
    }

    /**
     * GET /api/admin/finance/transactions/{paymentId}
     * Single transaction detail.
     */
    @GetMapping("/transactions/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getTransaction(
            @PathVariable Long paymentId,
            @AuthenticationPrincipal User admin) {

        return ResponseEntity.ok(
            ApiResponse.success("Transaction retrieved.", paymentService.getPaymentById(paymentId, admin)));
    }

    /**
     * GET /api/admin/finance/invoices/{invoiceId}
     * Admin retrieves any invoice (HTML for preview).
     */
    @GetMapping("/invoices/{invoiceId}")
    public ResponseEntity<ApiResponse<InvoiceResponse>> getInvoice(
            @PathVariable Long invoiceId,
            @AuthenticationPrincipal User admin) {

        return ResponseEntity.ok(
            ApiResponse.success("Invoice retrieved.", invoiceService.getInvoiceById(invoiceId, admin)));
    }
}
