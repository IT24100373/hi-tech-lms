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


import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * POST /api/payments/{intentId}/mock-pay
 * Simulates the card submission in demo mode.
 * In production this step is handled entirely by the gateway widget
 * (Stripe Elements / PayHere hosted form) — card data never reaches LMS.
 *
 * Mock card numbers (matches Stripe test cards pattern):
 *   4242424242424242 → SUCCESS
 *   4000000000000002 → DECLINED
 *   Any other 16-digit number → SUCCESS by default
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class MockPayRequest {

    @NotNull(message = "Payment intent ID is required")
    private Long intentId;

    // Simulated card number (16 digits, not stored)
    @NotBlank(message = "Card number is required")
    @Pattern(regexp = "\\d{16}", message = "Card number must be 16 digits")
    private String cardNumber;

    // Expiry in MM/YY format (validated but not stored)
    @NotBlank(message = "Expiry is required")
    @Pattern(regexp = "^(0[1-9]|1[0-2])/\\d{2}$", message = "Expiry must be MM/YY")
    private String expiry;

    // CVV (validated but NOT stored — PCI compliance)
    @NotBlank(message = "CVV is required")
    @Pattern(regexp = "\\d{3,4}", message = "CVV must be 3 or 4 digits")
    private String cvv;

    // Cardholder name on card
    @NotBlank(message = "Cardholder name is required")
    private String cardholderName;
}
