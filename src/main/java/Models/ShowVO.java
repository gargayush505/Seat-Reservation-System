package Models;

import java.util.List;

public class ShowVO {

	int showId;

    String name;

    long price_paise;

    int perUserLimit;

    int totalSeats;

    int availableSeats;

    int holdSeats;

    int confirmedSeats;

    List<String> seats;

	public int getShowId() {
		return showId;
	}

	public void setShowId(int showId) {
		this.showId = showId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public long getPricePaise() {
		return price_paise;
	}

	public void setPricePaise(long price_paise) {
		this.price_paise = price_paise;
	}

	public int getPerUserLimit() {
		return perUserLimit;
	}

	public void setPerUserLimit(int perUserLimit) {
		this.perUserLimit = perUserLimit;
	}

	public int getTotalSeats() {
		return totalSeats;
	}

	public void setTotalSeats(int totalSeats) {
		this.totalSeats = totalSeats;
	}

	public int getAvailableSeats() {
		return availableSeats;
	}

	public void setAvailableSeats(int availableSeats) {
		this.availableSeats = availableSeats;
	}

	public int getHoldSeats() {
		return holdSeats;
	}

	public void setHoldSeats(int holdSeats) {
		this.holdSeats = holdSeats;
	}

	public int getConfirmedSeats() {
		return confirmedSeats;
	}

	public void setConfirmedSeats(int confirmedSeats) {
		this.confirmedSeats = confirmedSeats;
	}

	public List<String> getSeats() {
		return seats;
	}

	public void setSeats(List<String> seats) {
		this.seats = seats;
	}
    
    
}
