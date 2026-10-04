package com.paytm.seatreservation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytm.seatreservation.service.ReservationService;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SeatReservationApplicationTests {
	
	@Autowired
	ReservationService reservationService;
	
    @Test
    void java17SmokeTest() {
        assertEquals(17, Runtime.version().feature());
    }
    
//    @Test
//    public void ShowResponse() {
//    	reservationService.getShow(3l);
//    }
}
