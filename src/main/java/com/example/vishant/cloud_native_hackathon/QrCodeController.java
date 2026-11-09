package com.example.vishant.cloud_native_hackathon;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;

import com.google.zxing.WriterException;

@RestController
public class QrCodeController {

	private final QrCodeService qrCodeService;

	public QrCodeController(QrCodeService qrCodeService) {
		this.qrCodeService = qrCodeService;
	}

	@GetMapping(value = "/api/qr", produces = MediaType.IMAGE_PNG_VALUE)
	public ResponseEntity<byte[]> generateQrCode(@RequestParam String text)
			throws WriterException, IOException {
		if (text.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "QR code text must not be blank");
		}

		var image = qrCodeService.generateImage(text, 300, 300);
		var output = new ByteArrayOutputStream();
		if (!ImageIO.write(image, "png", output)) {
			throw new IllegalStateException("PNG image writer is not available");
		}

		return ResponseEntity.ok()
				.contentType(MediaType.IMAGE_PNG)
				.body(output.toByteArray());
	}
}
