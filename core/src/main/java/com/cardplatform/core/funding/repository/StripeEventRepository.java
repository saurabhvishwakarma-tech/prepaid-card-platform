package com.cardplatform.core.funding.repository;

import com.cardplatform.core.funding.model.StripeEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StripeEventRepository extends JpaRepository<StripeEvent, String> {
}
