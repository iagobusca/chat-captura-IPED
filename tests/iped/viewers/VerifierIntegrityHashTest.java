package iped.viewers;

import java.lang.reflect.Method;

public final class VerifierIntegrityHashTest {
    private VerifierIntegrityHashTest() { }

    public static void main(String[] args) throws Exception {
        Method isValid = VerifierIntegrity.class.getDeclaredMethod("isValid", String.class, String.class);
        isValid.setAccessible(true);
        check((Boolean) isValid.invoke(null, "integridade-verifier.jar",
                "906705C1A69F41505B0A794AA93F7E87D606FD62E5895A8FD2D3A9E0AA9E4B96"), "1.2.8 JAR hash must be accepted");
        check((Boolean) isValid.invoke(null, "integridade-verifier.exe",
                "4F591C102058B0046814E9A5C9019E7483DB82A1E286BFF80DDC4CEA7E85A4EF"), "1.2.8 EXE hash must be accepted");
        check(!(Boolean) isValid.invoke(null, "integridade-verifier.jar",
                "D16D0BA8EDD722DE810DE5A7E5B458271E216961CAF0DAD9BEBDA9B0F715097D"), "old JAR hash must be rejected");
        check(!(Boolean) isValid.invoke(null, "integridade-verifier.exe",
                "AB85C3B2C55E2A727378BD5973ACF64CED2E16D1AC32198B11670DB48C8744A5"), "old EXE hash must be rejected");
        System.out.println("VerifierIntegrityHashTest: PASS");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
