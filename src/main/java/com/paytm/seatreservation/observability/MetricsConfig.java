package com.paytm.seatreservation.observability;

import com.paytm.seatreservation.repository.ReservationRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {
    @Bean
    Gauge availableSeatsGauge(MeterRegistry registry, ReservationRepository repository) {
        return Gauge.builder("seats_available", repository, r -> r.availableSeatCount())
                .description("Currently available seats across all shows")
                .register(registry);
    }
}
