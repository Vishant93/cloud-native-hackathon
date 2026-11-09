package com.example.vishant.cloud_native_hackathon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

class QrCodeControllerTests {

	@Test
	void endpointReturnsDecodablePng() throws Exception {
		String expected = "https://example.com";
		var response = new QrCodeController(new QrCodeService()).generateQrCode(expected);

		assertEquals(MediaType.IMAGE_PNG, response.getHeaders().getContentType());
		var png = response.getBody();
		var image = ImageIO.read(new ByteArrayInputStream(png));
		var bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
		assertEquals(expected, new MultiFormatReader().decode(bitmap).getText());
	}
}
