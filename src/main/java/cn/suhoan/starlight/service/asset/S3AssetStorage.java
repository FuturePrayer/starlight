package cn.suhoan.starlight.service.asset;

import cn.suhoan.starlight.config.StarlightProperties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.InputStream;
import java.net.URI;

@Component
public class S3AssetStorage implements AssetStorage {

    private final StarlightProperties starlightProperties;

    public S3AssetStorage(StarlightProperties starlightProperties) {
        this.starlightProperties = starlightProperties;
    }

    @Override
    public String provider() {
        return "s3";
    }

    public boolean isAvailable() {
        StarlightProperties.Assets.S3 s3 = starlightProperties.getAssets().getS3();
        return hasText(s3.getBucket()) && hasText(s3.getAccessKey()) && hasText(s3.getSecretKey());
    }

    @Override
    public StoredAsset put(String objectKey, InputStream inputStream, long sizeBytes, String contentType) {
        try (S3Client client = createClient()) {
            String key = buildKey(objectKey);
            client.putObject(request -> request
                            .bucket(config().getBucket())
                            .key(key)
                            .contentType(contentType)
                            .contentLength(sizeBytes),
                    RequestBody.fromInputStream(inputStream, sizeBytes));
            return new StoredAsset(objectKey);
        } catch (Exception exception) {
            throw new IllegalStateException("保存 S3 图片失败", exception);
        }
    }

    @Override
    public AssetStream open(String objectKey) {
        try {
            S3Client client = createClient();
            ResponseInputStream<GetObjectResponse> stream = client.getObject(request -> request
                    .bucket(config().getBucket())
                    .key(buildKey(objectKey)));
            GetObjectResponse response = stream.response();
            return new AssetStream(new ClosingInputStream(stream, client), response.contentLength(), response.contentType());
        } catch (Exception exception) {
            throw new IllegalStateException("读取 S3 图片失败", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try (S3Client client = createClient()) {
            client.deleteObject(request -> request
                    .bucket(config().getBucket())
                    .key(buildKey(objectKey)));
        } catch (Exception exception) {
            throw new IllegalStateException("删除 S3 图片失败", exception);
        }
    }

    private S3Client createClient() {
        StarlightProperties.Assets.S3 s3 = config();
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(hasText(s3.getRegion()) ? s3.getRegion() : "us-east-1"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3.getAccessKey(), s3.getSecretKey())))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .forcePathStyle(s3.isPathStyleAccess());
        if (hasText(s3.getEndpoint())) {
            builder.endpointOverride(URI.create(s3.getEndpoint()));
        }
        return builder.build();
    }

    private String buildKey(String objectKey) {
        String prefix = trimSlashes(config().getPrefix());
        String key = trimSlashes(objectKey);
        return prefix.isBlank() ? key : prefix + "/" + key;
    }

    private StarlightProperties.Assets.S3 config() {
        return starlightProperties.getAssets().getS3();
    }

    private static String trimSlashes(String value) {
        return (value == null ? "" : value.trim()).replaceAll("^/+", "").replaceAll("/+$", "");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static class ClosingInputStream extends InputStream {

        private final ResponseInputStream<GetObjectResponse> delegate;
        private final S3Client client;

        private ClosingInputStream(ResponseInputStream<GetObjectResponse> delegate, S3Client client) {
            this.delegate = delegate;
            this.client = client;
        }

        @Override
        public int read() throws java.io.IOException {
            return delegate.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws java.io.IOException {
            return delegate.read(b, off, len);
        }

        @Override
        public void close() throws java.io.IOException {
            try {
                delegate.close();
            } finally {
                client.close();
            }
        }
    }
}
