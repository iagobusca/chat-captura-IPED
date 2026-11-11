package iped.viewers;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

public final class VerifierIntegrityHashTest {
    private VerifierIntegrityHashTest() { }

    public static void main(String[] args) throws Exception {
        Method isValid = VerifierIntegrity.class.getDeclaredMethod("isValid", String.class, String.class);
        isValid.setAccessible(true);
        check((Boolean) isValid.invoke(null, "integridade-verifier.jar",
                "241FD904C02F65E19EE3D663C24A9AA15B685E8A43DD7F545AD5D07133BC79DB"), "1.3.4 JAR hash must be accepted");
        check((Boolean) isValid.invoke(null, "integridade-verifier.exe",
                "E4137082FE2A025DD406891A82313B2094A6C15A2F6D2F0804E2BE342ED57C38"), "1.3.4 EXE hash must be accepted");
        check(!(Boolean) isValid.invoke(null, "integridade-verifier.jar",
                "034DC3369C7AC00C80D6F2034745E5B1242B2DD2055C9DD583B97C6DD041CB44"), "previous 1.3.4 JAR hash must be rejected");
        check(!(Boolean) isValid.invoke(null, "integridade-verifier.exe",
                "FD136842AE223C3AE8E09E1C1670884249390D37CDB763E1D247CBC3462A4FDE"), "previous 1.3.4 EXE hash must be rejected");
        check(!(Boolean) isValid.invoke(null, "integridade-verifier.jar",
                "22FBAA2A8D4EA32940B4555E95207A22982B7FCDD308020950A667853420D921"), "1.3.3 JAR hash must be rejected");
        check(!(Boolean) isValid.invoke(null, "integridade-verifier.exe",
                "933F27A61AEDDAC5E4F1D8D7CA7AFC057AA2F04062A9267783FC56BB00DCE25B"), "1.3.3 EXE hash must be rejected");
        check(!(Boolean) isValid.invoke(null, "integridade-verifier.jar",
                "F6D21DC4DAAFBF0BB241B66790EE2FE137AF5DBD5609EF9CED8D1336D622A7A7"), "1.3.2 JAR hash must be rejected");
        check(!(Boolean) isValid.invoke(null, "integridade-verifier.exe",
                "94FC180EA3D25DB9881F3D245FE1D2100CC21B6263F6163E129B5033B55D5908"), "1.3.2 EXE hash must be rejected");
        check(!(Boolean) isValid.invoke(null, "integridade-verifier.jar",
                "0C262CC3D2F8BB0FD6CF7FB3E4071CF19903D684396356F56F9D92DB869F8339"), "1.3.1 JAR hash must be rejected");
        check(!(Boolean) isValid.invoke(null, "integridade-verifier.exe",
                "AE1E2E1E9D7E78BBCF8C551C2209F7F990C8AC37C77F751556BB29D698D719F5"), "1.3.1 EXE hash must be rejected");
        check(!(Boolean) isValid.invoke(null, "integridade-verifier.jar",
                "CB5B11C913763546084EC0745E5D95F059BC731D752183FE306AB8FBCBD29FC6"), "1.3.0 JAR hash must be rejected");
        check(!(Boolean) isValid.invoke(null, "integridade-verifier.exe",
                "963B44556ECEA6A57D65E14A5EF74C1F842779FE05ACA068390F6511F295654C"), "1.3.0 EXE hash must be rejected");
        check(!(Boolean) isValid.invoke(null, "integridade-verifier.jar",
                "906705C1A69F41505B0A794AA93F7E87D606FD62E5895A8FD2D3A9E0AA9E4B96"), "1.2.8 JAR hash must be rejected");
        check(!(Boolean) isValid.invoke(null, "integridade-verifier.exe",
                "4F591C102058B0046814E9A5C9019E7483DB82A1E286BFF80DDC4CEA7E85A4EF"), "1.2.8 EXE hash must be rejected");
        Method findTrustedFile = VerifierIntegrity.class.getDeclaredMethod("findTrustedVerifierFile", String.class);
        findTrustedFile.setAccessible(true);
        Method sha256 = VerifierIntegrity.class.getDeclaredMethod("sha256", Path.class);
        sha256.setAccessible(true);
        for (String name : new String[] { "integridade-verifier.jar", "integridade-verifier.exe" }) {
            Path file = (Path) findTrustedFile.invoke(null, name);
            check(file != null && Files.isRegularFile(file), "trusted current verifier must be found: " + name);
            check((Boolean) isValid.invoke(null, name, sha256.invoke(null, file)),
                    "selected verifier source must match the current allowlist: " + name);
        }
        Path temp = Files.createTempDirectory("verifier-integrity-copy-test");
        Path output = temp.resolve("new-output");
        Method install = VerifierIntegrity.class.getDeclaredMethod("installVerifierFiles", Path.class);
        install.setAccessible(true);
        try {
            install.invoke(null, output);
            check(Files.isRegularFile(output.resolve("integridade-verifier.jar")), "missing JAR must be installed");
            check(Files.isRegularFile(output.resolve("integridade-verifier.exe")), "missing EXE must be installed");
            for (String name : new String[] { "integridade-verifier.jar", "integridade-verifier.exe" }) {
                Path file = output.resolve(name);
                check((Boolean) isValid.invoke(null, name, sha256.invoke(null, file)),
                        "installed missing verifier must match allowlist: " + name);
                Files.write(file, new byte[] { 1, 2, 3 });
            }
            install.invoke(null, output);
            for (String name : new String[] { "integridade-verifier.jar", "integridade-verifier.exe" }) {
                Path file = output.resolve(name);
                check((Boolean) isValid.invoke(null, name, sha256.invoke(null, file)),
                        "invalid verifier must be replaced by trusted source: " + name);
            }
        } finally {
            Files.deleteIfExists(output.resolve("integridade-verifier.jar"));
            Files.deleteIfExists(output.resolve("integridade-verifier.exe"));
            Files.deleteIfExists(output);
            Files.deleteIfExists(temp);
        }
        System.out.println("VerifierIntegrityHashTest: PASS");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
