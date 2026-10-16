package com.wsnsa.payment.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Background job-ууд. Олон instance ажиллуулж байгаа бол зөвхөн нэг дээр нь
 * {@code payment.scheduling-enabled=true} байлгана.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "payment", name = "scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
