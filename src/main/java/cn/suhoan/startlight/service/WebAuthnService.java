package cn.suhoan.startlight.service;

import cn.suhoan.startlight.entity.UserAccount;
import cn.suhoan.startlight.entity.UserCredential;
import cn.suhoan.startlight.repository.UserAccountRepository;
import cn.suhoan.startlight.repository.UserCredentialRepository;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import com.yubico.webauthn.data.ResidentKeyRequirement;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.data.UserVerificationRequirement;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Transactional
public class WebAuthnService implements CredentialRepository {

    private record PendingOp(Object data, long expiresAt) {}

    private final ConcurrentHashMap<String, PendingOp> pending = new ConcurrentHashMap<>();

    private final UserCredentialRepository userCredentialRepository;
    private final UserAccountRepository userAccountRepository;
    private final SettingsService settingsService;

    public WebAuthnService(UserCredentialRepository userCredentialRepository,
                           UserAccountRepository userAccountRepository,
                           SettingsService settingsService) {
        this.userCredentialRepository = userCredentialRepository;
        this.userAccountRepository = userAccountRepository;
        this.settingsService = settingsService;
    }

    /**
     * Start passkey registration for a logged-in user.
     * Returns a handle + JSON options for navigator.credentials.create()
     */
    public Map<String, Object> startRegistration(UserAccount user) {
        RelyingParty rp = buildRelyingParty();
        PublicKeyCredentialCreationOptions options = rp.startRegistration(
                StartRegistrationOptions.builder()
                        .user(UserIdentity.builder()
                                .name(user.getUsername())
                                .displayName(user.getUsername())
                                .id(new ByteArray(user.getId().getBytes(StandardCharsets.UTF_8)))
                                .build())
                        .authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                                .residentKey(ResidentKeyRequirement.PREFERRED)
                                .userVerification(UserVerificationRequirement.PREFERRED)
                                .build())
                        .build());

        String handle = storePending(options);
        Map<String, Object> result = new HashMap<>();
        result.put("handle", handle);
        try {
            result.put("optionsJson", options.toCredentialsCreateJson());
        } catch (Exception e) {
            throw new IllegalStateException("序列化注册选项失败", e);
        }
        return result;
    }

    /**
     * Finish passkey registration.
     */
    public void finishRegistration(UserAccount user, String handle, String responseJson, String nickname) {
        PublicKeyCredentialCreationOptions options = retrievePending(handle, PublicKeyCredentialCreationOptions.class);
        RelyingParty rp = buildRelyingParty();

        try {
            RegistrationResult result = rp.finishRegistration(
                    FinishRegistrationOptions.builder()
                            .request(options)
                            .response(PublicKeyCredential.parseRegistrationResponseJson(responseJson))
                            .build());

            UserCredential credential = new UserCredential();
            credential.setUserId(user.getId());
            credential.setCredentialId(result.getKeyId().getId().getBase64Url());
            credential.setPublicKeyCose(result.getPublicKeyCose().getBase64Url());
            credential.setSignatureCount(result.getSignatureCount());
            credential.setNickname(nickname == null || nickname.isBlank() ? "通行密钥" : nickname.trim());
            userCredentialRepository.save(credential);
        } catch (RegistrationFailedException e) {
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
        RelyingParty rp = buildRelyingParty();
        AssertionRequest request = rp.startAssertion(
                StartAssertionOptions.builder()
                        .userVerification(UserVerificationRequirement.PREFERRED)
                        .build());

        String handle = storePending(request);
        Map<String, Object> result = new HashMap<>();
        result.put("handle", handle);
        try {
            result.put("optionsJson", request.toCredentialsGetJson());
        } catch (Exception e) {
            throw new IllegalStateException("序列化认证选项失败", e);
        }
        return result;
    }

    /**
     * Finish passkey login. Returns the authenticated UserAccount.
     */
    public UserAccount finishAssertion(String handle, String responseJson) {
        AssertionRequest request = retrievePending(handle, AssertionRequest.class);
        RelyingParty rp = buildRelyingParty();

        try {
            AssertionResult result = rp.finishAssertion(
                    FinishAssertionOptions.builder()
                            .request(request)
                            .response(PublicKeyCredential.parseAssertionResponseJson(responseJson))
                            .build());

            if (!result.isSuccess()) {
                throw new IllegalArgumentException("通行密钥验证失败");
            }

            // Update signature count
            String credIdBase64 = result.getCredential().getCredentialId().getBase64Url();
            userCredentialRepository.findByCredentialId(credIdBase64).ifPresent(cred -> {
                cred.setSignatureCount(result.getSignatureCount());
                userCredentialRepository.save(cred);
            });

            String userId = new String(result.getCredential().getUserHandle().getBytes(), StandardCharsets.UTF_8);
            return userAccountRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        } catch (AssertionFailedException e) {
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

    // ──── CredentialRepository interface ────

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        return userCredentialRepository.findByUserId(username).stream()
                .map(cred -> PublicKeyCredentialDescriptor.builder()
                        .id(decodeBase64Url(cred.getCredentialId()))
                        .build())
                .collect(Collectors.toSet());
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        return userAccountRepository.findById(username)
                .map(u -> new ByteArray(u.getId().getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        String userId = new String(userHandle.getBytes(), StandardCharsets.UTF_8);
        return userAccountRepository.findById(userId).map(UserAccount::getId);
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
        String userId = new String(userHandle.getBytes(), StandardCharsets.UTF_8);
        String credIdBase64 = credentialId.getBase64Url();
        return userCredentialRepository.findByUserId(userId).stream()
                .filter(c -> c.getCredentialId().equals(credIdBase64))
                .findFirst()
                .map(this::toRegisteredCredential);
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        String credIdBase64 = credentialId.getBase64Url();
        return new HashSet<>(userCredentialRepository.findAllByCredentialId(credIdBase64).stream()
                .map(this::toRegisteredCredential)
                .toList());
    }

    // ──── Helpers ────

    private RelyingParty buildRelyingParty() {
        String siteUrl = settingsService.getShareBaseUrl();
        if (siteUrl.isBlank()) {
            throw new IllegalStateException("未配置站点 URL，无法使用通行密钥");
        }
        URI uri = URI.create(siteUrl);
        String rpId = uri.getHost();
        String origin = siteUrl.endsWith("/") ? siteUrl.substring(0, siteUrl.length() - 1) : siteUrl;

        return RelyingParty.builder()
                .identity(RelyingPartyIdentity.builder()
                        .id(rpId)
                        .name("Starlight")
                        .build())
                .credentialRepository(this)
                .origins(Set.of(origin))
                .build();
    }

    private RegisteredCredential toRegisteredCredential(UserCredential cred) {
        return RegisteredCredential.builder()
                .credentialId(decodeBase64Url(cred.getCredentialId()))
                .userHandle(new ByteArray(cred.getUserId().getBytes(StandardCharsets.UTF_8)))
                .publicKeyCose(decodeBase64Url(cred.getPublicKeyCose()))
                .signatureCount(cred.getSignatureCount())
                .build();
    }

    /** Wrap ByteArray.fromBase64Url which throws a checked Base64UrlException. */
    private static ByteArray decodeBase64Url(String value) {
        try {
            return ByteArray.fromBase64Url(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Base64Url 解码失败: " + e.getMessage(), e);
        }
    }

    private String storePending(Object data) {
        cleanExpired();
        String handle = UUID.randomUUID().toString();
        pending.put(handle, new PendingOp(data, System.currentTimeMillis() + 300_000));
        return handle;
    }

    @SuppressWarnings("unchecked")
    private <T> T retrievePending(String handle, Class<T> type) {
        PendingOp op = pending.remove(handle);
        if (op == null || op.expiresAt() < System.currentTimeMillis()) {
            throw new IllegalArgumentException("操作已过期，请重试");
        }
        return (T) op.data();
    }

    private void cleanExpired() {
        long now = System.currentTimeMillis();
        pending.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
    }
}

