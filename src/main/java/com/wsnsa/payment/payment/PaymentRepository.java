package com.wsnsa.payment.payment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, String> {

    Optional<Payment> findByQpayInvoiceId(String qpayInvoiceId);

    List<Payment> findByOrderIdOrderByCreatedAtDesc(String orderId);
}
