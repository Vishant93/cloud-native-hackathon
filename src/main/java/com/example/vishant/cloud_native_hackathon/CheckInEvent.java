package com.example.vishant.cloud_native_hackathon;

import java.time.Instant;

public record CheckInEvent(String eventId, String attendeeId, String attendeeName, Instant checkedInAt) {
}
