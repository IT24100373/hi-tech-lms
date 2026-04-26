package com.hitech.lms.finance.dto;
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


import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Invoice response — used in student My Payments and admin Invoice Detail.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class InvoiceResponse {
    private Long id;
    private String invoiceNumber;
    private Long paymentId;
    private String gatewayReference;
    // Student info
    private Long studentId;
    private String studentName;
    private String studentEmail;
    // Course info
    private Long courseId;
    private String courseTitle;
    private String courseCode;
    private String programName;
    // Financials
    private BigDecimal subtotal;
    private BigDecimal taxRate;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private String currency;
    // Card
    private String cardBrand;
    private String cardLastFour;
    // Meta
    private String status;              // GENERATED / VOID
    private LocalDateTime generatedAt;
    private LocalDateTime paidAt;
    // Rendered HTML (for browser preview)
    private String invoiceHtml;
}
