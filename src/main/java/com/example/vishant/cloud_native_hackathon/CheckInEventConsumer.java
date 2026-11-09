package com.example.vishant.cloud_native_hackathon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class CheckInEventConsumer {

	private static final Logger logger = LoggerFactory.getLogger(CheckInEventConsumer.class);

	@KafkaListener(
			topics = "${app.kafka.check-in-topic:eventpulse.checkins}",
			groupId = "${spring.kafka.consumer.group-id:eventpulse-check-in-consumer}",
			containerFactory = "checkInKafkaListenerContainerFactory")
	public void consume(CheckInEvent event) {
		logger.info("Processed check-in event {} for attendee {} ({}) at {}",
				event.eventId(), event.attendeeId(), event.attendeeName(), event.checkedInAt());
	}
}
