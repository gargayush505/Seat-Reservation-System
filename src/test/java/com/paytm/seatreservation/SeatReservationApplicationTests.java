package com.paytm.seatreservation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SeatReservationApplicationTests {
    @Test
    void java17SmokeTest() {
        assertEquals(17, Runtime.version().feature());
    }
}
