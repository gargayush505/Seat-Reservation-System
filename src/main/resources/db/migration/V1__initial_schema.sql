CREATE SEQUENCE show_seq START WITH 1 INCREMENT BY 1 NOCACHE;

CREATE TABLE shows (
    show_id NUMBER PRIMARY KEY,
    show_name VARCHAR2(200 CHAR) NOT NULL,
    show_price_paise NUMBER(19,0) NOT NULL,
    show_per_user_limit NUMBER(10,0) DEFAULT 4 NOT NULL,
    show_created_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT ck_shows_price CHECK (show_price_paise >= 0),
    CONSTRAINT ck_shows_limit CHECK (show_per_user_limit > 0)
);

CREATE TABLE reservations (
    reservation_id VARCHAR2(36 CHAR) PRIMARY KEY,
    show_id NUMBER NOT NULL,
    user_id VARCHAR2(200 CHAR) NOT NULL,
    idempotency_key VARCHAR2(200 CHAR) NOT NULL,
    request_hash VARCHAR2(64 CHAR) NOT NULL,
    amount_paise NUMBER(19,0) NOT NULL,
    status VARCHAR2(20 CHAR) NOT NULL,
    created_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT fk_res_show FOREIGN KEY (show_id) REFERENCES shows(show_id),
    CONSTRAINT ck_res_status CHECK (status IN ('CONFIRMED','CANCELLED')),
    CONSTRAINT uq_res_idem UNIQUE (show_id, user_id, idempotency_key)
);

CREATE TABLE seats (
    seat_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    show_id NUMBER NOT NULL,
    seat_number VARCHAR2(50 CHAR) NOT NULL,
    status VARCHAR2(20 CHAR) DEFAULT 'AVAILABLE' NOT NULL,
    reservation_id VARCHAR2(36 CHAR),
    CONSTRAINT fk_seat_show FOREIGN KEY (show_id) REFERENCES shows(show_id),
    CONSTRAINT fk_seat_res FOREIGN KEY (reservation_id) REFERENCES reservations(reservation_id),
    CONSTRAINT uq_seat_show_number UNIQUE (show_id, seat_number),
    CONSTRAINT ck_seat_status CHECK (status IN ('AVAILABLE','CONFIRMED'))
);

CREATE TABLE reservation_seats (
    reservation_id VARCHAR2(36 CHAR) NOT NULL,
    show_id NUMBER NOT NULL,
    seat_number VARCHAR2(50 CHAR) NOT NULL,
    CONSTRAINT pk_reservation_seats PRIMARY KEY (reservation_id, seat_number),
    CONSTRAINT fk_rs_res FOREIGN KEY (reservation_id) REFERENCES reservations(reservation_id),
    CONSTRAINT fk_rs_seat FOREIGN KEY (show_id, seat_number) REFERENCES seats(show_id, seat_number)
);

CREATE TABLE user_show_bookings (
    show_id NUMBER NOT NULL,
    user_id VARCHAR2(200 CHAR) NOT NULL,
    booked_count NUMBER(10,0) DEFAULT 0 NOT NULL,
    CONSTRAINT pk_user_show_bookings PRIMARY KEY (show_id, user_id),
    CONSTRAINT fk_usb_show FOREIGN KEY (show_id) REFERENCES shows(show_id),
    CONSTRAINT ck_usb_count CHECK (booked_count >= 0)
);

CREATE INDEX ix_seats_show_status ON seats(show_id, status);
CREATE INDEX ix_res_show_user ON reservations(show_id, user_id);
CREATE INDEX ix_res_seat_show ON reservation_seats(show_id, seat_number);
