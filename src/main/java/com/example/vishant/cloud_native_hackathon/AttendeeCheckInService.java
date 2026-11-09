package com.example.vishant.cloud_native_hackathon;

import java.time.Instant;
import java.util.UUID;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class AttendeeCheckInService {

	private static final RowMapper<Attendee> ATTENDEE_ROW_MAPPER = AttendeeCheckInService::mapAttendee;

	private final JdbcTemplate jdbcTemplate;

	public AttendeeCheckInService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public Attendee register(String name) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Attendee name must not be blank");
		}

		String token = UUID.randomUUID().toString();
		String normalizedName = name.trim();
		jdbcTemplate.update(
				"INSERT INTO attendees (token, name, checked_in) VALUES (?, ?, FALSE)",
				token,
				normalizedName);
		var attendee = new Attendee(token, normalizedName, false, null);
		return attendee;
	}

	public Attendee findByToken(String token) {
		return jdbcTemplate.query(
				"SELECT token, name, checked_in, checked_in_at FROM attendees WHERE token = ?",
				ATTENDEE_ROW_MAPPER,
				token).stream().findFirst().orElse(null);
	}

	public Attendee checkIn(String token) {
		jdbcTemplate.update(
				"UPDATE attendees SET checked_in = TRUE, checked_in_at = CURRENT_TIMESTAMP "
						+ "WHERE token = ? AND checked_in = FALSE",
				token);
		return findByToken(token);
	}

	private static Attendee mapAttendee(ResultSet resultSet, int rowNumber) throws SQLException {
		var checkedInAt = resultSet.getTimestamp("checked_in_at");
		return new Attendee(
				resultSet.getString("token"),
				resultSet.getString("name"),
				resultSet.getBoolean("checked_in"),
				checkedInAt == null ? null : checkedInAt.toInstant());
	}

	public record Attendee(String token, String name, boolean checkedIn, Instant checkedInAt) {
	}
}
