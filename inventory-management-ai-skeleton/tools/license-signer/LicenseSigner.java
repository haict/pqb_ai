import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Công cụ của nhà cung cấp để tạo cặp khóa và ký license (D-59). Chạy trên máy nhà cung cấp, KHÔNG đóng gói vào WAR,
 * KHÔNG commit private key. Java 17, không cần thư viện ngoài:
 *
 * <pre>
 * java LicenseSigner.java keygen --key-id vendor-2026 --out D:/secure/license-keys
 * java LicenseSigner.java sign --private-key D:/secure/license-keys/vendor-2026.private.key --key-id vendor-2026 \
 *      --license-id PQB-2026-11 --customer "Công ty PQB" --tax-codes 0106901068,0108267224 \
 *      --valid-to 2026-11-30 --grace-days 15 --out pqb-2026-11.lic
 * java LicenseSigner.java sign --params pqb-2026-11.properties   (tham số trong file UTF-8, nên dùng khi có tiếng Việt)
 * java LicenseSigner.java verify --public-key D:/secure/license-keys/vendor-2026.public.key --license pqb-2026-11.lic
 * </pre>
 *
 * Định dạng license: {@code base64(payload JSON UTF-8).base64(chữ ký Ed25519 trên đúng chuỗi byte payload)}.
 */
public final class LicenseSigner {

    private static final String ALGORITHM = "Ed25519";

    private LicenseSigner() {
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        if (args.length == 0) {
            usage();
            return;
        }
        Map<String, String> options = options(args);
        // Tham số tiếng Việt trên dòng lệnh Windows có thể sai mã; đặt trong file UTF-8 và truyền --params.
        if (options.containsKey("params")) {
            Map<String, String> merged = params(Path.of(options.get("params")));
            merged.putAll(options);
            options = merged;
        }
        switch (args[0]) {
            case "keygen" -> keygen(options);
            case "sign" -> sign(options);
            case "verify" -> verify(options);
            default -> usage();
        }
    }

    private static void keygen(Map<String, String> options) throws GeneralSecurityException, IOException {
        String keyId = required(options, "key-id");
        Path dir = Path.of(required(options, "out"));
        Files.createDirectories(dir);
        Path privateFile = dir.resolve(keyId + ".private.key");
        if (Files.exists(privateFile)) {
            throw new IllegalArgumentException("Đã có " + privateFile + "; không ghi đè private key.");
        }
        KeyPair pair = KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair();
        String publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        Files.writeString(privateFile, Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
        Files.writeString(dir.resolve(keyId + ".public.key"), publicKey);
        System.out.println("Đã tạo private key: " + privateFile + " (cất giữ an toàn, sao lưu, không gửi cho ai)");
        System.out.println("Thêm dòng sau vào backend/src/main/resources/license/public-keys.properties:");
        System.out.println(keyId + "=" + publicKey);
    }

    private static void sign(Map<String, String> options) throws GeneralSecurityException, IOException {
        PrivateKey key = KeyFactory.getInstance(ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(
                Base64.getDecoder().decode(Files.readString(Path.of(required(options, "private-key"))).trim())));
        List<String> taxCodes = new ArrayList<>();
        for (String code : required(options, "tax-codes").split(",")) {
            if (!code.isBlank()) {
                taxCodes.add(code.trim());
            }
        }
        String payload = "{"
                + field("keyId", required(options, "key-id")) + ","
                + field("licenseId", required(options, "license-id")) + ","
                + field("customerName", options.getOrDefault("customer", "")) + ","
                + "\"taxCodes\":[" + String.join(",", taxCodes.stream().map(LicenseSigner::quote).toList()) + "],"
                + field("validTo", LocalDate.parse(required(options, "valid-to")).toString()) + ","
                + "\"graceDays\":" + Integer.parseInt(options.getOrDefault("grace-days", "15")) + ","
                + field("issuedAt", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString())
                + "}";
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance(ALGORITHM);
        signer.initSign(key);
        signer.update(bytes);
        String license = Base64.getEncoder().encodeToString(bytes) + "."
                + Base64.getEncoder().encodeToString(signer.sign());
        Path out = Path.of(required(options, "out"));
        Files.writeString(out, license);
        System.out.println("Đã ký license: " + out);
        System.out.println(payload);
    }

    private static void verify(Map<String, String> options) throws GeneralSecurityException, IOException {
        PublicKey key = KeyFactory.getInstance(ALGORITHM).generatePublic(new X509EncodedKeySpec(
                Base64.getDecoder().decode(Files.readString(Path.of(required(options, "public-key"))).trim())));
        String[] parts = Files.readString(Path.of(required(options, "license"))).trim().split("\\.");
        byte[] payload = Base64.getDecoder().decode(parts[0]);
        Signature verifier = Signature.getInstance(ALGORITHM);
        verifier.initVerify(key);
        verifier.update(payload);
        boolean valid = parts.length == 2 && verifier.verify(Base64.getDecoder().decode(parts[1]));
        System.out.println(valid ? "Chữ ký HỢP LỆ" : "Chữ ký KHÔNG hợp lệ");
        System.out.println(new String(payload, StandardCharsets.UTF_8));
    }

    private static String field(String name, String value) {
        return quote(name) + ":" + quote(value);
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    /** File tham số dạng {@code key=value} (UTF-8), cùng tên với tùy chọn dòng lệnh, ví dụ {@code customer=Công ty PQB}. */
    private static Map<String, String> params(Path file) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        Map<String, String> values = new HashMap<>();
        properties.stringPropertyNames().forEach(name -> values.put(name, properties.getProperty(name).trim()));
        return values;
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 1; i + 1 < args.length; i += 2) {
            if (!args[i].startsWith("--")) {
                throw new IllegalArgumentException("Tham số không hợp lệ: " + args[i]);
            }
            options.put(args[i].substring(2), args[i + 1]);
        }
        return options;
    }

    private static String required(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Thiếu tham số --" + name);
        }
        return value;
    }

    private static void usage() {
        System.out.println("""
                Cách dùng:
                  keygen --key-id <mã khóa> --out <thư mục>
                  sign   --private-key <file> --key-id <mã khóa> --license-id <mã license> --customer <tên khách>
                         --tax-codes <MST1,MST2> --valid-to <yyyy-mm-dd> [--grace-days 15] --out <file .lic>
                  verify --public-key <file> --license <file .lic>""");
    }
}
