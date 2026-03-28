package cn.suhoan.starlight.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;

/**
 * 二维码生成服务。
 * <p>使用 ZXing 库将文本内容生成为 Base64 编码的 PNG 图片 Data URL。</p>
 *
 * @author suhoan
 */
@Service
public class QrCodeService {

    /**
     * 生成二维码图片的 Data URL。
     *
     * @param content 二维码内容（如 TOTP 的 otpauth:// URI）
     * @param size    图片尺寸（正方形，单位像素）
     * @return Base64 编码的 PNG Data URL
     * @throws IllegalStateException 当二维码生成失败时
     */
    public String generateDataUrl(String content, int size) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size,
                    Map.of(EncodeHintType.MARGIN, 1));
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    image.setRGB(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
                }
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (WriterException | IOException e) {
            throw new IllegalStateException("二维码生成失败", e);
        }
    }
}

