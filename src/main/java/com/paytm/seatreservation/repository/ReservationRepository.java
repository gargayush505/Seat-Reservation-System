package com.paytm.seatreservation.repository;

import com.paytm.seatreservation.dto.ReservationResponse;
import com.paytm.seatreservation.dto.ShowResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ReservationRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public ReservationRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    // Oracle's RETURNING INTO is not supported by NamedParameterJdbcTemplate in a portable way.
    public long createShowAndReturnId(String name, long pricePaise, int limit) {
        long id = jdbc.queryForObject("SELECT show_seq.NEXTVAL FROM dual", new MapSqlParameterSource(), Long.class);
        jdbc.update("""
            INSERT INTO shows(show_id, show_name, show_price_paise, show_per_user_limit)
            VALUES (:id, :name, :price, :lim)
            """, new MapSqlParameterSource().addValue("id",id).addValue("name", name)
                .addValue("price", pricePaise).addValue("lim", limit));
        return id;
    }

    public void insertSeats(long showId, List<String> seats) {
        for (String seat : seats) {
            jdbc.update("INSERT INTO seats(show_id, seat_number, status) VALUES (:showId,:seat,'AVAILABLE')",
                    new MapSqlParameterSource().addValue("showId", showId).addValue("seat", seat));
        }
    }

    public Optional<ShowRow> findShow(long showId) {
        List<ShowRow> rows = jdbc.query("""
            SELECT show_id, show_name, show_price_paise, show_per_user_limit
            FROM shows WHERE show_id=:id
            """, new MapSqlParameterSource("id", showId),
            (rs, n) -> new ShowRow(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getInt(4)));
        return rows.stream().findFirst();
    }

    public List<SeatRow> seats(long showId) {
        return jdbc.query("""
            SELECT seat_number, status FROM seats
            WHERE show_id=:id ORDER BY seat_number
            """, new MapSqlParameterSource("id", showId),
            (rs,n) -> new SeatRow(rs.getString(1), rs.getString(2)));
    }

    public int insertReservation(String reservationId, long showId, String userId, String key, String hash, long amount) {
        String sql = """
            INSERT /*+ IGNORE_ROW_ON_DUPKEY_INDEX(reservations uq_res_idem) */
            INTO reservations(reservation_id,show_id,user_id,idempotency_key,request_hash,amount_paise,status)
            VALUES (:rid,:show,:user,:key,:hash,:amount,'CONFIRMED')
            """;
        return jdbc.update(sql, new MapSqlParameterSource()
                .addValue("rid", reservationId).addValue("show", showId).addValue("user", userId)
                .addValue("key", key).addValue("hash", hash).addValue("amount", amount));
    }

    public Optional<ReservationRow> findReservation(long showId, String userId, String key) {
        List<ReservationRow> rows = jdbc.query("""
            SELECT reservation_id, show_id, user_id, idempotency_key, request_hash, amount_paise, status
            FROM reservations WHERE show_id=:show AND user_id=:user AND idempotency_key=:key
            """, new MapSqlParameterSource().addValue("show", showId).addValue("user", userId).addValue("key", key),
            (rs,n) -> new ReservationRow(rs.getString(1),rs.getLong(2),rs.getString(3),rs.getString(4),
                    rs.getString(5),rs.getLong(6),rs.getString(7)));
        return rows.stream().findFirst();
    }

    public int incrementUserBooking(long showId, String userId, int count, int limit) {
        return jdbc.update("""
            MERGE INTO user_show_bookings b
            USING (SELECT :showId show_id, :userId user_id, :count increment_by, :limit max_limit FROM dual) s
            ON (b.show_id=s.show_id AND b.user_id=s.user_id)
            WHEN MATCHED THEN UPDATE SET b.booked_count=b.booked_count+s.increment_by
                WHERE b.booked_count+s.increment_by <= s.max_limit
            WHEN NOT MATCHED THEN INSERT(show_id,user_id,booked_count)
                VALUES(s.show_id,s.user_id,s.increment_by)
            """, new MapSqlParameterSource().addValue("showId",showId).addValue("userId",userId)
                .addValue("count",count).addValue("limit",limit));
    }

    public int confirmSeat(long showId, String seat, String reservationId) {
        return jdbc.update("""
            UPDATE seats SET status='CONFIRMED', reservation_id=:rid
            WHERE show_id=:show AND seat_number=:seat AND status='AVAILABLE'
            """, new MapSqlParameterSource().addValue("show",showId).addValue("seat",seat).addValue("rid",reservationId));
    }

    public void insertReservationSeat(String reservationId, long showId, String seat) {
        jdbc.update("""
            INSERT INTO reservation_seats(reservation_id,show_id,seat_number)
            VALUES (:rid,:show,:seat)
            """, new MapSqlParameterSource().addValue("rid",reservationId).addValue("show",showId).addValue("seat",seat));
    }

    public List<ReservationRow> findReservationById(String reservationId) {
        return jdbc.query("""
            SELECT reservation_id, show_id, user_id, idempotency_key, request_hash, amount_paise, status
            FROM reservations WHERE reservation_id=:rid
            """, new MapSqlParameterSource("rid",reservationId),
            (rs,n) -> new ReservationRow(rs.getString(1),rs.getLong(2),rs.getString(3),rs.getString(4),
                    rs.getString(5),rs.getLong(6),rs.getString(7)));
    }

    public List<String> reservationSeats(String reservationId) {
        return jdbc.query("""
            SELECT seat_number FROM reservation_seats
            WHERE reservation_id=:rid ORDER BY seat_number
            """, new MapSqlParameterSource("rid",reservationId),
            (rs,n)->rs.getString(1));
    }

    public int cancelReservation(String reservationId, long showId, String userId) {
        return jdbc.update("""
            UPDATE reservations SET status='CANCELLED', updated_at=SYSTIMESTAMP
            WHERE reservation_id=:rid AND show_id=:show AND user_id=:user AND status='CONFIRMED'
            """, new MapSqlParameterSource().addValue("rid",reservationId).addValue("show",showId).addValue("user",userId));
    }

    public int releaseSeats(String reservationId) {
        return jdbc.update("""
            UPDATE seats SET status='AVAILABLE', reservation_id=NULL
            WHERE reservation_id=:rid AND status='CONFIRMED'
            """, new MapSqlParameterSource("rid",reservationId));
    }

    public int decrementUserBooking(long showId, String userId, int count) {
        return jdbc.update("""
            UPDATE user_show_bookings SET booked_count=booked_count-:count
            WHERE show_id=:show AND user_id=:user AND booked_count >= :count
            """, new MapSqlParameterSource().addValue("show",showId).addValue("user",userId).addValue("count",count));
    }

    public int availableSeatCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM seats WHERE status='AVAILABLE'", new MapSqlParameterSource(), Integer.class);
    }

    public record ShowRow(long id,String name,long pricePaise,int perUserLimit) {}
    public record SeatRow(String seat,String status) {}
    public record ReservationRow(String id,long showId,String userId,String key,String hash,long amount,String status) {}

    public static ReservationResponse toResponse(ReservationRow r, List<String> seats) {
        return new ReservationResponse(r.id(),r.showId(),r.userId(),seats,r.amount(),r.status());
    }
}
