package com.example.vishant.cloud_native_hackathon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

class QrCodeServiceTests {

	private final QrCodeService qrCodeService = new QrCodeService();

	@Test
	void generatedQrCodeCanBeDecodedToOriginalContent() throws Exception {
		String expected = "https://example.com";
		BufferedImage image = qrCodeService.generateImage(expected, 300, 300);
		var bitmap = new BinaryBitmap(
				new HybridBinarizer(new BufferedImageLuminanceSource(image)));

		assertEquals(expected, new MultiFormatReader().decode(bitmap).getText());
	}
}
