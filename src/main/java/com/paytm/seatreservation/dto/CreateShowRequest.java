package com.paytm.seatreservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

public record CreateShowRequest(
        @NotBlank String name,
        @NotEmpty List<@NotBlank String> seats,
        @NotNull @PositiveOrZero @JsonProperty("price_paise") Long pricePaise,
        @JsonProperty("per_user_limit") Integer perUserLimit
) {}
