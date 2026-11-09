package com.example.vishant.cloud_native_hackathon;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;

@Component
@ConditionalOnProperty(prefix = "app.kafka.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CheckInEventOutboxPublisher {

	private static final Logger logger = LoggerFactory.getLogger(CheckInEventOutboxPublisher.class);
	private static final int BATCH_SIZE = 100;

	private final JdbcTemplate jdbcTemplate;
	private final KafkaTemplate<String, CheckInEvent> kafkaTemplate;
	private final String topic;
	private final Set<String> inFlightEventIds = ConcurrentHashMap.newKeySet();

	public CheckInEventOutboxPublisher(
			JdbcTemplate jdbcTemplate,
			KafkaTemplate<String, CheckInEvent> kafkaTemplate,
			@Value("${app.kafka.check-in-topic:eventpulse.checkins}") String topic) {
		this.jdbcTemplate = jdbcTemplate;
		this.kafkaTemplate = kafkaTemplate;
		this.topic = topic;
	}

	@Scheduled(fixedDelayString = "${app.kafka.outbox.poll-interval:1000}")
	public void publishPendingEvents() {
		var events = jdbcTemplate.query(
				"SELECT event_id, attendee_id, attendee_name, checked_in_at "
						+ "FROM check_in_outbox WHERE published_at IS NULL "
						+ "ORDER BY created_at LIMIT ?",
				(resultSet, rowNumber) -> mapEvent(resultSet),
				BATCH_SIZE);

		for (var event : events) {
			if (inFlightEventIds.add(event.eventId())) {
				publish(event);
			}
		}
	}

	private void publish(CheckInEvent event) {
		try {
			kafkaTemplate.send(topic, event.attendeeId(), event)
					.whenComplete((result, error) -> {
						try {
							if (error != null) {
								logger.error("Could not publish check-in event {}; it will be retried",
										event.eventId(), error);
								return;
							}
							int updatedRows = jdbcTemplate.update(
									"UPDATE check_in_outbox SET published_at = ? "
											+ "WHERE event_id = ? AND published_at IS NULL",
									java.sql.Timestamp.from(Instant.now()),
									event.eventId());
							if (updatedRows != 1) {
								logger.warn("Check-in event {} was acknowledged but its outbox row was not updated",
										event.eventId());
							}
						} catch (DataAccessException exception) {
							logger.error("Could not mark check-in event {} as published; it may be delivered again",
									event.eventId(), exception);
						} finally {
							inFlightEventIds.remove(event.eventId());
						}
					});
		} catch (KafkaException exception) {
			inFlightEventIds.remove(event.eventId());
			logger.error("Could not start publishing check-in event {}; it will be retried",
					event.eventId(), exception);
		}
	}

	private static CheckInEvent mapEvent(ResultSet resultSet) throws SQLException {
		return new CheckInEvent(
				resultSet.getString("event_id"),
				resultSet.getString("attendee_id"),
				resultSet.getString("attendee_name"),
				resultSet.getTimestamp("checked_in_at").toInstant());
	}
}
