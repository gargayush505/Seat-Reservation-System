package com.paytm.seatreservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ShowResponse(
        long id,
        String name,
        @JsonProperty("price_paise") long pricePaise,
        @JsonProperty("per_user_limit") int perUserLimit,
        @JsonProperty("total_seats") int totalSeats,
        @JsonProperty("available_seats") int availableSeats,
        @JsonProperty("held_seats") int heldSeats,
        @JsonProperty("confirmed_seats") int confirmedSeats,
        List<SeatState> seats
) {
    public record SeatState(String seat, String status) {}
}
