package Application;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("Mappers")
public class SeatReservationApplication {

	public static void main(String[] args) {
		 SpringApplication.run(SeatReservationApplication.class, args);
	}

}
