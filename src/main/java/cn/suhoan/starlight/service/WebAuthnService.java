package cn.suhoan.starlight.service;

import cn.suhoan.starlight.entity.UserAccount;
import cn.suhoan.starlight.entity.UserCredential;
import cn.suhoan.starlight.repository.UserAccountRepository;
import cn.suhoan.starlight.repository.UserCredentialRepository;
import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.converter.AttestedCredentialDataConverter;
import com.webauthn4j.converter.exception.DataConversionException;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.credential.CredentialRecord;
import com.webauthn4j.credential.CredentialRecordImpl;
import com.webauthn4j.data.AuthenticationData;
import com.webauthn4j.data.AuthenticationParameters;
import com.webauthn4j.data.PublicKeyCredentialParameters;
import com.webauthn4j.data.PublicKeyCredentialType;
import com.webauthn4j.data.RegistrationData;
import com.webauthn4j.data.RegistrationParameters;
import com.webauthn4j.data.attestation.authenticator.AAGUID;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.authenticator.COSEKey;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.server.ServerProperty;
import com.webauthn4j.verifier.exception.VerificationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通行密钥登录服务
 *
 * @author suhoan
 */
@Service
@Transactional
public class WebAuthnService {

    private static final long OPERATION_TIMEOUT_MS = 300_000L;
    private static final long CEREMONY_TIMEOUT_MS = 300_000L;
    private static final SecureRandom CHALLENGE_RANDOM = new SecureRandom();
    private static final AAGUID EMPTY_AAGUID = new AAGUID(new byte[16]);
    private static final List<PublicKeyCredentialParameters> PUB_KEY_CRED_PARAMS = List.of(
            new PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY, COSEAlgorithmIdentifier.ES256),
            new PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY, COSEAlgorithmIdentifier.EdDSA),
            new PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY, COSEAlgorithmIdentifier.RS256)
    );
    private static final List<Map<String, Object>> PUB_KEY_CRED_PARAM_OPTIONS = List.of(
            credentialParameterOption(COSEAlgorithmIdentifier.ES256),
            credentialParameterOption(COSEAlgorithmIdentifier.EdDSA),
            credentialParameterOption(COSEAlgorithmIdentifier.RS256)
    );

    private record PendingOp(Object data, long expiresAt) {}
    private record PendingRegistration(String userId, String challenge) {}
    private record PendingAssertion(String challenge) {}
    private record ResolvedRelyingParty(String rpId, Origin origin) {}

    private final ConcurrentHashMap<String, PendingOp> pending = new ConcurrentHashMap<>();

    private final UserCredentialRepository userCredentialRepository;
    private final UserAccountRepository userAccountRepository;
    private final SettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final ObjectConverter objectConverter;
    private final WebAuthnManager webAuthnManager;
    private final AttestedCredentialDataConverter attestedCredentialDataConverter;

    public WebAuthnService(UserCredentialRepository userCredentialRepository,
                           UserAccountRepository userAccountRepository,
                           SettingsService settingsService,
                           ObjectMapper objectMapper) {
        this.userCredentialRepository = userCredentialRepository;
        this.userAccountRepository = userAccountRepository;
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
        this.objectConverter = new ObjectConverter();
        this.webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager(objectConverter);
        this.attestedCredentialDataConverter = new AttestedCredentialDataConverter(objectConverter);
    }

    /**
     * Start passkey registration for a logged-in user.
     * Returns a handle + JSON options for navigator.credentials.create()
     */
    public Map<String, Object> startRegistration(UserAccount user) {
        String challenge = generateChallenge();
        String handle = storePending(new PendingRegistration(user.getId(), challenge));
        List<Map<String, Object>> excludeCredentials = userCredentialRepository.findByUserId(user.getId()).stream()
                .map(this::toCredentialDescriptor)
                .toList();

        Map<String, Object> publicKey = new LinkedHashMap<>();
        publicKey.put("challenge", challenge);
        publicKey.put("rp", Map.of(
                "id", resolveRelyingParty().rpId(),
                "name", "Starlight"
        ));
        publicKey.put("user", Map.of(
                "id", encodeBase64Url(user.getId().getBytes(StandardCharsets.UTF_8)),
                "name", user.getUsername(),
                "displayName", user.getUsername()
        ));
        publicKey.put("pubKeyCredParams", PUB_KEY_CRED_PARAM_OPTIONS);
        publicKey.put("timeout", CEREMONY_TIMEOUT_MS);
        publicKey.put("attestation", "none");
        publicKey.put("authenticatorSelection", Map.of(
                "residentKey", "preferred",
                "requireResidentKey", false,
                "userVerification", "preferred"
        ));
        if (!excludeCredentials.isEmpty()) {
            publicKey.put("excludeCredentials", excludeCredentials);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("handle", handle);
        result.put("optionsJson", serializeOptionsJson(publicKey, "序列化注册选项失败"));
        return result;
    }

    /**
     * Finish passkey registration.
     */
    public void finishRegistration(UserAccount user, String handle, String responseJson, String nickname) {
        PendingRegistration pendingRegistration = retrievePending(handle, PendingRegistration.class);
        if (!pendingRegistration.userId().equals(user.getId())) {
            throw new IllegalArgumentException("当前会话与通行密钥注册请求不匹配");
        }
        try {
            RegistrationData registrationData = webAuthnManager.verifyRegistrationResponseJSON(
                    responseJson,
                    buildRegistrationParameters(pendingRegistration.challenge())
            );
            AttestedCredentialData attestedCredentialData = requireAttestedCredentialData(registrationData);
            String credentialId = encodeBase64Url(attestedCredentialData.getCredentialId());
            if (userCredentialRepository.findByCredentialId(credentialId).isPresent()) {
                throw new IllegalArgumentException("该通行密钥已注册");
            }

            UserCredential credential = new UserCredential();
            credential.setUserId(user.getId());
            credential.setCredentialId(credentialId);
            credential.setPublicKeyCose(encodeBase64Url(objectConverter.getCborMapper().writeValueAsBytes(attestedCredentialData.getCOSEKey())));
            credential.setSignatureCount(registrationData.getAttestationObject().getAuthenticatorData().getSignCount());
            credential.setNickname(nickname == null || nickname.isBlank() ? "通行密钥" : nickname.trim());
            userCredentialRepository.save(credential);
        } catch (VerificationException | DataConversionException e) {
            throw new IllegalArgumentException("通行密钥注册失败：" + e.getMessage());
        } catch (Exception e) {
            throw new IllegalArgumentException("通行密钥注册失败：" + e.getMessage());
        }
    }

    /**
     * Start passkey login (no auth needed).
     * Returns a handle + JSON options for navigator.credentials.get()
     */
    public Map<String, Object> startAssertion() {
        String challenge = generateChallenge();
        String handle = storePending(new PendingAssertion(challenge));

        Map<String, Object> publicKey = new LinkedHashMap<>();
        publicKey.put("challenge", challenge);
        publicKey.put("rpId", resolveRelyingParty().rpId());
        publicKey.put("timeout", CEREMONY_TIMEOUT_MS);
        publicKey.put("userVerification", "preferred");

        Map<String, Object> result = new HashMap<>();
        result.put("handle", handle);
        result.put("optionsJson", serializeOptionsJson(publicKey, "序列化认证选项失败"));
        return result;
    }

    /**
     * Finish passkey login. Returns the authenticated UserAccount.
     */
    public UserAccount finishAssertion(String handle, String responseJson) {
        PendingAssertion pendingAssertion = retrievePending(handle, PendingAssertion.class);
        try {
            AuthenticationData authenticationData = webAuthnManager.parseAuthenticationResponseJSON(responseJson);
            byte[] credentialIdBytes = authenticationData.getCredentialId();
            if (credentialIdBytes == null || credentialIdBytes.length == 0) {
                throw new IllegalArgumentException("未提供有效的通行密钥凭证 ID");
            }

            String credentialId = encodeBase64Url(credentialIdBytes);
            UserCredential storedCredential = userCredentialRepository.findByCredentialId(credentialId)
                    .orElseThrow(() -> new IllegalArgumentException("通行密钥不存在或已失效"));

            byte[] userHandle = authenticationData.getUserHandle();
            byte[] expectedUserHandle = storedCredential.getUserId().getBytes(StandardCharsets.UTF_8);
            if (userHandle != null && !Arrays.equals(userHandle, expectedUserHandle)) {
                throw new IllegalArgumentException("通行密钥所属用户与响应不匹配");
            }

            UserAccount userAccount = userAccountRepository.findById(storedCredential.getUserId())
                    .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

            CredentialRecord credentialRecord = toCredentialRecord(storedCredential);
            webAuthnManager.verify(
                    authenticationData,
                    buildAuthenticationParameters(pendingAssertion.challenge(), credentialRecord, credentialIdBytes)
            );

            storedCredential.setSignatureCount(credentialRecord.getCounter());
            userCredentialRepository.save(storedCredential);
            return userAccount;
        } catch (VerificationException | DataConversionException e) {
            throw new IllegalArgumentException("通行密钥验证失败：" + e.getMessage());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("通行密钥验证失败：" + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCredentials(String userId) {
        return userCredentialRepository.findByUserId(userId).stream()
                .map(cred -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", cred.getId());
                    map.put("nickname", cred.getNickname());
                    map.put("createdAt", cred.getCreatedAt());
                    return map;
                })
                .toList();
    }

    public void deleteCredential(String credentialEntityId, String userId) {
        UserCredential cred = userCredentialRepository.findById(credentialEntityId)
                .orElseThrow(() -> new IllegalArgumentException("通行密钥不存在"));
        if (!cred.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权删除该通行密钥");
        }
        userCredentialRepository.delete(cred);
    }

    // ──── Helpers ────

    private ResolvedRelyingParty resolveRelyingParty() {
        String siteUrl = settingsService.getShareBaseUrl();
        if (siteUrl.isBlank()) {
            throw new IllegalStateException("未配置站点 URL，无法使用通行密钥");
        }
        URI uri = URI.create(siteUrl);
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalStateException("站点 URL 必须为 HTTPS，才能使用通行密钥");
        }
        String rpId = uri.getHost();
        if (rpId == null || rpId.isBlank()) {
            throw new IllegalStateException("站点 URL 未包含有效域名，无法使用通行密钥");
        }
        String authority = uri.getAuthority();
        if (authority == null || authority.isBlank()) {
            throw new IllegalStateException("站点 URL 未包含有效站点来源，无法使用通行密钥");
        }
        return new ResolvedRelyingParty(rpId, new Origin(uri.getScheme() + "://" + authority));
    }

    private RegistrationParameters buildRegistrationParameters(String challenge) {
        return new RegistrationParameters(buildServerProperty(challenge), PUB_KEY_CRED_PARAMS, false, true);
    }

    private AuthenticationParameters buildAuthenticationParameters(String challenge,
                                                                   CredentialRecord credentialRecord,
                                                                   byte[] credentialId) {
        return new AuthenticationParameters(
                buildServerProperty(challenge),
                credentialRecord,
                List.of(credentialId),
                false,
                true
        );
    }

    private ServerProperty buildServerProperty(String challenge) {
        ResolvedRelyingParty relyingParty = resolveRelyingParty();
        return ServerProperty.builder()
                .origin(relyingParty.origin())
                .rpId(relyingParty.rpId())
                .challenge(new DefaultChallenge(challenge))
                .build();
    }

    private AttestedCredentialData requireAttestedCredentialData(RegistrationData registrationData) {
        if (registrationData.getAttestationObject() == null
                || registrationData.getAttestationObject().getAuthenticatorData() == null
                || registrationData.getAttestationObject().getAuthenticatorData().getAttestedCredentialData() == null) {
            throw new IllegalArgumentException("通行密钥注册响应缺少公钥数据");
        }
        return registrationData.getAttestationObject().getAuthenticatorData().getAttestedCredentialData();
    }

    private CredentialRecord toCredentialRecord(UserCredential credential) {
        try {
            byte[] credentialId = decodeBase64Url(credential.getCredentialId());
            byte[] publicKeyCose = decodeBase64Url(credential.getPublicKeyCose());
            COSEKey coseKey = objectConverter.getCborMapper().readValue(publicKeyCose, COSEKey.class);
            AttestedCredentialData attestedCredentialData = new AttestedCredentialData(EMPTY_AAGUID, credentialId, coseKey);
            return new CredentialRecordImpl(
                    null,
                    null,
                    null,
                    null,
                    credential.getSignatureCount(),
                    attestedCredentialData,
                    null,
                    null,
                    null,
                    null
            );
        } catch (Exception e) {
            throw new IllegalArgumentException("通行密钥数据损坏，无法完成验证：" + e.getMessage(), e);
        }
    }

    private Map<String, Object> toCredentialDescriptor(UserCredential credential) {
        return Map.of(
                "type", "public-key",
                "id", credential.getCredentialId()
        );
    }

    private String serializeOptionsJson(Map<String, Object> publicKeyOptions, String errorMessage) {
        try {
            return objectMapper.writeValueAsString(publicKeyOptions);
        } catch (Exception e) {
            throw new IllegalStateException(errorMessage, e);
        }
    }

    private static Map<String, Object> credentialParameterOption(COSEAlgorithmIdentifier algorithmIdentifier) {
        return Map.of(
                "type", "public-key",
                "alg", algorithmIdentifier.getValue()
        );
    }

    private static String generateChallenge() {
        byte[] bytes = new byte[32];
        CHALLENGE_RANDOM.nextBytes(bytes);
        return encodeBase64Url(bytes);
    }

    private static String encodeBase64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static byte[] decodeBase64Url(String value) {
        try {
            return Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Base64Url 解码失败: " + e.getMessage(), e);
        }
    }

    private String storePending(Object data) {
        cleanExpired();
        String handle = UUID.randomUUID().toString();
        pending.put(handle, new PendingOp(data, System.currentTimeMillis() + OPERATION_TIMEOUT_MS));
        return handle;
    }

    @SuppressWarnings("unchecked")
    private <T> T retrievePending(String handle, Class<T> type) {
        if (handle == null || handle.isBlank()) {
            throw new IllegalArgumentException("请求缺少有效的操作句柄");
        }
        PendingOp op = pending.remove(handle);
        if (op == null || op.expiresAt() < System.currentTimeMillis()) {
            throw new IllegalArgumentException("操作已过期，请重试");
        }
        if (!type.isInstance(op.data())) {
            throw new IllegalArgumentException("通行密钥操作类型不匹配，请重试");
        }
        return (T) op.data();
    }

    private void cleanExpired() {
        long now = System.currentTimeMillis();
        pending.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
    }
}

