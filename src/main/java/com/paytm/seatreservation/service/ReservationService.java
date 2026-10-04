package com.paytm.seatreservation.service;

import com.paytm.seatreservation.dto.*;
import com.paytm.seatreservation.exception.DomainException;
import com.paytm.seatreservation.repository.ReservationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
public class ReservationService {
    private final ReservationRepository repo;
    private final MeterRegistry metrics;
    private final int defaultLimit;

    public ReservationService(ReservationRepository repo, MeterRegistry metrics,
                               @Value("${app.reservation.default-per-user-limit:4}") int defaultLimit) {
        this.repo = repo; this.metrics = metrics; this.defaultLimit = defaultLimit;
    }

    @Transactional
    public ShowResponse createShow(CreateShowRequest request) {
        int limit = request.perUserLimit() == null ? defaultLimit : request.perUserLimit();
        if (limit <= 0) throw new DomainException(HttpStatus.BAD_REQUEST,"invalid_limit","per_user_limit must be positive");
        List<String> seats = normalizedSeats(request.seats());
        long id;
        try {
            id = repo.createShowAndReturnId(request.name().trim(), request.pricePaise(), limit);
            repo.insertSeats(id, seats);
        } catch (DataIntegrityViolationException e) {
            throw new DomainException(HttpStatus.CONFLICT,"show_create_conflict","Show or seat names conflict");
        }
        return getShow(id);
    }

    @Transactional(readOnly = true)
    public ShowResponse getShow(long showId) {
        ReservationRepository.ShowRow show = repo.findShow(showId)
                .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND,"show_not_found","Show not found"));
        List<ReservationRepository.SeatRow> rows = repo.seats(showId);
        int available=0, confirmed=0, held=0;
        List<ShowResponse.SeatState> seats = new ArrayList<>();
        for (var row: rows) {
            if ("AVAILABLE".equals(row.status())) available++;
            else if ("CONFIRMED".equals(row.status())) confirmed++;
            else held++;
            seats.add(new ShowResponse.SeatState(row.seat(),"CONFIRMED".equals(row.status()) ? "confirmed" :
                    "AVAILABLE".equals(row.status()) ? "available" : "held"));
        }
        if (available + held + confirmed != rows.size())
            throw new IllegalStateException("seat reconciliation invariant violated");
        return new ShowResponse(show.id(),show.name(),show.pricePaise(),show.perUserLimit(),
                rows.size(),available,held,confirmed,seats);
    }

    @Transactional
    public ReserveResult reserve(long showId, String userId, ReserveRequest request) {
        ReservationRepository.ShowRow show = repo.findShow(showId)
                .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND,"show_not_found","Show not found"));
        if (request.idempotency_key() == null || request.idempotency_key().isBlank())
            throw new DomainException(HttpStatus.BAD_REQUEST,"missing_idempotency_key","idempotency_key is required");

        List<String> seats = normalizedSeats(request.seats());
        if (seats.size() > show.perUserLimit())
            throw decline("per-user-limit","Requested seats exceed per-user limit");

        String hash = hash(seats);
        Optional<ReservationRepository.ReservationRow> existing = repo.findReservation(showId,userId,request.idempotency_key());
        if (existing.isPresent()) {
            if (!existing.get().hash().equals(hash))
                throw decline("idempotency-mismatch","Idempotency key was already used with a different seat list");
            metricDeclined("idempotent-replay");
            return new ReserveResult(ReservationRepository.toResponse(existing.get(), repo.reservationSeats(existing.get().id())), false);
        }

        String reservationId = UUID.randomUUID().toString();
        int inserted = repo.insertReservation(reservationId,showId,userId,request.idempotency_key(),hash,show.pricePaise()*seats.size());
        if (inserted == 0) {
            ReservationRepository.ReservationRow winner = repo.findReservation(showId,userId,request.idempotency_key())
                    .orElseThrow(() -> new IllegalStateException("idempotency row disappeared"));
            if (!winner.hash().equals(hash))
                throw decline("idempotency-mismatch","Idempotency key was already used with a different seat list");
            metricDeclined("idempotent-replay");
            return new ReserveResult(ReservationRepository.toResponse(winner, repo.reservationSeats(winner.id())), false);
        }

        if (repo.incrementUserBooking(showId,userId,seats.size(),show.perUserLimit()) == 0)
            throw decline("per-user-limit","User booking limit exceeded");

        for (String seat : seats) {
            if (repo.confirmSeat(showId,seat,reservationId) != 1)
                throw decline("seat-taken","One or more requested seats are already taken");
            repo.insertReservationSeat(reservationId,showId,seat);
        }

        metricConfirmed(seats.size());
        ReservationRepository.ReservationRow saved = repo.findReservation(showId,userId,request.idempotency_key())
                .orElseThrow(() -> new IllegalStateException("reservation not found after insert"));
        return new ReserveResult(ReservationRepository.toResponse(saved,seats), true);
    }

    @Transactional
    public ReservationResponse cancel(String reservationId, String userId) {
        List<ReservationRepository.ReservationRow> rows = repo.findReservationById(reservationId);
        if (rows.isEmpty()) throw new DomainException(HttpStatus.NOT_FOUND,"reservation_not_found","Reservation not found");
        var r=rows.get(0);
        if (!r.userId().equals(userId)) throw new DomainException(HttpStatus.FORBIDDEN,"not_owner","Only the reservation owner can cancel");
        if ("CANCELLED".equals(r.status())) return ReservationRepository.toResponse(r,repo.reservationSeats(r.id()));
        int changed=repo.cancelReservation(reservationId,r.showId(),userId);
        if (changed != 1) return ReservationRepository.toResponse(r,repo.reservationSeats(r.id()));
        List<String> seats=repo.reservationSeats(reservationId);
        repo.releaseSeats(reservationId);
        repo.decrementUserBooking(r.showId(),userId,seats.size());
        return new ReservationResponse(r.id(),r.showId(),r.userId(),seats,r.amount(),"CANCELLED");
    }

    private List<String> normalizedSeats(List<String> input) {
        if (input == null || input.isEmpty()) throw new DomainException(HttpStatus.BAD_REQUEST,"empty_seats","At least one seat is required");
        List<String> seats=input.stream().map(String::trim).filter(s->!s.isBlank()).distinct().sorted().toList();
        if (seats.size()!=input.size()) throw new DomainException(HttpStatus.BAD_REQUEST,"duplicate_seats","Seat list must contain unique non-empty seat numbers");
        return seats;
    }

    private DomainException decline(String reason,String message) {
        metricDeclined(reason);
        return new DomainException(HttpStatus.CONFLICT,reason,message);
    }

    private void metricConfirmed(int n) {
        Counter.builder("reservations_confirmed").description("Confirmed seat reservations").register(metrics).increment();
    }
    private void metricDeclined(String reason) {
        Counter.builder("reservations_declined").tag("reason",reason)
                .description("Reservation domain declines").register(metrics).increment();
    }

    private String hash(List<String> seats) {
        try {
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest(String.join("\n",seats).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public record ReserveResult(ReservationResponse response, boolean created) {}
}
