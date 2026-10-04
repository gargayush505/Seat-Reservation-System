package com.paytm.seatreservation.controller;

import com.paytm.seatreservation.dto.ReservationResponse;
import com.paytm.seatreservation.dto.ReserveRequest;
import com.paytm.seatreservation.security.AuthService;
import com.paytm.seatreservation.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class ReservationController {
    private final ReservationService service;
    private final AuthService auth;

    public ReservationController(ReservationService service, AuthService auth) { this.service=service; this.auth=auth; }

    @PostMapping("/shows/{showId}/reserve")
    public ResponseEntity<ReservationResponse> reserve(
            @PathVariable long showId,
            @RequestHeader(value="Authorization",required=false) String authorization,
            @Valid @RequestBody ReserveRequest request) {
        String userId=auth.user(authorization).userId();
        var result=service.reserve(showId,userId,request);
        return ResponseEntity.status(result.created()?201:200).body(result.response());
    }

    @PostMapping("/reservations/{reservationId}/cancel")
    public ReservationResponse cancel(@PathVariable String reservationId,
                                       @RequestHeader(value="Authorization",required=false) String authorization) {
        return service.cancel(reservationId,auth.user(authorization).userId());
    }
}
