package com.hitech.lms.finance.repository;
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


import com.hitech.lms.finance.model.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // ---- Student: their own payment history ----
    Page<Payment> findByStudentIdOrderByPaidAtDesc(Long studentId, Pageable pageable);

    // ---- Admin: searchable / filterable transaction ledger ----
    @Query("SELECT p FROM Payment p WHERE " +
           "(:search IS NULL OR LOWER(p.student.fullName) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(p.student.email) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:status IS NULL OR p.status = :status) " +
           "AND (:courseId IS NULL OR p.course.id = :courseId) " +
           "AND (:from IS NULL OR p.paidAt >= :from) " +
           "AND (:to IS NULL OR p.paidAt <= :to)")
    Page<Payment> searchTransactions(
        @Param("search")   String search,
        @Param("status")   Payment.PaymentStatus status,
        @Param("courseId") Long courseId,
        @Param("from")     LocalDateTime from,
        @Param("to")       LocalDateTime to,
        Pageable pageable
    );

    // ---- Dashboard: total revenue (all time, SUCCESS only) ----
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = 'SUCCESS'")
    BigDecimal sumTotalRevenue();

    // ---- Dashboard: revenue for current month ----
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
           "WHERE p.status = 'SUCCESS' " +
           "AND YEAR(p.paidAt) = YEAR(CURRENT_DATE) " +
           "AND MONTH(p.paidAt) = MONTH(CURRENT_DATE)")
    BigDecimal sumRevenueCurrentMonth();

    // ---- Dashboard: count by status ----
    long countByStatus(Payment.PaymentStatus status);

    // ---- Monthly revenue chart: last 12 months ----
    @Query(value = "SELECT YEAR(paid_at) AS yr, MONTH(paid_at) AS mo, SUM(amount) AS total " +
                   "FROM payments WHERE status = 'SUCCESS' " +
                   "AND paid_at >= DATE_SUB(CURRENT_DATE, INTERVAL 12 MONTH) " +
                   "GROUP BY yr, mo ORDER BY yr ASC, mo ASC",
           nativeQuery = true)
    List<Object[]> getMonthlyRevenueLast12Months();

    // ---- Top courses by revenue ----
    @Query(value = "SELECT c.title, SUM(p.amount) AS revenue " +
                   "FROM payments p JOIN courses c ON p.course_id = c.id " +
                   "WHERE p.status = 'SUCCESS' " +
                   "GROUP BY p.course_id, c.title ORDER BY revenue DESC LIMIT 5",
           nativeQuery = true)
    List<Object[]> getTopCoursesByRevenue();
}
