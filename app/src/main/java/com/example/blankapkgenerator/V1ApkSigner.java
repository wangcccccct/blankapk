package com.example.blankapkgenerator;

import android.util.Base64;

import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public final class V1ApkSigner {
    private static final BouncyCastleProvider BC_PROVIDER = new BouncyCastleProvider();

    private V1ApkSigner() {
    }

    public static byte[] sign(byte[] unsignedApk,
                              PrivateKey privateKey,
                              X509Certificate certificate,
                              String signerBaseName) throws Exception {
        LinkedHashMap<String, byte[]> sourceEntries = ZipUtils.readZip(unsignedApk);
        TreeMap<String, byte[]> payloadEntries = new TreeMap<>();
        for (Map.Entry<String, byte[]> entry : sourceEntries.entrySet()) {
            if (entry.getKey().startsWith("META-INF/")) {
                continue;
            }
            payloadEntries.put(entry.getKey(), entry.getValue());
        }

        Manifest manifest = new Manifest();
        Attributes mainAttributes = manifest.getMainAttributes();
        mainAttributes.putValue("Manifest-Version", "1.0");
        mainAttributes.putValue("Created-By", "Blank APK Generator");

        for (Map.Entry<String, byte[]> entry : payloadEntries.entrySet()) {
            Attributes attributes = new Attributes();
            attributes.putValue("SHA-256-Digest", base64(sha256(entry.getValue())));
            manifest.getEntries().put(entry.getKey(), attributes);
        }

        byte[] manifestBytes = writeManifest(manifest);
        byte[] signatureFileBytes = buildSignatureFile(payloadEntries, manifestBytes);
        byte[] signatureBlockBytes = buildSignatureBlock(signatureFileBytes, privateKey, certificate);

        LinkedHashMap<String, byte[]> finalEntries = new LinkedHashMap<>(payloadEntries);
        finalEntries.put("META-INF/MANIFEST.MF", manifestBytes);
        finalEntries.put("META-INF/" + signerBaseName + ".SF", signatureFileBytes);
        finalEntries.put("META-INF/" + signerBaseName + ".EC", signatureBlockBytes);
        return ZipUtils.writeZip(finalEntries);
    }

    private static byte[] buildSignatureFile(TreeMap<String, byte[]> payloadEntries, byte[] manifestBytes)
            throws Exception {
        StringBuilder builder = new StringBuilder();
        builder.append("Signature-Version: 1.0\r\n");
        builder.append("Created-By: Blank APK Generator\r\n");
        builder.append("SHA-256-Digest-Manifest: ")
                .append(base64(sha256(manifestBytes)))
                .append("\r\n\r\n");

        for (Map.Entry<String, byte[]> entry : payloadEntries.entrySet()) {
            byte[] sectionBytes = buildManifestSection(
                    entry.getKey(), base64(sha256(entry.getValue())));
            builder.append("Name: ")
                    .append(entry.getKey())
                    .append("\r\n");
            builder.append("SHA-256-Digest: ")
                    .append(base64(sha256(sectionBytes)))
                    .append("\r\n\r\n");
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] buildManifestSection(String entryName, String digestValue) {
        String section = "Name: " + entryName + "\r\n"
                + "SHA-256-Digest: " + digestValue + "\r\n\r\n";
        return section.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] buildSignatureBlock(byte[] signatureFileBytes,
                                              PrivateKey privateKey,
                                              X509Certificate certificate) throws Exception {
        List<X509Certificate> certificates = new ArrayList<>();
        certificates.add(certificate);

        JcaCertStore certStore = new JcaCertStore(certificates);
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA")
                .setProvider(BC_PROVIDER)
                .build(privateKey);
        JcaSignerInfoGeneratorBuilder signerInfoGeneratorBuilder =
                new JcaSignerInfoGeneratorBuilder(
                        new JcaDigestCalculatorProviderBuilder()
                                .setProvider(BC_PROVIDER)
                                .build());

        CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
        generator.addSignerInfoGenerator(signerInfoGeneratorBuilder.build(signer, certificate));
        generator.addCertificates(certStore);
        CMSSignedData signedData = generator.generate(new CMSProcessableByteArray(signatureFileBytes), false);
        return signedData.getEncoded();
    }

    private static byte[] writeManifest(Manifest manifest) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        manifest.write(outputStream);
        return outputStream.toByteArray();
    }

    private static byte[] sha256(byte[] bytes) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(bytes);
    }

    private static String base64(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }
}
