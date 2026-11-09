package com.example.vishant.cloud_native_hackathon;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

@Service
public class QrCodeService {

	public BufferedImage generateImage(String content, int width, int height) throws WriterException {
		if (content == null || content.isBlank()) {
			throw new IllegalArgumentException("QR code content must not be blank");
		}
		if (width <= 0 || height <= 0) {
			throw new IllegalArgumentException("QR code dimensions must be positive");
		}

		var hints = java.util.Map.of(
				EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
				EncodeHintType.MARGIN, 1);
		var matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, width, height, hints);
		return MatrixToImageWriter.toBufferedImage(matrix);
	}

	public byte[] generatePng(String content, int width, int height) throws WriterException, IOException {
		var output = new ByteArrayOutputStream();
		if (!ImageIO.write(generateImage(content, width, height), "png", output)) {
			throw new IllegalStateException("PNG image writer is not available");
		}
		return output.toByteArray();
	}
}
