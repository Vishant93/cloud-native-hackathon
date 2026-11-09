package com.example.vishant.cloud_native_hackathon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

class CheckInEventSerializationTests {

	@Test
	void checkInEventCanBeSerializedAndDeserialized() {
		var event = new CheckInEvent(
				"event-123",
				"attendee-456",
				"Alex Morgan",
				Instant.parse("2026-11-09T12:00:00Z"));
		var serializer = new JacksonJsonSerializer<CheckInEvent>().noTypeInfo();
		var deserializer = new JacksonJsonDeserializer<>(CheckInEvent.class, false);

		var serialized = serializer.serialize("eventpulse.checkins", event);
		var deserialized = deserializer.deserialize("eventpulse.checkins", serialized);

		assertEquals(event, deserialized);
	}
}
