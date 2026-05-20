package cn.suhoan.starlight.service.asset;

import java.io.InputStream;

public record AssetStream(InputStream inputStream, long sizeBytes, String contentType) {
}
