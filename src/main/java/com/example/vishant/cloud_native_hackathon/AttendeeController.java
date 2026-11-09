package com.example.vishant.cloud_native_hackathon;

import java.io.IOException;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

import com.google.zxing.WriterException;

@RestController
public class AttendeeController {

	private final AttendeeCheckInService attendeeService;
	private final QrCodeService qrCodeService;
	private final String baseUrl;

	public AttendeeController(
			AttendeeCheckInService attendeeService,
			QrCodeService qrCodeService,
			@Value("${app.base-url:http://localhost:8080}") String baseUrl) {
		this.attendeeService = attendeeService;
		this.qrCodeService = qrCodeService;
		this.baseUrl = baseUrl.replaceAll("/+$", "");
	}

	@PostMapping("/api/attendees")
	public ResponseEntity<RegistrationResponse> register(@RequestBody RegistrationRequest request)
			throws WriterException, IOException {
		if (request == null || request.name() == null || request.name().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attendee name must not be blank");
		}

		var attendee = attendeeService.register(request.name());
		String checkInUrl = baseUrl + "/check-in/" + attendee.token();
		byte[] png = qrCodeService.generatePng(checkInUrl, 300, 300);
		var response = new RegistrationResponse(
				attendee.token(),
				attendee.name(),
				checkInUrl,
				Base64.getEncoder().encodeToString(png));
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@GetMapping(value = "/check-in/{token}", produces = MediaType.TEXT_HTML_VALUE)
	public String checkInPage(@PathVariable String token) {
		var attendee = attendeeService.findByToken(token);
		if (attendee == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Check-in link not found");
		}

		if (attendee.checkedIn()) {
			return "<!doctype html><html><body><h1>Already checked in</h1><p>"
					+ HtmlUtils.htmlEscape(attendee.name())
					+ "</p></body></html>";
		}

		return "<!doctype html><html><body><h1>Check in</h1><p>"
				+ HtmlUtils.htmlEscape(attendee.name())
				+ "</p><form method=\"post\" action=\"/check-in/"
				+ attendee.token()
				+ "\"><button type=\"submit\">Confirm check-in</button></form></body></html>";
	}

	@PostMapping(value = "/check-in/{token}", produces = MediaType.TEXT_HTML_VALUE)
	public String checkIn(@PathVariable String token) {
		var attendee = attendeeService.checkIn(token);
		if (attendee == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Check-in link not found");
		}

		return "<!doctype html><html><body><h1>Checked in</h1><p>"
				+ HtmlUtils.htmlEscape(attendee.name())
				+ "</p></body></html>";
	}

	public record RegistrationRequest(String name) {
	}

	public record RegistrationResponse(String attendeeId, String name, String checkInUrl, String qrCodePngBase64) {
	}
}
