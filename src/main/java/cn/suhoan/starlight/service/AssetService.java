package cn.suhoan.starlight.service;

import cn.suhoan.starlight.config.StarlightProperties;
import cn.suhoan.starlight.entity.Asset;
import cn.suhoan.starlight.entity.Note;
import cn.suhoan.starlight.entity.NoteAssetRef;
import cn.suhoan.starlight.entity.UserAccount;
import cn.suhoan.starlight.repository.AssetRepository;
import cn.suhoan.starlight.repository.NoteAssetRefRepository;
import cn.suhoan.starlight.repository.NoteRepository;
import cn.suhoan.starlight.service.asset.AssetStorage;
import cn.suhoan.starlight.service.asset.AssetStorageRegistry;
import cn.suhoan.starlight.service.asset.AssetStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional
public class AssetService {

    private static final Pattern ASSET_URL_PATTERN = Pattern.compile("/api/assets/([0-9a-fA-F-]{36})/content(?:\\?[^\\s)\"']*)?");
    private final AssetRepository assetRepository;
    private final NoteAssetRefRepository noteAssetRefRepository;
    private final NoteRepository noteRepository;
    private final SettingsService settingsService;
    private final StarlightProperties starlightProperties;
    private final AssetStorageRegistry storageRegistry;

    public AssetService(AssetRepository assetRepository,
                        NoteAssetRefRepository noteAssetRefRepository,
                        NoteRepository noteRepository,
                        SettingsService settingsService,
                        StarlightProperties starlightProperties,
                        AssetStorageRegistry storageRegistry) {
        this.assetRepository = assetRepository;
        this.noteAssetRefRepository = noteAssetRefRepository;
        this.noteRepository = noteRepository;
        this.settingsService = settingsService;
        this.starlightProperties = starlightProperties;
        this.storageRegistry = storageRegistry;
    }

    public Map<String, Object> getSettings(UserAccount userAccount) {
        Map<String, Object> data = new LinkedHashMap<>();
        String provider = resolveWritableProvider();
        data.put("uploadEnabled", settingsService.isAssetUploadEnabled());
        data.put("storageProvider", provider);
        data.put("configuredStorageProvider", settingsService.getAssetStorageProvider());
        data.put("s3Available", storageRegistry.isS3Available());
        data.put("maxFileSize", maxFileSize());
        data.put("allowedTypes", allowedTypes());
        data.put("userQuotaBytes", settingsService.getAssetUserQuotaBytes());
        data.put("cleanupGraceHours", settingsService.getAssetCleanupGraceHours());
        data.put("refererCheckEnabled", refererCheckEnabled());
        data.put("siteUrl", settingsService.getShareBaseUrl());
        data.put("admin", userAccount.isAdminFlag());
        return data;
    }

    public Map<String, Object> getUsage(UserAccount userAccount) {
        return usageForOwner(userAccount.getId(), userAccount.isAdminFlag());
    }

    public Map<String, Object> getAdminUsage(UserAccount admin, String scope) {
        if (!admin.isAdminFlag()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要管理员权限");
        }
        if ("all".equalsIgnoreCase(scope)) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("scope", "all");
            data.put("usedBytes", assetRepository.sumSizeBytesAll());
            data.put("unreferencedBytes", assetRepository.sumUnreferencedSizeBytesAll());
            data.put("quotaBytes", null);
            data.put("admin", true);
            return data;
        }
        return usageForOwner(admin.getId(), true);
    }

    public Map<String, Object> uploadImage(UserAccount owner, MultipartFile file, String noteId) {
        if (!settingsService.isAssetUploadEnabled()) {
            throw new IllegalArgumentException("管理员尚未开启图片上传功能");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的图片");
        }
        if (file.getSize() > maxFileSize()) {
            throw new IllegalArgumentException("图片大小超过单文件上限");
        }

        try {
            byte[] bytes = file.getBytes();
            String contentType = detectContentType(bytes, file.getContentType());
            if (!allowedTypes().contains(contentType)) {
                throw new IllegalArgumentException("不支持的图片类型: " + contentType);
            }
            if (!owner.isAdminFlag()) {
                long quota = settingsService.getAssetUserQuotaBytes();
                long used = assetRepository.sumSizeBytesByOwnerId(owner.getId());
                if (quota > 0 && used + bytes.length > quota) {
                    throw new IllegalArgumentException("图片容量已超出管理员设置的上限");
                }
            }

            String provider = resolveWritableProvider();
            String assetId = UUID.randomUUID().toString();
            String objectKey = buildObjectKey(owner.getId(), assetId, contentType);
            AssetStorage storage = storageRegistry.get(provider);
            storage.put(objectKey, new ByteArrayInputStream(bytes), bytes.length, contentType);

            Asset asset = new Asset();
            asset.setId(assetId);
            asset.setOwner(owner);
            asset.setOriginalFilename(safeFilename(file.getOriginalFilename()));
            asset.setContentType(contentType);
            asset.setSizeBytes(bytes.length);
            asset.setSha256(sha256(bytes));
            asset.setStorageProvider(provider);
            asset.setObjectKey(objectKey);
            asset.setReadToken(randomToken());
            asset.setUnreferencedSince(LocalDateTime.now());
            asset = assetRepository.save(asset);
            maybeAttachToNote(owner, asset, noteId);

            return toUploadResponse(asset);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("图片上传失败", exception);
        }
    }

    @Transactional(readOnly = true)
    public Asset getReadableAsset(String assetId, String token, UserAccount currentUser, String referer) {
        Asset asset = assetRepository.findByIdAndDeletedAtIsNull(assetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "图片不存在"));
        boolean owner = currentUser != null && asset.getOwner().getId().equals(currentUser.getId());
        boolean tokenOk = token != null && token.equals(asset.getReadToken());
        if (!owner && !tokenOk) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "没有图片访问权限");
        }
        validateReferer(referer);
        return asset;
    }

    public AssetStream openContent(Asset asset) {
        return storageRegistry.get(asset.getStorageProvider()).open(asset.getObjectKey());
    }

    @Transactional(readOnly = true)
    public Map<String, Asset> findOwnedAssetsByIds(String ownerId, Set<String> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return Map.of();
        }
        return assetRepository.findByIdInAndOwnerIdAndDeletedAtIsNull(assetIds, ownerId).stream()
                .collect(java.util.stream.Collectors.toMap(Asset::getId, asset -> asset));
    }

    public Map<String, Object> importImageAsset(UserAccount owner,
                                                String filename,
                                                byte[] bytes,
                                                String contentType,
                                                String noteId) {
        return uploadImage(owner, new InMemoryMultipartFile(filename, bytes, contentType), noteId);
    }

    public void deleteAsset(UserAccount owner, String assetId) {
        Asset asset = assetRepository.findByIdAndOwnerIdAndDeletedAtIsNull(assetId, owner.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "图片不存在"));
        noteAssetRefRepository.deleteByAssetId(asset.getId());
        asset.setDeletedAt(LocalDateTime.now());
        assetRepository.save(asset);
        storageRegistry.get(asset.getStorageProvider()).delete(asset.getObjectKey());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAssetMeta(UserAccount owner, String assetId) {
        Asset asset = assetRepository.findByIdAndOwnerIdAndDeletedAtIsNull(assetId, owner.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "图片不存在"));
        return toMeta(asset);
    }

    public CleanupResult cleanupUnreferenced(UserAccount userAccount, boolean dryRun, String scope) {
        boolean all = userAccount.isAdminFlag() && "all".equalsIgnoreCase(scope);
        LocalDateTime cutoff = LocalDateTime.now().minusHours(Math.max(settingsService.getAssetCleanupGraceHours(), 0));
        List<Asset> candidates = all
                ? assetRepository.findCleanupCandidatesAll(cutoff)
                : assetRepository.findCleanupCandidatesByOwnerId(userAccount.getId(), cutoff);
        long bytes = candidates.stream().mapToLong(Asset::getSizeBytes).sum();
        if (!dryRun) {
            noteAssetRefRepository.deleteByAssetIdIn(candidates.stream().map(Asset::getId).toList());
            for (Asset asset : candidates) {
                asset.setDeletedAt(LocalDateTime.now());
                storageRegistry.get(asset.getStorageProvider()).delete(asset.getObjectKey());
            }
            assetRepository.saveAll(candidates);
        }
        return new CleanupResult(candidates.size(), bytes, dryRun, all ? "all" : "self");
    }

    public void rebuildNoteReferences(Note note) {
        if (note == null || note.getOwner() == null) {
            return;
        }
        String noteId = note.getId();
        String ownerId = note.getOwner().getId();
        Set<String> oldAssetIds = noteAssetRefRepository.findByNoteId(noteId).stream()
                .map(ref -> ref.getAsset().getId())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> nextAssetIds = extractAssetIds(note.getMarkdownContent());
        noteAssetRefRepository.deleteByNoteId(noteId);

        List<Asset> assets = nextAssetIds.isEmpty()
                ? List.of()
                : assetRepository.findByIdInAndOwnerIdAndDeletedAtIsNull(nextAssetIds, ownerId);
        LocalDateTime now = LocalDateTime.now();
        List<NoteAssetRef> refs = assets.stream().map(asset -> {
            asset.setUnreferencedSince(null);
            NoteAssetRef ref = new NoteAssetRef();
            ref.setNote(note);
            ref.setAsset(asset);
            ref.setOwner(note.getOwner());
            ref.setReferencedAt(now);
            return ref;
        }).toList();
        noteAssetRefRepository.saveAll(refs);

        Set<String> retained = assets.stream()
                .map(Asset::getId)
                .collect(java.util.stream.Collectors.toSet());
        oldAssetIds.removeAll(retained);
        markUnreferenced(oldAssetIds, now);
    }

    public void removeNoteReferences(List<Note> notes) {
        if (notes == null || notes.isEmpty()) {
            return;
        }
        Set<String> assetIds = new LinkedHashSet<>();
        for (Note note : notes) {
            for (NoteAssetRef ref : noteAssetRefRepository.findByNoteId(note.getId())) {
                assetIds.add(ref.getAsset().getId());
            }
        }
        noteAssetRefRepository.deleteByNoteIdIn(notes.stream().map(Note::getId).toList());
        markUnreferenced(assetIds, LocalDateTime.now());
    }

    private void markUnreferenced(Set<String> assetIds, LocalDateTime now) {
        if (assetIds.isEmpty()) {
            return;
        }
        List<Asset> assets = assetRepository.findAllById(assetIds);
        for (Asset asset : assets) {
            if (asset.getDeletedAt() == null && noteAssetRefRepository.countByAssetId(asset.getId()) == 0) {
                asset.setUnreferencedSince(now);
            }
        }
        assetRepository.saveAll(assets);
    }

    private Map<String, Object> usageForOwner(String ownerId, boolean admin) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("scope", "self");
        data.put("usedBytes", assetRepository.sumSizeBytesByOwnerId(ownerId));
        data.put("unreferencedBytes", assetRepository.sumUnreferencedSizeBytesByOwnerId(ownerId));
        data.put("quotaBytes", admin ? null : settingsService.getAssetUserQuotaBytes());
        data.put("admin", admin);
        return data;
    }

    private void maybeAttachToNote(UserAccount owner, Asset asset, String noteId) {
        if (noteId == null || noteId.isBlank()) {
            return;
        }
        noteRepository.findByIdAndOwnerIdAndDeletedAtIsNull(noteId.trim(), owner.getId()).ifPresent(note -> {
            asset.setUnreferencedSince(null);
            NoteAssetRef ref = new NoteAssetRef();
            ref.setNote(note);
            ref.setAsset(asset);
            ref.setOwner(owner);
            ref.setReferencedAt(LocalDateTime.now());
            noteAssetRefRepository.save(ref);
        });
    }

    private Set<String> extractAssetIds(String markdown) {
        Set<String> ids = new LinkedHashSet<>();
        Matcher matcher = ASSET_URL_PATTERN.matcher(markdown == null ? "" : markdown);
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        return ids;
    }

    public static Set<String> extractAssetIdsFromMarkdown(String markdown) {
        Set<String> ids = new LinkedHashSet<>();
        Matcher matcher = ASSET_URL_PATTERN.matcher(markdown == null ? "" : markdown);
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        return ids;
    }

    private String resolveWritableProvider() {
        String provider = settingsService.getAssetStorageProvider();
        if ("s3".equals(provider) && !storageRegistry.isS3Available()) {
            return "local";
        }
        return provider;
    }

    private void validateReferer(String referer) {
        if (!refererCheckEnabled()) {
            return;
        }
        try {
            URI site = URI.create(settingsService.getShareBaseUrl());
            URI source = URI.create(referer == null ? "" : referer);
            String siteOrigin = originOf(site);
            String sourceOrigin = originOf(source);
            if (!siteOrigin.equalsIgnoreCase(sourceOrigin)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "图片访问来源不受信任");
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "图片访问来源不受信任");
        }
    }

    private boolean refererCheckEnabled() {
        String siteUrl = settingsService.getShareBaseUrl();
        return siteUrl != null && !siteUrl.isBlank();
    }

    private static String originOf(URI uri) {
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalArgumentException("URL 缺少来源信息");
        }
        int port = uri.getPort();
        return uri.getScheme() + "://" + uri.getHost() + (port < 0 ? "" : ":" + port);
    }

    private Map<String, Object> toUploadResponse(Asset asset) {
        Map<String, Object> data = toMeta(asset);
        String url = contentUrl(asset);
        data.put("url", url);
        data.put("markdown", "![" + asset.getOriginalFilename() + "](" + url + ")");
        return data;
    }

    private Map<String, Object> toMeta(Asset asset) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", asset.getId());
        data.put("originalFilename", asset.getOriginalFilename());
        data.put("contentType", asset.getContentType());
        data.put("sizeBytes", asset.getSizeBytes());
        data.put("storageProvider", asset.getStorageProvider());
        data.put("createdAt", asset.getCreatedAt());
        data.put("unreferencedSince", asset.getUnreferencedSince());
        data.put("url", contentUrl(asset));
        return data;
    }

    private String contentUrl(Asset asset) {
        return "/api/assets/" + asset.getId() + "/content?token=" + asset.getReadToken();
    }

    private String buildObjectKey(String ownerId, String assetId, String contentType) {
        LocalDate date = LocalDate.now();
        return ownerId + "/" + date.getYear() + "/" + String.format("%02d", date.getMonthValue())
                + "/" + assetId + extensionFor(contentType);
    }

    private String detectContentType(byte[] bytes, String fallback) {
        String signature = HexFormat.of().formatHex(bytes, 0, Math.min(bytes.length, 12)).toLowerCase(Locale.ROOT);
        if (signature.startsWith("89504e47")) return "image/png";
        if (signature.startsWith("ffd8ff")) return "image/jpeg";
        if (signature.startsWith("47494638")) return "image/gif";
        if (signature.length() >= 24 && signature.startsWith("52494646") && signature.substring(16, 24).equals("57454250")) {
            return "image/webp";
        }
        if (signature.length() >= 24 && signature.substring(8, 16).equals("66747970")) {
            return "image/avif";
        }
        return fallback == null ? "application/octet-stream" : fallback.toLowerCase(Locale.ROOT);
    }

    private Set<String> allowedTypes() {
        return java.util.Arrays.stream(starlightProperties.getAssets().getAllowedTypes().split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private long maxFileSize() {
        return Math.max(starlightProperties.getAssets().getMaxFileSize(), 1);
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            case "image/avif" -> ".avif";
            default -> ".bin";
        };
    }

    private String safeFilename(String filename) {
        String value = filename == null ? "image" : filename.trim();
        value = value.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        if (value.isBlank()) {
            return "image";
        }
        return value.length() > 255 ? value.substring(0, 255) : value;
    }

    private String sha256(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(bytes));
    }

    private String randomToken() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    public record CleanupResult(int count, long bytes, boolean dryRun, String scope) {
    }

    private static final class InMemoryMultipartFile implements MultipartFile {

        private final String filename;
        private final byte[] bytes;
        private final String contentType;

        private InMemoryMultipartFile(String filename, byte[] bytes, String contentType) {
            this.filename = filename == null || filename.isBlank() ? "image" : filename;
            this.bytes = bytes == null ? new byte[0] : bytes;
            this.contentType = contentType;
        }

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return filename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes.length;
        }

        @Override
        public byte[] getBytes() {
            return bytes;
        }

        @Override
        public java.io.InputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            java.nio.file.Files.write(dest.toPath(), bytes);
        }
    }
}
