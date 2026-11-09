package com.example.vishant.cloud_native_hackathon;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Service;

@Service
public class AttendeeCheckInService {

	private final ConcurrentMap<String, Attendee> attendees = new ConcurrentHashMap<>();

	public Attendee register(String name) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Attendee name must not be blank");
		}

		String token;
		Attendee attendee;
		do {
			token = UUID.randomUUID().toString();
			attendee = new Attendee(token, name.trim(), false, null);
		} while (attendees.putIfAbsent(token, attendee) != null);

		return attendee;
	}

	public Attendee findByToken(String token) {
		return attendees.get(token);
	}

	public Attendee checkIn(String token) {
		return attendees.computeIfPresent(token, (key, attendee) -> attendee.checkedIn()
				? attendee
				: new Attendee(attendee.token(), attendee.name(), true, Instant.now()));
	}

	public record Attendee(String token, String name, boolean checkedIn, Instant checkedInAt) {
	}
}
