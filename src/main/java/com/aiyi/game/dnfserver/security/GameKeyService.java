package com.aiyi.game.dnfserver.security;

import com.aiyi.game.dnfserver.channel.ChannelService;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Cipher;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** The platform and game private keys are updated together; channels verify with the paired public key. */
@Service
public class GameKeyService {
    private static final Logger LOG = LoggerFactory.getLogger(GameKeyService.class);
    private static final int MAX_KEY_BYTES = 64 * 1024;
    private final Path gameDir;
    private final Path dataDir;
    private final Path configuredKey;
    private final Path configuredGatewayKey;
    private final ChannelService channels;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);

    public GameKeyService(ChannelService channels,
                          @Value("${dnf.channels.game-dir:/home/neople/game}") String gameDir,
                          @Value("${dnf.channels.data-dir:/data}") String dataDir,
                          @Value("${dnf.private-key-path:}") String configuredKey,
                          @Value("${dnf.keys.gateway-private-key-path:}") String gatewayKey) {
        this.channels = channels;
        this.gameDir = Paths.get(gameDir).toAbsolutePath().normalize();
        this.dataDir = Paths.get(dataDir).toAbsolutePath().normalize();
        this.configuredKey = configuredKey == null || configuredKey.trim().isEmpty() ? null : Paths.get(configuredKey).toAbsolutePath().normalize();
        this.configuredGatewayKey = gatewayKey == null || gatewayKey.trim().isEmpty() ? null : Paths.get(gatewayKey).toAbsolutePath().normalize();
    }

    public byte[] encryptForGame(byte[] plaintext) {
        lock.readLock().lock();
        try {
            RSAPrivateCrtKey key = readPrivate(signingPath());
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return cipher.doFinal(plaintext);
        } catch (Exception e) {
            LOG.warn("无法读取游戏登录私钥：{}", e.getClass().getSimpleName());
            throw new IllegalStateException("无法读取游戏登录私钥，请检查游戏服务密钥配置", e);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Map<String, Object> status() {
        boolean gameAvailable = channels.canReloadPvf();
        Boolean gatewayRegistered = gameAvailable ? channels.legacyGatewayRegistered() : null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("gameAvailable", gameAvailable);
        result.put("canManage", gameAvailable && Files.isDirectory(dataDir) && Files.isDirectory(gameDir));
        result.put("available", false);
        result.put("matchesGame", false);
        lock.readLock().lock();
        try {
            Path keyPath = signingPath();
            result.put("source", keyPath.toString());
            if (!Files.isRegularFile(keyPath)) {
                result.put("message", "未找到登录私钥，请配置游戏密钥或上传公私钥对");
                return result;
            }
            RSAPrivateCrtKey privateKey = readPrivate(keyPath);
            PublicKey publicKey = publicKey(privateKey);
            result.put("available", true);
            result.put("bits", privateKey.getModulus().bitLength());
            result.put("fingerprint", fingerprint(publicKey));
            if (Files.isRegularFile(gameDir.resolve("df_game_r"))) {
                boolean runtimeMatches = publicEquals(gameDir.resolve("publickey.pem"), publicKey);
                boolean persistentMatches = publicEquals(dataDir.resolve("publickey.pem"), publicKey);
                boolean privateMatches = privateEquals(dataDir.resolve("privatekey.pem"), publicKey);
                boolean gatewayMatches = true;
                if (Boolean.TRUE.equals(gatewayRegistered)) {
                    try { gatewayMatches = samePublic(publicKey(readPrivate(gatewayKeyPath())), publicKey); }
                    catch (Exception e) { gatewayMatches = false; }
                }
                result.put("gatewayRegistered", gatewayRegistered);
                result.put("matchesGame", runtimeMatches && persistentMatches && privateMatches && gatewayMatches);
                if (!runtimeMatches || !persistentMatches || !privateMatches || !gatewayMatches) result.put("message", "游戏或原网关密钥未同步，请上传或重新生成公私钥对");
                else if (gameAvailable && gatewayRegistered == null) result.put("message", "无法检查原网关状态，请刷新后重试");
            } else {
                result.put("message", "未发现本地游戏服务，无法检查游戏公钥或在此更换密钥");
            }
        } catch (Exception e) {
            result.put("message", "私钥或游戏公钥无法读取，请检查密钥文件");
        } finally {
            lock.readLock().unlock();
        }
        return result;
    }

    public synchronized Map<String, Object> upload(MultipartFile privateFile, MultipartFile publicFile) throws Exception {
        if (privateFile == null || privateFile.isEmpty() || publicFile == null || publicFile.isEmpty()) {
            throw new IllegalArgumentException("请选择非空的 RSA 公钥和私钥文件");
        }
        if (privateFile.getSize() > MAX_KEY_BYTES || publicFile.getSize() > MAX_KEY_BYTES) {
            throw new IllegalArgumentException("公钥和私钥文件各不能超过 64 KB");
        }
        byte[] bytes = privateFile.getBytes();
        try {
            RSAPrivateCrtKey key;
            try {
                key = parsePrivate(bytes);
                validateKey(key);
                PublicKey suppliedPublic = parsePublic(publicFile.getBytes());
                if (!samePublic(publicKey(key), suppliedPublic)) {
                    throw new IllegalArgumentException("公钥与私钥不匹配，原密钥未更改");
                }
            }
            catch (IllegalArgumentException e) { throw e; }
            catch (Exception e) { throw new IllegalArgumentException("公钥或私钥格式无效，请上传匹配的 RSA 公私钥对", e); }
            return install(key);
        } finally { Arrays.fill(bytes, (byte) 0); }
    }

    public synchronized Map<String, Object> generate() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        return install((RSAPrivateCrtKey) pair.getPrivate());
    }

    private Map<String, Object> install(RSAPrivateCrtKey privateKey) throws Exception {
        validateKey(privateKey);
        PublicKey publicKey = publicKey(privateKey);
        byte[] privatePem = privatePem(privateKey);
        byte[] publicPem = pem("PUBLIC KEY", publicKey.getEncoded());
        try {
            Map<String, Object> result = channels.installKeyAndRestart(gatewayRegistered -> {
                List<FileReplacement> replacements = new ArrayList<>();
                Files.createDirectories(Paths.get("data"));
                add(replacements, dataDir.resolve("publickey.pem"), publicPem, false);
                add(replacements, gameDir.resolve("publickey.pem"), publicPem, false);
                if (gatewayRegistered) add(replacements, gatewayKeyPath(), privatePem, true);
                add(replacements, dataDir.resolve("privatekey.pem"), privatePem, true);
                add(replacements, Paths.get("data", "private.key").toAbsolutePath().normalize(), privatePem, true);
                lock.writeLock().lock();
                try { replaceFiles(replacements); }
                finally { lock.writeLock().unlock(); }
            });
            result.put("fingerprint", fingerprint(publicKey));
            result.put("bits", privateKey.getModulus().bitLength());
            return result;
        } finally {
            Arrays.fill(privatePem, (byte) 0);
        }
    }

    private Path signingPath() {
        Path webKey = Paths.get("data", "private.key").toAbsolutePath().normalize();
        Path gameKey = dataDir.resolve("privatekey.pem");
        if (Files.isRegularFile(gameDir.resolve("df_game_r"))) {
            return Files.isRegularFile(webKey) ? webKey : gameKey;
        }
        if (configuredKey != null) return configuredKey;
        if (Files.isRegularFile(webKey)) return webKey;
        return gameKey;
    }

    private Path gatewayKeyPath() {
        if (configuredGatewayKey != null) return configuredGatewayKey;
        if (gameDir.endsWith(Paths.get("home", "neople", "game"))) {
            return gameDir.getParent().getParent().getParent().resolve("root/privatekey.pem");
        }
        throw new IllegalStateException("发现原游戏网关，请通过 DNF_GATE_PRIVATE_KEY_PATH 配置其私钥位置后再更换");
    }

    private static RSAPrivateCrtKey readPrivate(Path path) throws Exception {
        long size = Files.size(path);
        if (size <= 0 || size > MAX_KEY_BYTES) throw new IOException("私钥文件大小无效");
        byte[] data = Files.readAllBytes(path);
        try { return parsePrivate(data); }
        finally { Arrays.fill(data, (byte) 0); }
    }

    private static RSAPrivateCrtKey parsePrivate(byte[] data) throws Exception {
        String text = new String(data, StandardCharsets.US_ASCII).trim();
        PrivateKey key;
        if (text.startsWith("-----BEGIN")) {
            try (PEMParser parser = new PEMParser(new StringReader(text))) {
                Object object = parser.readObject();
                JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
                if (object instanceof PEMKeyPair) key = converter.getKeyPair((PEMKeyPair) object).getPrivate();
                else if (object instanceof PrivateKeyInfo) key = converter.getPrivateKey((PrivateKeyInfo) object);
                else throw new IllegalArgumentException("请选择未加密的 RSA 私钥");
                if (parser.readObject() != null) throw new IllegalArgumentException("私钥文件包含多个密钥");
            }
        } else {
            byte[] encoded;
            if (text.matches("[A-Za-z0-9+/=\\s]+")) {
                try { encoded = Base64.getMimeDecoder().decode(text); }
                catch (IllegalArgumentException invalidBase64) { throw new IllegalArgumentException("私钥编码无效", invalidBase64); }
            } else encoded = data.clone();
            try {
                KeyFactory factory = KeyFactory.getInstance("RSA");
                try { key = factory.generatePrivate(new PKCS8EncodedKeySpec(encoded)); }
                catch (Exception invalidPkcs8) {
                    org.bouncycastle.asn1.pkcs.RSAPrivateKey rsa = org.bouncycastle.asn1.pkcs.RSAPrivateKey.getInstance(ASN1Primitive.fromByteArray(encoded));
                    key = factory.generatePrivate(new RSAPrivateCrtKeySpec(rsa.getModulus(), rsa.getPublicExponent(), rsa.getPrivateExponent(),
                            rsa.getPrime1(), rsa.getPrime2(), rsa.getExponent1(), rsa.getExponent2(), rsa.getCoefficient()));
                }
            } finally { Arrays.fill(encoded, (byte) 0); }
        }
        if (!(key instanceof RSAPrivateCrtKey)) throw new IllegalArgumentException("请选择包含完整参数的 RSA 私钥");
        return (RSAPrivateCrtKey) key;
    }

    private static PublicKey publicKey(RSAPrivateCrtKey key) throws Exception {
        return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(key.getModulus(), key.getPublicExponent()));
    }

    private static void validateKey(RSAPrivateCrtKey key) throws Exception {
        int bits = key.getModulus().bitLength();
        if (bits < 1024 || bits > 4096) throw new IllegalArgumentException("RSA 密钥长度需在 1024 至 4096 位之间");
        byte[] sample = new byte[]{0x42, 0x17, 0x25, 0x31};
        Cipher encrypt = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        encrypt.init(Cipher.ENCRYPT_MODE, key);
        Cipher decrypt = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        decrypt.init(Cipher.DECRYPT_MODE, publicKey(key));
        if (!Arrays.equals(sample, decrypt.doFinal(encrypt.doFinal(sample)))) throw new IllegalArgumentException("私钥参数校验失败");
    }

    private static byte[] privatePem(RSAPrivateCrtKey key) throws Exception {
        org.bouncycastle.asn1.pkcs.RSAPrivateKey rsa = new org.bouncycastle.asn1.pkcs.RSAPrivateKey(
                key.getModulus(), key.getPublicExponent(), key.getPrivateExponent(), key.getPrimeP(), key.getPrimeQ(),
                key.getPrimeExponentP(), key.getPrimeExponentQ(), key.getCrtCoefficient());
        return pem("RSA PRIVATE KEY", rsa.getEncoded());
    }

    private static byte[] pem(String label, byte[] encoded) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PemWriter writer = new PemWriter(new OutputStreamWriter(output, StandardCharsets.US_ASCII))) {
            writer.writeObject(new PemObject(label, encoded));
        }
        return output.toByteArray();
    }

    private static boolean publicEquals(Path file, PublicKey expected) {
        if (!Files.isRegularFile(file)) return false;
        try {
            if (Files.size(file) > MAX_KEY_BYTES) return false;
            return samePublic(parsePublic(Files.readAllBytes(file)), expected);
        } catch (Exception e) { return false; }
    }

    private static boolean privateEquals(Path file, PublicKey expected) {
        if (!Files.isRegularFile(file)) return false;
        try { return samePublic(publicKey(readPrivate(file)), expected); }
        catch (Exception e) { return false; }
    }

    private static boolean samePublic(PublicKey first, PublicKey second) {
        if (!(first instanceof RSAPublicKey) || !(second instanceof RSAPublicKey)) return false;
        RSAPublicKey a = (RSAPublicKey) first, b = (RSAPublicKey) second;
        return a.getModulus().equals(b.getModulus()) && a.getPublicExponent().equals(b.getPublicExponent());
    }

    private static PublicKey parsePublic(byte[] data) throws Exception {
        String text = new String(data, StandardCharsets.US_ASCII).trim();
        PublicKey key;
        if (text.startsWith("-----BEGIN")) {
            try (PEMParser parser = new PEMParser(new StringReader(text))) {
                Object object = parser.readObject();
                if (!(object instanceof SubjectPublicKeyInfo)) throw new IllegalArgumentException("请选择 RSA 公钥文件");
                key = new JcaPEMKeyConverter().getPublicKey((SubjectPublicKeyInfo) object);
                if (parser.readObject() != null) throw new IllegalArgumentException("公钥文件包含多个密钥");
            }
        } else {
            byte[] encoded = text.matches("[A-Za-z0-9+/=\\s]+") ? Base64.getMimeDecoder().decode(text) : data;
            KeyFactory factory = KeyFactory.getInstance("RSA");
            try { key = factory.generatePublic(new X509EncodedKeySpec(encoded)); }
            catch (Exception invalidX509) {
                org.bouncycastle.asn1.pkcs.RSAPublicKey rsa = org.bouncycastle.asn1.pkcs.RSAPublicKey.getInstance(ASN1Primitive.fromByteArray(encoded));
                key = factory.generatePublic(new RSAPublicKeySpec(rsa.getModulus(), rsa.getPublicExponent()));
            }
        }
        if (!(key instanceof RSAPublicKey)) throw new IllegalArgumentException("请选择 RSA 公钥文件");
        return key;
    }

    private static String fingerprint(PublicKey key) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getEncoded());
        StringBuilder text = new StringBuilder();
        for (byte b : digest) text.append(String.format(Locale.ROOT, "%02X", b & 0xff));
        return text.toString();
    }

    private static void add(List<FileReplacement> replacements, Path requested, byte[] content, boolean secret) throws IOException {
        Path path = actualPath(requested);
        for (FileReplacement existing : replacements) {
            if (existing.path.equals(path)) {
                if (existing.secret != secret || !Arrays.equals(existing.content, content)) throw new IOException("密钥文件路径发生冲突");
                return;
            }
        }
        replacements.add(new FileReplacement(path, content, secret));
    }

    private static Path actualPath(Path requested) throws IOException {
        if (Files.isSymbolicLink(requested)) {
            if (!Files.exists(requested)) throw new IOException("密钥文件软链接无效：" + requested);
            return requested.toRealPath();
        }
        if (Files.exists(requested) && !Files.isRegularFile(requested)) throw new IOException("密钥路径不是普通文件：" + requested);
        if (Files.exists(requested)) return requested.toRealPath();
        Path parent = requested.getParent();
        if (parent == null || !Files.isDirectory(parent)) throw new IOException("密钥目录不存在：" + parent);
        return parent.toRealPath().resolve(requested.getFileName());
    }

    private static void replaceFiles(List<FileReplacement> replacements) throws IOException {
        List<FileReplacement> changed = new ArrayList<>();
        try {
            for (FileReplacement file : replacements) file.prepare();
            for (FileReplacement file : replacements) {
                move(file.staged, file.path);
                file.staged = null;
                changed.add(file);
            }
        } catch (IOException | RuntimeException error) {
            Collections.reverse(changed);
            List<String> failures = new ArrayList<>();
            for (FileReplacement file : changed) {
                try { file.restore(); }
                catch (IOException e) { file.keepBackup = true; failures.add(String.valueOf(file.backup)); LOG.error("恢复密钥失败，保留备份 {}", file.backup, e); }
            }
            if (!failures.isEmpty()) throw new IOException("密钥更新失败且未能完全恢复，备份保存在：" + String.join("、", failures), error);
            throw new IOException("密钥更新失败，原密钥已保留", error);
        } finally {
            for (FileReplacement file : replacements) {
                deleteQuietly(file.staged);
                if (!file.keepBackup) deleteQuietly(file.backup);
            }
        }
    }

    private static void move(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException e) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try { Files.deleteIfExists(path); }
        catch (IOException e) { LOG.warn("无法清理密钥临时文件：{}", path, e); }
    }

    private static final class FileReplacement {
        final Path path;
        final byte[] content;
        final boolean secret;
        final boolean existed;
        Path staged, backup;
        boolean keepBackup;
        PosixFileAttributes oldAttributes;

        FileReplacement(Path path, byte[] content, boolean secret) {
            this.path = path; this.content = content; this.secret = secret; this.existed = Files.isRegularFile(path);
        }

        void prepare() throws IOException {
            Path parent = path.getParent();
            staged = Files.createTempFile(parent, ".dnf-key-new-", ".tmp");
            PosixFileAttributeView stagedView = Files.getFileAttributeView(staged, PosixFileAttributeView.class);
            if (stagedView != null) stagedView.setPermissions(PosixFilePermissions.fromString(secret ? "rw-------" : "rw-r--r--"));
            try (OutputStream output = Files.newOutputStream(staged, StandardOpenOption.TRUNCATE_EXISTING)) { output.write(content); }
            if (existed) {
                PosixFileAttributeView oldView = Files.getFileAttributeView(path, PosixFileAttributeView.class);
                if (oldView != null && stagedView != null) {
                    oldAttributes = oldView.readAttributes();
                    if (!stagedView.readAttributes().group().equals(oldAttributes.group())) stagedView.setGroup(oldAttributes.group());
                    if (!Files.getOwner(staged).equals(oldAttributes.owner())) Files.setOwner(staged, oldAttributes.owner());
                }
                if (!Files.isReadable(staged)) throw new IOException("当前 Web 用户无法读取更新后的密钥文件");
                backup = Files.createTempFile(parent, ".dnf-key-backup-", ".tmp");
                PosixFileAttributeView backupView = Files.getFileAttributeView(backup, PosixFileAttributeView.class);
                if (backupView != null) backupView.setPermissions(PosixFilePermissions.fromString("rw-------"));
                try (InputStream input = Files.newInputStream(path); OutputStream output = Files.newOutputStream(backup, StandardOpenOption.TRUNCATE_EXISTING)) {
                    byte[] buffer = new byte[8192]; int n;
                    while ((n = input.read(buffer)) != -1) output.write(buffer, 0, n);
                }
            }
        }

        void restore() throws IOException {
            if (!existed) { Files.deleteIfExists(path); return; }
            if (oldAttributes != null) {
                PosixFileAttributeView view = Files.getFileAttributeView(backup, PosixFileAttributeView.class);
                if (view != null) {
                    view.setPermissions(oldAttributes.permissions());
                    if (!view.readAttributes().group().equals(oldAttributes.group())) view.setGroup(oldAttributes.group());
                    if (!Files.getOwner(backup).equals(oldAttributes.owner())) Files.setOwner(backup, oldAttributes.owner());
                }
            }
            move(backup, path);
            backup = null;
        }
    }
}
