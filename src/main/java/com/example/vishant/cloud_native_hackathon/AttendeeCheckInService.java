package com.example.vishant.cloud_native_hackathon;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttendeeCheckInService {

	private static final int RECENT_CHECK_IN_LIMIT = 8;
	private static final RowMapper<Attendee> ATTENDEE_ROW_MAPPER = AttendeeCheckInService::mapAttendee;

	private final JdbcTemplate jdbcTemplate;

	public AttendeeCheckInService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public Attendee register(String name, String email) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Attendee name must not be blank");
		}
		if (email == null || email.isBlank()) {
			throw new IllegalArgumentException("Attendee email must not be blank");
		}

		String token = UUID.randomUUID().toString();
		String normalizedName = name.trim();
		String normalizedEmail = email.trim();
		jdbcTemplate.update(
				"INSERT INTO attendees (token, name, email, checked_in) VALUES (?, ?, ?, FALSE)",
				token,
				normalizedName,
				normalizedEmail);
		var attendee = new Attendee(token, normalizedName, normalizedEmail, false, null);
		return attendee;
	}

	public Attendee findByToken(String token) {
		return jdbcTemplate.query(
				"SELECT token, name, email, checked_in, checked_in_at FROM attendees WHERE token = ?",
				ATTENDEE_ROW_MAPPER,
				token).stream().findFirst().orElse(null);
	}

	@Transactional
	public CheckInAttempt checkInOnce(String token) {
		int updatedRows = jdbcTemplate.update(
				"UPDATE attendees SET checked_in = TRUE, checked_in_at = CURRENT_TIMESTAMP "
						+ "WHERE token = ? AND checked_in = FALSE",
				token);
		var attendee = findByToken(token);
		if (updatedRows == 1) {
			jdbcTemplate.update(
					"INSERT INTO check_in_outbox (event_id, attendee_id, attendee_name, checked_in_at) "
							+ "VALUES (?, ?, ?, ?)",
					UUID.randomUUID().toString(),
					attendee.token(),
					attendee.name(),
					Timestamp.from(attendee.checkedInAt()));
		}
		return attendee == null ? null : new CheckInAttempt(attendee, updatedRows == 1);
	}

	public AttendanceSummary getAttendanceSummary() {
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*) AS total_registered, "
						+ "COUNT(CASE WHEN checked_in = TRUE THEN 1 END) AS checked_in "
						+ "FROM attendees",
				(resultSet, rowNumber) -> {
					long totalRegistered = resultSet.getLong("total_registered");
					long checkedIn = resultSet.getLong("checked_in");
					return new AttendanceSummary(totalRegistered, checkedIn, totalRegistered - checkedIn);
				});
	}

	public List<RecentCheckIn> getRecentCheckIns() {
		return jdbcTemplate.query(
				"SELECT name, checked_in_at FROM attendees "
						+ "WHERE checked_in = TRUE AND checked_in_at IS NOT NULL "
						+ "ORDER BY checked_in_at DESC, token DESC LIMIT ?",
				(resultSet, rowNumber) -> new RecentCheckIn(
						resultSet.getString("name"),
						resultSet.getTimestamp("checked_in_at").toInstant()),
				RECENT_CHECK_IN_LIMIT);
	}

	private static Attendee mapAttendee(ResultSet resultSet, int rowNumber) throws SQLException {
		var checkedInAt = resultSet.getTimestamp("checked_in_at");
		return new Attendee(
				resultSet.getString("token"),
				resultSet.getString("name"),
				resultSet.getString("email"),
				resultSet.getBoolean("checked_in"),
				checkedInAt == null ? null : checkedInAt.toInstant());
	}

	public record Attendee(String token, String name, String email, boolean checkedIn, Instant checkedInAt) {
	}

	public record CheckInAttempt(Attendee attendee, boolean checkedInNow) {
	}

	public record AttendanceSummary(long totalRegistered, long checkedIn, long remaining) {
	}

	public record RecentCheckIn(String name, Instant checkedInAt) {
	}
}
