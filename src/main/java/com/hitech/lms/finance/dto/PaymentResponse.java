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
 * Response for a completed payment (success or failure).
 * Returned after mock-pay and used in transaction lists.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PaymentResponse {
    private Long id;
    private Long intentId;
    private Long courseId;
    private String courseTitle;
    private String courseCode;
    private Long studentId;
    private String studentName;
    private String studentEmail;
    private BigDecimal amount;
    private String currency;
    private String status;              // SUCCESS / FAILED
    private String paymentMethod;       // MOCK_CARD
    private String cardLastFour;
    private String cardBrand;
    private String gatewayReference;
    private String failureReason;
    private LocalDateTime paidAt;
    private Long invoiceId;             // null if FAILED or invoice pending
    private String invoiceNumber;       // null if FAILED
}
