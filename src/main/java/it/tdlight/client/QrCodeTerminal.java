package it.tdlight.client;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.util.Hashtable;

public class QrCodeTerminal {

	public static String getQr(String url) {
		int width = 40;
		int height = 40;
		Hashtable<EncodeHintType, Object> qrParam = new Hashtable<>();
		qrParam.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
		qrParam.put(EncodeHintType.CHARACTER_SET, "utf-8");
		try {
			BitMatrix bitMatrix = new MultiFormatWriter().encode(url, BarcodeFormat.QR_CODE, width, height, qrParam);
			return toAscii(bitMatrix);
		} catch (WriterException ex) {
			throw new IllegalStateException("Can't encode QR code", ex);
		}
	}

	static String toAscii(BitMatrix bitMatrix) {
		StringBuilder sb = new StringBuilder();
		for (int rows = 0; rows < bitMatrix.getHeight(); rows++) {
			for (int cols = 0; cols < bitMatrix.getWidth(); cols++) {
				boolean x = bitMatrix.get(rows, cols);
				sb.append(x ? "  " : "██");
			}
			sb.append("\n");
		}
		return sb.toString();
	}
}
