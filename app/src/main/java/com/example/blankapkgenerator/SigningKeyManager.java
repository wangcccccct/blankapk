package com.example.blankapkgenerator;

import android.content.Context;
import android.util.Base64;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Date;

public final class SigningKeyManager {
    private static final String KEY_ALIAS = "generated-apk-signer";
    private static final BouncyCastleProvider BC_PROVIDER = new BouncyCastleProvider();

    private final File signingDir;
    private final File keyStoreFile;
    private final File passwordFile;
    private final SecureRandom secureRandom = new SecureRandom();

    public SigningKeyManager(Context context) {
        this.signingDir = new File(context.getNoBackupFilesDir(), "generated-signer");
        this.keyStoreFile = new File(signingDir, "signer.p12");
        this.passwordFile = new File(signingDir, "signer.pass");
    }

    public synchronized KeyMaterial getOrCreate() throws Exception {
        if (!signingDir.exists() && !signingDir.mkdirs()) {
            throw new IllegalStateException("无法创建签名目录：" + signingDir.getAbsolutePath());
        }
        char[] password = getOrCreatePassword();
        if (!keyStoreFile.exists()) {
            createNewKeyStore(password);
        }

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream inputStream = new FileInputStream(keyStoreFile)) {
            keyStore.load(inputStream, password);
        }
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(KEY_ALIAS, password);
        X509Certificate certificate = (X509Certificate) keyStore.getCertificate(KEY_ALIAS);
        if (privateKey == null || certificate == null) {
            throw new IllegalStateException("本地签名密钥读取失败");
        }
        return new KeyMaterial(privateKey, certificate);
    }

    public synchronized void rotate() throws Exception {
        if (keyStoreFile.exists() && !keyStoreFile.delete()) {
            throw new IllegalStateException("删除旧 keystore 失败");
        }
        if (passwordFile.exists() && !passwordFile.delete()) {
            throw new IllegalStateException("删除旧密码文件失败");
        }
        getOrCreate();
    }

    private void createNewKeyStore(char[] password) throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"), secureRandom);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        X509Certificate certificate = createSelfSignedCertificate(keyPair);

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, password);
        keyStore.setKeyEntry(KEY_ALIAS, keyPair.getPrivate(), password, new Certificate[]{certificate});
        try (FileOutputStream outputStream = new FileOutputStream(keyStoreFile)) {
            keyStore.store(outputStream, password);
        }
    }

    private char[] getOrCreatePassword() throws Exception {
        if (!passwordFile.exists()) {
            byte[] randomBytes = new byte[24];
            secureRandom.nextBytes(randomBytes);
            String encoded = Base64.encodeToString(
                    randomBytes, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            try (FileOutputStream outputStream = new FileOutputStream(passwordFile)) {
                outputStream.write(encoded.getBytes(StandardCharsets.UTF_8));
            }
            return encoded.toCharArray();
        }
        byte[] bytes = readAllBytes(passwordFile);
        return new String(bytes, StandardCharsets.UTF_8).trim().toCharArray();
    }

    private static byte[] readAllBytes(File file) throws Exception {
        try (FileInputStream inputStream = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int read;
            java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        }
    }

    private X509Certificate createSelfSignedCertificate(KeyPair keyPair) throws Exception {
        long now = System.currentTimeMillis();
        Date notBefore = new Date(now - 24L * 60L * 60L * 1000L);
        Date notAfter = new Date(now + (20L * 365L * 24L * 60L * 60L * 1000L));
        X500Name name = new X500Name("CN=Blank APK Generator Local Signer");
        BigInteger serial = new BigInteger(64, secureRandom).abs();

        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                name, serial, notBefore, notAfter, name, keyPair.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA")
                .setProvider(BC_PROVIDER)
                .build(keyPair.getPrivate());
        X509CertificateHolder holder = builder.build(signer);
        return new JcaX509CertificateConverter()
                .setProvider(BC_PROVIDER)
                .getCertificate(holder);
    }

    public static String fingerprintSha256(X509Certificate certificate) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < digest.length; i++) {
            if (i > 0) {
                builder.append(':');
            }
            builder.append(String.format("%02X", digest[i]));
        }
        return builder.toString();
    }

    public static final class KeyMaterial {
        public final PrivateKey privateKey;
        public final X509Certificate certificate;

        KeyMaterial(PrivateKey privateKey, X509Certificate certificate) {
            this.privateKey = privateKey;
            this.certificate = certificate;
        }
    }
}
