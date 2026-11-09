package com.example.vishant.cloud_native_hackathon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:attendees-test;DB_CLOSE_DELAY=-1",
		"app.base-url=https://event.example",
		"app.kafka.outbox.enabled=false",
		"spring.kafka.listener.auto-startup=false"
})
class AttendeeControllerTests {

	@Autowired
	private AttendeeCheckInService attendeeService;

	@Autowired
	private AttendeeController controller;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void resetAttendeeRecords() {
		jdbcTemplate.update("DELETE FROM check_in_outbox");
		jdbcTemplate.update("DELETE FROM attendees");
	}

	@Test
	void eachAttendeeGetsUniqueCheckInUrlAndQrCode() throws Exception {
		var first = controller.register(new AttendeeController.RegistrationRequest("Alex", "alex@example.com"));
		var second = controller.register(new AttendeeController.RegistrationRequest("Sam", "sam@example.com"));

		assertNotEquals(first.getBody().attendeeId(), second.getBody().attendeeId());
		assertNotEquals(first.getBody().checkInUrl(), second.getBody().checkInUrl());
		assertEquals("alex@example.com", first.getBody().email());
		assertEquals("https://event.example/check-in/" + first.getBody().attendeeId(),
				first.getBody().checkInUrl());
		assertEquals("alex@example.com", attendeeService.findByToken(first.getBody().attendeeId()).email());

		var png = Base64.getDecoder().decode(first.getBody().qrCodePngBase64());
		var image = ImageIO.read(new ByteArrayInputStream(png));
		var bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
		assertEquals(first.getBody().checkInUrl(), new MultiFormatReader().decode(bitmap).getText());
	}

	@Test
	void attendeeMustConfirmCheckInFromScannedLink() throws Exception {
		var registration = controller.register(new AttendeeController.RegistrationRequest("Alex", "alex@example.com"));
		String token = registration.getBody().attendeeId();

		assertTrue(controller.checkInPage(token).contains(
				"action=\"https://event.example/check-in/" + token + "\""));
		assertTrue(controller.checkInPage(token).contains("Confirm check-in"));
		assertTrue(controller.checkIn(token).contains("Checked in"));
		assertTrue(controller.checkInPage(token).contains("Already checked in"));
		assertTrue(controller.checkIn(token).contains("Already checked in"));
	}

	@Test
	void unknownAttendeeIdDoesNotValidate() {
		assertNull(attendeeService.findByToken("not-a-real-attendee"));
	}

	@Test
	void apiCheckInReturnsAttendeeAndPersistsCheckIn() throws Exception {
		var registration = controller.register(new AttendeeController.RegistrationRequest("Jordan", "jordan@example.com"));
		var result = controller.checkInApi(registration.getBody().attendeeId());

		assertEquals(registration.getBody().attendeeId(), result.attendeeId());
		assertEquals("Jordan", result.name());
		assertTrue(result.checkedIn());
		assertTrue(attendeeService.findByToken(result.attendeeId()).checkedIn());
		assertEquals(1, jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM check_in_outbox WHERE attendee_id = ?",
				Integer.class,
				result.attendeeId()));
	}

	@Test
	void apiCheckInRejectsUnknownToken() {
		var exception = assertThrows(ResponseStatusException.class,
				() -> controller.checkInApi("not-a-real-attendee"));
		assertEquals(404, exception.getStatusCode().value());
	}

	@Test
	void apiCheckInRejectsRepeatCheckIn() throws Exception {
		var registration = controller.register(new AttendeeController.RegistrationRequest("Casey", "casey@example.com"));
		String token = registration.getBody().attendeeId();
		controller.checkInApi(token);

		var exception = assertThrows(ResponseStatusException.class, () -> controller.checkInApi(token));
		assertEquals(409, exception.getStatusCode().value());
		assertEquals(1, jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM check_in_outbox WHERE attendee_id = ?",
				Integer.class,
				token));
	}

	@Test
	void concurrentApiCheckInsOnlyAllowOneRequest() throws Exception {
		var registration = controller.register(new AttendeeController.RegistrationRequest("Riley", "riley@example.com"));
		String token = registration.getBody().attendeeId();
		var executor = Executors.newFixedThreadPool(2);
		var start = new CountDownLatch(1);

		try {
			Future<Integer> first = executor.submit(() -> checkInStatusAfter(start, token));
			Future<Integer> second = executor.submit(() -> checkInStatusAfter(start, token));
			start.countDown();

			var statuses = java.util.List.of(first.get(), second.get());
			assertEquals(1, statuses.stream().filter(status -> status == 200).count());
			assertEquals(1, statuses.stream().filter(status -> status == 409).count());
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void attendanceSummaryCountsRegisteredCheckedInAndRemainingAttendees() throws Exception {
		var first = controller.register(new AttendeeController.RegistrationRequest("Alex", "alex@example.com"));
		controller.register(new AttendeeController.RegistrationRequest("Sam", "sam@example.com"));
		controller.checkInApi(first.getBody().attendeeId());

		var summary = controller.attendanceSummary();

		assertEquals(2, summary.totalRegistered());
		assertEquals(1, summary.checkedIn());
		assertEquals(1, summary.remaining());
	}

	@Test
	void attendanceSummaryIsZeroForEmptyAttendeeList() {
		var summary = controller.attendanceSummary();

		assertEquals(0, summary.totalRegistered());
		assertEquals(0, summary.checkedIn());
		assertEquals(0, summary.remaining());
	}

	private int checkInStatusAfter(CountDownLatch start, String token) throws InterruptedException {
		start.await();
		try {
			controller.checkInApi(token);
			return 200;
		} catch (ResponseStatusException exception) {
			return exception.getStatusCode().value();
		}
	}
}
