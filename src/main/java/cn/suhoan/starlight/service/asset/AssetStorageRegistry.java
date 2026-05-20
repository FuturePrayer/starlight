package cn.suhoan.starlight.service.asset;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class AssetStorageRegistry {

    private final Map<String, AssetStorage> storages;
    private final S3AssetStorage s3AssetStorage;

    public AssetStorageRegistry(List<AssetStorage> storages, S3AssetStorage s3AssetStorage) {
        this.storages = storages.stream().collect(Collectors.toMap(AssetStorage::provider, storage -> storage));
        this.s3AssetStorage = s3AssetStorage;
    }

    public AssetStorage get(String provider) {
        AssetStorage storage = storages.get(provider);
        if (storage == null) {
            throw new IllegalArgumentException("不支持的图片存储后端: " + provider);
        }
        return storage;
    }

    public boolean isS3Available() {
        return s3AssetStorage.isAvailable();
    }
}
