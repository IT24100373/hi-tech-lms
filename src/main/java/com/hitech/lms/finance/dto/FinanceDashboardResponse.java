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
import java.util.List;

/**
 * Admin Finance Dashboard KPI response.
 * FR-3.1 / FR-3.2: Admin monitors all transactions.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FinanceDashboardResponse {

    // KPI cards
    private BigDecimal totalRevenueAllTime;
    private BigDecimal totalRevenueThisMonth;
    private long totalSuccessfulTransactions;
    private long totalFailedTransactions;

    // Monthly revenue chart (last 12 months)
    private List<MonthlyRevenue> monthlyRevenue;

    // Top 5 courses by revenue
    private List<CourseRevenue> topCoursesByRevenue;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MonthlyRevenue {
        private int year;
        private int month;
        private String monthLabel;   // e.g. "Jan 2026"
        private BigDecimal amount;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CourseRevenue {
        private String courseTitle;
        private BigDecimal revenue;
    }
}
