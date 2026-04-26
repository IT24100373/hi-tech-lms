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


import com.hitech.lms.finance.dto.InvoiceResponse;
import com.hitech.lms.auth.model.*;
import com.hitech.lms.course.model.*;
import com.hitech.lms.exam.model.*;
import com.hitech.lms.schedule.model.*;
import com.hitech.lms.finance.model.*;
import com.hitech.lms.support.model.*;
import com.hitech.lms.finance.repository.InvoiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * InvoiceService — FR-3.2: Automatic Invoice Generation.
 *
 * Generates a professional HTML invoice for every SUCCESS payment.
 * Invoice number format: INV-YYYY-NNNNN (e.g. INV-2026-00001).
 * HTML is stored in the DB; the frontend renders it and uses window.print() for PDF.
 *
 * Production upgrade path:
 * - Replace HTML generation with iText / Thymeleaf → PDF → upload to S3
 * - Store S3 URL instead of HTML in invoice_html column
 */
@Service
@Transactional
public class InvoiceService {

    private static final Logger logger = LoggerFactory.getLogger(InvoiceService.class);

    @Autowired private InvoiceRepository invoiceRepository;

    @Value("${finance.tax-rate:0.00}")
    private BigDecimal taxRate;

    @Value("${finance.institution-name:Hi-Tech Institute}")
    private String institutionName;

    @Value("${finance.institution-address:No. 1, Hi-Tech Road, Colombo 03, Sri Lanka}")
    private String institutionAddress;

    @Value("${finance.institution-phone:+94 11 234 5678}")
    private String institutionPhone;

    @Value("${finance.institution-email:info@hitech.lk}")
    private String institutionEmail;

    // ---- Generate invoice after successful payment (FR-3.2 step 81-88) ----
    public Invoice generateInvoice(Payment payment) {
        int year = LocalDate.now().getYear();
        long count = invoiceRepository.countByYear(year);
        String invoiceNumber = String.format("INV-%d-%05d", year, count + 1);

        BigDecimal subtotal = payment.getAmount();
        BigDecimal taxAmt   = subtotal.multiply(taxRate)
                                      .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal total    = subtotal.add(taxAmt);

        String html = buildInvoiceHtml(payment, invoiceNumber, subtotal, taxAmt, total);

        Invoice invoice = Invoice.builder()
            .payment(payment)
            .student(payment.getStudent())
            .invoiceNumber(invoiceNumber)
            .subtotal(subtotal)
            .taxRate(taxRate)
            .taxAmount(taxAmt)
            .totalAmount(total)
            .currency(payment.getCurrency())
            .invoiceHtml(html)
            .status(Invoice.InvoiceStatus.GENERATED)
            .build();

        invoice = invoiceRepository.save(invoice);
        logger.info("Invoice {} generated for payment {}", invoiceNumber, payment.getId());
        return invoice;
    }

    // ---- Get invoice by ID ----
    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceById(Long invoiceId, User viewer) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found."));

        if (viewer.getRole() == User.Role.STUDENT
                && !invoice.getStudent().getId().equals(viewer.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied.");

        return mapToResponse(invoice);
    }

    // ---- Find by payment ID (used by PaymentService) ----
    @Transactional(readOnly = true)
    public Optional<Invoice> findByPaymentId(Long paymentId) {
        return invoiceRepository.findByPaymentId(paymentId);
    }

    // ---- Map to response DTO ----
    private InvoiceResponse mapToResponse(Invoice inv) {
        Payment p = inv.getPayment();
        Course  c = p.getCourse();
        return InvoiceResponse.builder()
            .id(inv.getId())
            .invoiceNumber(inv.getInvoiceNumber())
            .paymentId(p.getId())
            .gatewayReference(p.getGatewayReference())
            .studentId(inv.getStudent().getId())
            .studentName(inv.getStudent().getFullName())
            .studentEmail(inv.getStudent().getEmail())
            .courseId(c.getId())
            .courseTitle(c.getTitle())
            .courseCode(c.getCourseCode())
            .programName(c.getProgram() != null ? c.getProgram().getName() : "")
            .subtotal(inv.getSubtotal())
            .taxRate(inv.getTaxRate())
            .taxAmount(inv.getTaxAmount())
            .totalAmount(inv.getTotalAmount())
            .currency(inv.getCurrency())
            .cardBrand(p.getCardBrand())
            .cardLastFour(p.getCardLastFour())
            .status(inv.getStatus().name())
            .generatedAt(inv.getGeneratedAt())
            .paidAt(p.getPaidAt())
            .invoiceHtml(inv.getInvoiceHtml())
            .build();
    }

    // ---- HTML invoice template (FR-3.2: must include all required fields) ----
    private String buildInvoiceHtml(Payment p, String invNum,
                                    BigDecimal subtotal, BigDecimal taxAmt, BigDecimal total) {
        Course c = p.getCourse();
        String dateStr = p.getPaidAt() != null
            ? p.getPaidAt().format(DateTimeFormatter.ofPattern("dd MMMM yyyy"))
            : LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy"));
        String taxNote = taxRate.compareTo(BigDecimal.ZERO) == 0 ? " (Tax not applicable)" : "";
        String taxRateDisplay = taxRate.compareTo(BigDecimal.ZERO) == 0
            ? "0%" : taxRate.stripTrailingZeros().toPlainString() + "%";
        String cardDisplay = (p.getCardBrand() != null && p.getCardLastFour() != null)
            ? p.getCardBrand() + " card ending " + p.getCardLastFour()
            : "Mock Payment";
        String programName = c.getProgram() != null ? c.getProgram().getName() : "";

        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>Invoice %s</title>
<style>
  * { margin:0; padding:0; box-sizing:border-box; }
  body { font-family: 'Segoe UI', Arial, sans-serif; font-size:14px; color:#1e293b; background:#f8fafc; }
  .invoice-wrapper { max-width:760px; margin:40px auto; background:#fff; border-radius:12px;
    box-shadow:0 4px 24px rgba(0,0,0,.1); overflow:hidden; }
  .inv-header { background:linear-gradient(135deg,#1e3a8a 0%%,#3b82f6 100%%); color:#fff; padding:40px 48px; }
  .inv-header h1 { font-size:28px; font-weight:700; letter-spacing:.5px; }
  .inv-header .inv-meta { margin-top:8px; opacity:.85; font-size:13px; }
  .inv-body { padding:40px 48px; }
  .inv-parties { display:grid; grid-template-columns:1fr 1fr; gap:32px; margin-bottom:32px; }
  .party-block h3 { font-size:11px; font-weight:700; color:#64748b; text-transform:uppercase;
    letter-spacing:1px; margin-bottom:10px; }
  .party-block p { line-height:1.7; color:#334155; }
  .party-block .name { font-weight:700; font-size:16px; color:#1e293b; }
  .line-items { width:100%%; border-collapse:collapse; margin-bottom:28px; }
  .line-items thead tr { background:#f1f5f9; }
  .line-items th { padding:12px 16px; text-align:left; font-size:12px; font-weight:700;
    color:#64748b; text-transform:uppercase; letter-spacing:.5px; border-bottom:2px solid #e2e8f0; }
  .line-items td { padding:14px 16px; border-bottom:1px solid #f1f5f9; color:#334155; }
  .line-items .amount { text-align:right; font-weight:600; }
  .totals { margin-left:auto; width:300px; }
  .totals-row { display:flex; justify-content:space-between; padding:8px 0;
    border-bottom:1px solid #f1f5f9; color:#475569; }
  .totals-row.grand { font-size:18px; font-weight:700; color:#1e293b;
    border-bottom:none; padding-top:14px; }
  .badge-success { display:inline-block; background:#dcfce7; color:#166534;
    padding:3px 12px; border-radius:999px; font-size:12px; font-weight:600; }
  .inv-footer { background:#f8fafc; padding:24px 48px; border-top:1px solid #e2e8f0;
    font-size:12px; color:#94a3b8; display:flex; justify-content:space-between; align-items:center; }
  .inv-footer .ref { font-family:monospace; color:#64748b; font-size:11px; }
  @media print { body{background:#fff} .invoice-wrapper{box-shadow:none;border-radius:0;margin:0} }
</style>
</head>
<body>
<div class="invoice-wrapper">
  <div class="inv-header">
    <h1>%s</h1>
    <div class="inv-meta">Learning Management System</div>
    <div style="margin-top:20px;display:flex;justify-content:space-between;align-items:flex-end;">
      <div>
        <div style="font-size:22px;font-weight:700;">INVOICE</div>
        <div style="opacity:.8;margin-top:4px;">%s</div>
      </div>
      <div style="text-align:right;">
        <div style="font-size:13px;opacity:.8;">Date Issued</div>
        <div style="font-size:15px;font-weight:600;">%s</div>
        <span class="badge-success" style="margin-top:8px;">PAID</span>
      </div>
    </div>
  </div>
  <div class="inv-body">
    <div class="inv-parties">
      <div class="party-block">
        <h3>Billed To</h3>
        <p class="name">%s</p>
        <p>%s</p>
        <p>Student</p>
      </div>
      <div class="party-block">
        <h3>From</h3>
        <p class="name">%s</p>
        <p>%s</p>
        <p>%s</p>
        <p>%s</p>
      </div>
    </div>
    <table class="line-items">
      <thead>
        <tr>
          <th>Description</th>
          <th>Course Code</th>
          <th>Programme</th>
          <th class="amount">Amount</th>
        </tr>
      </thead>
      <tbody>
        <tr>
          <td><strong>%s</strong><br><span style="font-size:12px;color:#64748b;">Course Enrolment Fee</span></td>
          <td>%s</td>
          <td>%s</td>
          <td class="amount">%s %s</td>
        </tr>
      </tbody>
    </table>
    <div class="totals">
      <div class="totals-row">
        <span>Subtotal</span><span>%s %s</span>
      </div>
      <div class="totals-row">
        <span>Tax (%s)%s</span><span>%s %s</span>
      </div>
      <div class="totals-row grand">
        <span>Total</span><span>%s %s</span>
      </div>
    </div>
    <div style="margin-top:32px;padding:20px;background:#f0f9ff;border-radius:8px;border-left:4px solid #3b82f6;">
      <div style="font-size:12px;font-weight:700;color:#1e40af;margin-bottom:6px;">PAYMENT DETAILS</div>
      <div style="color:#334155;">Payment Method: <strong>%s</strong></div>
      <div style="color:#334155;margin-top:4px;">Transaction Reference: <code style="font-size:12px;background:#e0f2fe;padding:2px 6px;border-radius:4px;">%s</code></div>
    </div>
  </div>
  <div class="inv-footer">
    <span>Thank you for choosing %s</span>
    <span class="ref">Invoice: %s</span>
  </div>
</div>
</body>
</html>
""".formatted(
            invNum,
            institutionName, invNum, dateStr,
            p.getStudent().getFullName(), p.getStudent().getEmail(),
            institutionName, institutionAddress, institutionPhone, institutionEmail,
            c.getTitle(), c.getCourseCode(), programName,
            p.getCurrency(), format(subtotal),
            p.getCurrency(), format(subtotal),
            taxRateDisplay, taxNote, p.getCurrency(), format(taxAmt),
            p.getCurrency(), format(total),
            cardDisplay, p.getGatewayReference(),
            institutionName, invNum
        );
    }

    private String format(BigDecimal v) {
        return String.format("%,.2f", v);
    }
}
