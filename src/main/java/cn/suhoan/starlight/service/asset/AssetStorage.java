package cn.suhoan.starlight.service.asset;

import java.io.InputStream;

public interface AssetStorage {

    String provider();

    StoredAsset put(String objectKey, InputStream inputStream, long sizeBytes, String contentType);

    AssetStream open(String objectKey);

    void delete(String objectKey);
}
