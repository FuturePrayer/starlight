package cn.suhoan.starlight.service.asset;

import cn.suhoan.starlight.config.StarlightProperties;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Component
public class LocalAssetStorage implements AssetStorage {

    private final StarlightProperties starlightProperties;

    public LocalAssetStorage(StarlightProperties starlightProperties) {
        this.starlightProperties = starlightProperties;
    }

    @Override
    public String provider() {
        return "local";
    }

    @Override
    public StoredAsset put(String objectKey, InputStream inputStream, long sizeBytes, String contentType) {
        try {
            Path target = resolve(objectKey);
            Files.createDirectories(target.getParent());
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            return new StoredAsset(objectKey);
        } catch (Exception exception) {
            throw new IllegalStateException("保存本地图片失败", exception);
        }
    }

    @Override
    public AssetStream open(String objectKey) {
        try {
            Path target = resolve(objectKey);
            return new AssetStream(Files.newInputStream(target), Files.size(target), Files.probeContentType(target));
        } catch (Exception exception) {
            throw new IllegalStateException("读取本地图片失败", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            Files.deleteIfExists(resolve(objectKey));
        } catch (Exception exception) {
            throw new IllegalStateException("删除本地图片失败", exception);
        }
    }

    private Path resolve(String objectKey) {
        Path root = Path.of(starlightProperties.getAssets().getLocal().getRoot()).toAbsolutePath().normalize();
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("图片路径非法");
        }
        return target;
    }
}
