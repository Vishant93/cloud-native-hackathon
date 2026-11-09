package com.example.vishant.cloud_native_hackathon;

import java.io.IOException;
import java.time.Instant;
import java.util.Base64;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
	public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegistrationRequest request)
			throws WriterException, IOException {
		if (request == null || request.name() == null || request.name().isBlank()
				|| request.email() == null || request.email().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attendee name and email must not be blank");
		}

		var attendee = attendeeService.register(request.name(), request.email());
		String checkInUrl = baseUrl + "/check-in/" + attendee.token();
		byte[] png = qrCodeService.generatePng(checkInUrl, 300, 300);
		var response = new RegistrationResponse(
				attendee.token(),
				attendee.name(),
				attendee.email(),
				checkInUrl,
				Base64.getEncoder().encodeToString(png));
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@GetMapping("/api/attendees/summary")
	public AttendeeCheckInService.AttendanceSummary attendanceSummary() {
		return attendeeService.getAttendanceSummary();
	}

	@GetMapping(value = "/check-in/{token}", produces = MediaType.TEXT_HTML_VALUE)
	public String checkInPage(@PathVariable String token) {
		var attendee = attendeeService.findByToken(token);
		if (attendee == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Check-in link not found");
		}

		if (attendee.checkedIn()) {
			return renderCheckInPage(
					"YOU'RE ALL SET",
					"Already checked in",
					"This ticket has already been checked in. You’re all set.",
					attendee.name(),
					null);
		}

		String confirmationUrl = HtmlUtils.htmlEscape(baseUrl + "/check-in/" + attendee.token());
		return renderCheckInPage(
				"ATTENDEE CHECK-IN",
				"Welcome, " + attendee.name(),
				"Confirm below when you arrive at the event entrance.",
				attendee.name(),
				confirmationUrl);
	}

	@PostMapping(value = "/check-in/{token}", produces = MediaType.TEXT_HTML_VALUE)
	public String checkIn(@PathVariable String token) {
		var attempt = attendeeService.checkInOnce(token);
		if (attempt == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Check-in link not found");
		}

		var attendee = attempt.attendee();
		if (!attempt.checkedInNow()) {
			return renderCheckInPage(
					"YOU'RE ALL SET",
					"Already checked in",
					"This ticket has already been checked in. You’re all set.",
					attendee.name(),
					null);
		}

		return renderCheckInPage(
				"CHECK-IN CONFIRMED",
				"You’re checked in",
				"Thanks for joining us. You’re all set to head inside.",
				attendee.name(),
				null);
	}

	private String renderCheckInPage(
			String eyebrow,
			String title,
			String message,
			String attendeeName,
			String confirmationUrl) {
		String action = confirmationUrl == null
				? ""
				: """
					<form method="post" action="%s">
						<button type="submit">Confirm check-in <span aria-hidden="true">→</span></button>
					</form>
					""".formatted(confirmationUrl);
		String icon = confirmationUrl == null ? "✓" : "→";
		String safeTitle = HtmlUtils.htmlEscape(title);
		if (confirmationUrl != null) {
			safeTitle = "Welcome, " + HtmlUtils.htmlEscape(attendeeName);
		}

		return """
				<!doctype html>
				<html lang="en">
				<head>
					<meta charset="utf-8">
					<meta name="viewport" content="width=device-width, initial-scale=1">
					<meta name="theme-color" content="#101827">
					<title>%s · EventPulse</title>
					<style>
						:root { color-scheme: dark; font-family: "Segoe UI", Arial, sans-serif; color: #edf2f8; background: #101827; }
						* { box-sizing: border-box; }
						body { min-width: 320px; min-height: 100vh; margin: 0; display: grid; place-items: center; padding: 24px; background: radial-gradient(ellipse at 75%% 8%%, rgba(52, 112, 145, .22), transparent 34rem), #101827; }
						.card { width: min(100%%, 480px); padding: 38px; border: 1px solid #2a374b; border-radius: 22px; background: rgba(23, 34, 51, .94); box-shadow: 0 24px 80px rgba(0, 0, 0, .3); }
						.brand { display: flex; align-items: center; gap: 11px; margin-bottom: 42px; color: #dce5ef; font-size: 13px; font-weight: 700; letter-spacing: .04em; }
						.brand-mark { width: 36px; height: 36px; display: grid; place-items: center; border-radius: 11px; background: #c9f36a; color: #16221a; font-size: 13px; letter-spacing: -.08em; }
						.eyebrow { margin: 0 0 10px; color: #a4b18e; font-size: 10px; font-weight: 700; letter-spacing: .16em; }
						.icon { width: 54px; height: 54px; display: grid; place-items: center; margin-bottom: 23px; border: 1px solid #536a3b; border-radius: 50%%; background: rgba(201, 243, 106, .12); color: #c9f36a; font-size: 25px; }
						h1 { margin: 0; color: #f3f6fa; font-size: clamp(26px, 6vw, 34px); letter-spacing: -.045em; line-height: 1.15; overflow-wrap: anywhere; }
						.message { margin: 13px 0 0; color: #aab6c7; font-size: 15px; line-height: 1.65; }
						.attendee { margin: 25px 0 0; padding: 14px 16px; border: 1px solid #344259; border-radius: 11px; color: #d5deea; font-size: 13px; }
						form { margin-top: 28px; }
						button { width: 100%%; display: flex; justify-content: center; align-items: center; gap: 10px; padding: 14px 18px; border: 0; border-radius: 10px; background: #c9f36a; color: #16221a; font: inherit; font-size: 14px; font-weight: 750; cursor: pointer; transition: background .15s ease, transform .15s ease; }
						button:hover { background: #d7ff7c; transform: translateY(-1px); }
						button:focus-visible { outline: 3px solid #efffca; outline-offset: 3px; }
						.footer { margin: 30px 0 0; padding-top: 18px; border-top: 1px solid #2a374b; color: #78889d; font-size: 11px; text-align: center; }
						@media (max-width: 480%%) { body { padding: 16px; } .card { padding: 26px 22px; } .brand { margin-bottom: 34px; } }
					</style>
				</head>
				<body>
					<main class="card">
						<div class="brand"><span class="brand-mark" aria-hidden="true">EP</span> EVENTPULSE</div>
						<div class="icon" aria-hidden="true">%s</div>
						<p class="eyebrow">%s</p>
						<h1>%s</h1>
						<p class="message">%s</p>
						<p class="attendee">Attendee <strong>%s</strong></p>
						%s
						<p class="footer">Your check-in is securely recorded with the event team.</p>
					</main>
				</body>
				</html>
				""".formatted(
				safeTitle,
				icon,
				HtmlUtils.htmlEscape(eyebrow),
				safeTitle,
				HtmlUtils.htmlEscape(message),
				HtmlUtils.htmlEscape(attendeeName),
				action);
	}

	@PostMapping("/api/attendees/{token}/check-in")
	public CheckInResponse checkInApi(@PathVariable String token) {
		var attempt = attendeeService.checkInOnce(token);
		if (attempt == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attendee ID is invalid");
		}
		if (!attempt.checkedInNow()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Attendee already checked in at " + attempt.attendee().checkedInAt());
		}
		var attendee = attempt.attendee();

		return new CheckInResponse(
				attendee.token(),
				attendee.name(),
				attendee.checkedIn(),
				attendee.checkedInAt());
	}

	public record RegistrationRequest(
			@NotBlank @Size(max = 255) String name,
			@NotBlank @Email @Size(max = 320) String email) {
	}

	public record RegistrationResponse(
			String attendeeId,
			String name,
			String email,
			String checkInUrl,
			String qrCodePngBase64) {
	}

	public record CheckInResponse(String attendeeId, String name, boolean checkedIn, Instant checkedInAt) {
	}
}
