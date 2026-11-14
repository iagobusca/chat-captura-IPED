package iped.viewers;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public final class VerifierIntegrityHashTest {
    private static final String EXPECTED_JAR_142 = "08877EFF5F2FA0A1E2F64AD0072490F9400AAA26E51ACD8E18C4D2DEE5F59475";
    private static final String EXPECTED_EXE_142 = "4859980DC532340427EEF2983B62EA4596BC6B8FA1D6F80462BB76CF03278105";

    private VerifierIntegrityHashTest() { }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Field validJarField = VerifierIntegrity.class.getDeclaredField("VALID_JAR_HASHES");
        validJarField.setAccessible(true);
        Set<String> validJarHashes = (Set<String>) validJarField.get(null);
        check(validJarHashes.size() == 1 && validJarHashes.contains(EXPECTED_JAR_142),
                "VALID_JAR_HASHES must contain exclusively 1.4.2 hash");

        Field validExeField = VerifierIntegrity.class.getDeclaredField("VALID_EXE_HASHES");
        validExeField.setAccessible(true);
        Set<String> validExeHashes = (Set<String>) validExeField.get(null);
        check(validExeHashes.size() == 1 && validExeHashes.contains(EXPECTED_EXE_142),
                "VALID_EXE_HASHES must contain exclusively 1.4.2 hash");

        Method isValid = VerifierIntegrity.class.getDeclaredMethod("isValid", String.class, String.class);
        isValid.setAccessible(true);
        check((Boolean) isValid.invoke(null, "integridade-verifier.jar", EXPECTED_JAR_142),
                "1.4.2 JAR hash must be accepted");
        check((Boolean) isValid.invoke(null, "integridade-verifier.exe", EXPECTED_EXE_142),
                "1.4.2 EXE hash must be accepted");
        check(!(Boolean) isValid.invoke(null, "integridade-verifier.jar",
                "CC2958812B0D56CF60B96641EE75F5776F463E0986503AC0FE15ABF82CE71345"), "old 1.4.1 JAR hash must be rejected");
        check(!(Boolean) isValid.invoke(null, "integridade-verifier.exe",
                "A7283005AF86ED5DD0E47C07619DCD1F3A6F4618B10DE3AC208B7F2A1549A29C"), "old 1.4.1 EXE hash must be rejected");
        check(!(Boolean) isValid.invoke(null, "integridade-verifier.jar",
                "9CA17D7BA2EC50808D333495716D277F4DA963BBBC946C3EF7F765079F48A6E5"), "old 1.4.0 JAR hash must be rejected");
        check(!(Boolean) isValid.invoke(null, "integridade-verifier.exe",
                "11094FA3CCE812AF3421490FF1BC2F3B8100C9B5D7551DBBA7F05F78C8136809"), "old 1.4.0 EXE hash must be rejected");
        check(!(Boolean) isValid.invoke(null, "integridade-verifier.jar",
                "043AFEC21D79B4DF3C8A3DDB42F49CF5CB9467CECD11397E724091DFA310E2A7"), "old 1.3.9 JAR hash must be rejected");
        check(!(Boolean) isValid.invoke(null, "integridade-verifier.exe",
                "FF06525056E008A91AABF49D9B98B3E0CEE55CCED3846A89AA76D7B39BC6BC85"), "old 1.3.9 EXE hash must be rejected");
        check(!(Boolean) isValid.invoke(null, "integridade-verifier.jar",
                "556B1A652183A343B168057029CCF10006361D8CB53526BA610E81A4D76532B6"), "old 1.3.8 JAR hash must be rejected");
        check(!(Boolean) isValid.invoke(null, "integridade-verifier.exe",
                "B9D77A7F88F7D87D3697B855191427C3CE790EFA827830DEABE263E4E2962DD2"), "old 1.3.8 EXE hash must be rejected");
        check(!(Boolean) isValid.invoke(null, "integridade-verifier.jar",
                "7A137AE3286231246AF335380C3E8716B17A6696552096474563169DB4F5F2EE"), "old 1.3.7 JAR hash must be rejected");
        check(!(Boolean) isValid.invoke(null, "integridade-verifier.exe",
                "D102E17D02F39E5D6E85249E9B9585BC26C3D005E7DBFF2296C22FB2E18E926A"), "old 1.3.7 EXE hash must be rejected");
        check(!(Boolean) isValid.invoke(null, "integridade-verifier.jar",
                "F7990400A98445B8FA718626DA08145CD974866487650BD443E95427DD015FAC"), "old 1.3.6 JAR hash must be rejected");
        check(!(Boolean) isValid.invoke(null, "integridade-verifier.exe",
                "24E84B1B014D9677E238A94FD9EBFFB5CD2E8CF5CCA244AF8D47D1ED48C704D4"), "old 1.3.6 EXE hash must be rejected");
        check(!(Boolean) isValid.invoke(null, "integridade-verifier.jar",
                "31B0CE725708ED53AD9BE319FFE51439940C65BB46EF8593AB78376C07BF46B9"), "previous 1.3.5 JAR hash must be rejected");
        check(!(Boolean) isValid.invoke(null, "integridade-verifier.exe",
                "75FB542BCB53C44C7B17553EEC761C16F17C670F8033DD3F901F80A0897AC42B"), "previous 1.3.5 EXE hash must be rejected");
        check(!(Boolean) isValid.invoke(null, "integridade-verifier.jar",
                "B456074C393949FE63DC33B404F539B569830CE92FC657FF43B622D9E03500DD"), "previous 1.3.4 JAR hash must be rejected");
        check(!(Boolean) isValid.invoke(null, "integridade-verifier.exe",
                "20FAF8A5AD2690A909F09B6A60DFBFE12A271493DA3331595F8BB2A95EF2BCA0"), "previous 1.3.4 EXE hash must be rejected");
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
            testInstalledIpedLayoutAndMissingDir(temp);
        } finally {
            Files.deleteIfExists(output.resolve("integridade-verifier.jar"));
            Files.deleteIfExists(output.resolve("integridade-verifier.exe"));
            Files.deleteIfExists(output);
            Files.deleteIfExists(temp);
        }
        System.out.println("VerifierIntegrityHashTest: PASS");
    }

    private static void testInstalledIpedLayoutAndMissingDir(Path temp) throws Exception {
        Path fakeIped = temp.resolve("fake-iped");
        Path fakeLibJar = fakeIped.resolve("lib").resolve("iped-viewers-impl-4.3.1.jar");
        Path fakeVerifierDir = fakeIped.resolve("verificador-integridade");
        Files.createDirectories(fakeLibJar.getParent());
        Files.createDirectories(fakeVerifierDir);
        Files.write(fakeLibJar, new byte[] { 1 });

        Path realJar = VerifierIntegrity.findTrustedVerifierFile("integridade-verifier.jar",
                java.util.Collections.singletonList(java.nio.file.Paths.get("verificador-integridade").toAbsolutePath().normalize()));
        Path realExe = VerifierIntegrity.findTrustedVerifierFile("integridade-verifier.exe",
                java.util.Collections.singletonList(java.nio.file.Paths.get("verificador-integridade").toAbsolutePath().normalize()));
        check(realJar != null && realExe != null, "repo verificador-integridade binaries must exist for layout test");

        Path installedJar = fakeVerifierDir.resolve("integridade-verifier.jar");
        Path installedExe = fakeVerifierDir.resolve("integridade-verifier.exe");
        Files.copy(realJar, installedJar);
        Files.copy(realExe, installedExe);
        try {
            java.util.List<Path> dirs = VerifierIntegrity.verifierSourceDirs(fakeLibJar);
            check(dirs.contains(fakeVerifierDir.toAbsolutePath().normalize()),
                    "verifierSourceDirs must include <IPED_ROOT>/verificador-integridade");
            Path foundJar = VerifierIntegrity.findTrustedVerifierFile("integridade-verifier.jar", dirs);
            Path foundExe = VerifierIntegrity.findTrustedVerifierFile("integridade-verifier.exe", dirs);
            check(installedJar.toAbsolutePath().normalize().equals(foundJar),
                    "must resolve installed verifier JAR from <IPED_ROOT>/verificador-integridade");
            check(installedExe.toAbsolutePath().normalize().equals(foundExe),
                    "must resolve installed verifier EXE from <IPED_ROOT>/verificador-integridade");

            Path emptyIpedJar = temp.resolve("missing-iped").resolve("lib").resolve("iped-viewers-impl-4.3.1.jar");
            java.util.List<Path> emptyDirs = VerifierIntegrity.verifierSourceDirs(emptyIpedJar);
            check(VerifierIntegrity.findTrustedVerifierFile("integridade-verifier.jar", emptyDirs) == null,
                    "missing verifier dir must return null without throwing");
            check(VerifierIntegrity.findTrustedVerifierFile("integridade-verifier.exe",
                    java.util.Collections.singletonList(temp.resolve("does-not-exist"))) == null,
                    "nonexistent explicit dir must return null without throwing");
        } finally {
            Files.deleteIfExists(installedJar);
            Files.deleteIfExists(installedExe);
            Files.deleteIfExists(fakeVerifierDir);
            Files.deleteIfExists(fakeLibJar);
            Files.deleteIfExists(fakeLibJar.getParent());
            Files.deleteIfExists(fakeIped);
        }
        junctionOutputAndSourceAreRejected(temp);
    }

    private static void junctionOutputAndSourceAreRejected(Path temp) throws Exception {
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
            return;
        }
        Path outside = Files.createDirectories(temp.resolve("outside-verifier-target"));
        Path outputJunction = temp.resolve("output-junction");
        createWindowsJunction(outputJunction, outside);
        try {
            VerifierIntegrity.installVerifierFiles(outputJunction);
            check(!Files.exists(outside.resolve("integridade-verifier.jar")),
                    "installVerifierFiles must not write JAR through output NTFS junction");
            check(!Files.exists(outside.resolve("integridade-verifier.exe")),
                    "installVerifierFiles must not write EXE through output NTFS junction");
        } finally {
            Files.deleteIfExists(outputJunction);
            Files.deleteIfExists(outside);
        }

        Path realSource = Files.createDirectories(temp.resolve("real-verifier-source"));
        Path repoVerifierDir = java.nio.file.Paths.get("verificador-integridade").toAbsolutePath().normalize();
        Path realJar = VerifierIntegrity.findTrustedVerifierFile("integridade-verifier.jar",
                java.util.Collections.singletonList(repoVerifierDir));
        check(realJar != null, "repo verifier JAR must exist for junction source test");
        Files.copy(realJar, realSource.resolve("integridade-verifier.jar"));
        Path sourceJunction = temp.resolve("source-junction");
        createWindowsJunction(sourceJunction, realSource);
        try {
            Path resolved = VerifierIntegrity.findTrustedVerifierFile("integridade-verifier.jar",
                    java.util.Collections.singletonList(sourceJunction));
            check(resolved == null, "findTrustedVerifierFile must reject NTFS junction source directory");
        } finally {
            Files.deleteIfExists(sourceJunction);
            Files.deleteIfExists(realSource.resolve("integridade-verifier.jar"));
            Files.deleteIfExists(realSource);
        }
    }

    private static void createWindowsJunction(Path link, Path target) throws Exception {
        Process p = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J",
                link.toAbsolutePath().toString(), target.toAbsolutePath().toString())
                .redirectErrorStream(true).start();
        byte[] out = p.getInputStream().readAllBytes();
        if (p.waitFor() != 0) {
            throw new java.io.IOException("Failed to create NTFS junction: "
                    + new String(out, java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
