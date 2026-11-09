package com.example.vishant.cloud_native_hackathon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.Base64;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

class AttendeeControllerTests {

	private final AttendeeCheckInService attendeeService = new AttendeeCheckInService();
	private final AttendeeController controller = new AttendeeController(
			attendeeService, new QrCodeService(), "https://event.example");

	@Test
	void eachAttendeeGetsUniqueCheckInUrlAndQrCode() throws Exception {
		var first = controller.register(new AttendeeController.RegistrationRequest("Alex"));
		var second = controller.register(new AttendeeController.RegistrationRequest("Sam"));

		assertNotEquals(first.getBody().attendeeId(), second.getBody().attendeeId());
		assertNotEquals(first.getBody().checkInUrl(), second.getBody().checkInUrl());
		assertEquals("https://event.example/check-in/" + first.getBody().attendeeId(),
				first.getBody().checkInUrl());

		var png = Base64.getDecoder().decode(first.getBody().qrCodePngBase64());
		var image = ImageIO.read(new ByteArrayInputStream(png));
		var bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
		assertEquals(first.getBody().checkInUrl(), new MultiFormatReader().decode(bitmap).getText());
	}

	@Test
	void attendeeMustConfirmCheckInFromScannedLink() throws Exception {
		var registration = controller.register(new AttendeeController.RegistrationRequest("Alex"));
		String token = registration.getBody().attendeeId();

		assertTrue(controller.checkInPage(token).contains("Confirm check-in"));
		assertTrue(controller.checkIn(token).contains("Checked in"));
		assertTrue(controller.checkInPage(token).contains("Already checked in"));
	}
}
