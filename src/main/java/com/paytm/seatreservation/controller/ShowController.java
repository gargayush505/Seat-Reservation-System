package com.paytm.seatreservation.controller;

import com.paytm.seatreservation.dto.CreateShowRequest;
import com.paytm.seatreservation.dto.ShowResponse;
import com.paytm.seatreservation.security.AuthService;
import com.paytm.seatreservation.service.ReservationService;
import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/shows")
public class ShowController {
    private final ReservationService service;
    private final AuthService auth;
    private static final Logger logger =LoggerFactory.getLogger(ShowController.class);
    public ShowController(ReservationService service, AuthService auth) { this.service=service; this.auth=auth; }

    @PostMapping
    public ResponseEntity<ShowResponse> create(@RequestHeader(value="Authorization",required=false) String authorization,
                                                @Valid @RequestBody CreateShowRequest request) {
        auth.requireAdmin(authorization);
    	logger.info("In Create Show API");
        return ResponseEntity.status(201).body(service.createShow(request));
    }

    @GetMapping("/{showId}")
    public ShowResponse get(@PathVariable long showId) { 
    	logger.info("In ShowResponse API");
    	return service.getShow(showId); }
}
