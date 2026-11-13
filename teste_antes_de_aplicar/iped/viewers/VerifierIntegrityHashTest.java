package iped.viewers;

import java.lang.reflect.Method;

public final class VerifierIntegrityHashTest {
    private VerifierIntegrityHashTest() { }

    public static void main(String[] args) throws Exception {
        Method isValid = VerifierIntegrity.class.getDeclaredMethod("isValid", String.class, String.class);
        isValid.setAccessible(true);
        check((Boolean) isValid.invoke(null, "integridade-verifier.jar",
                "7A137AE3286231246AF335380C3E8716B17A6696552096474563169DB4F5F2EE"), "1.3.7 JAR hash must be accepted");
        check((Boolean) isValid.invoke(null, "integridade-verifier.exe",
                "D102E17D02F39E5D6E85249E9B9585BC26C3D005E7DBFF2296C22FB2E18E926A"), "1.3.7 EXE hash must be accepted");
        check(!(Boolean) isValid.invoke(null, "integridade-verifier.jar",
                "F7990400A98445B8FA718626DA08145CD974866487650BD443E95427DD015FAC"), "old 1.3.6 JAR hash must be rejected");
        check(!(Boolean) isValid.invoke(null, "integridade-verifier.exe",
                "24E84B1B014D9677E238A94FD9EBFFB5CD2E8CF5CCA244AF8D47D1ED48C704D4"), "old 1.3.6 EXE hash must be rejected");
        System.out.println("VerifierIntegrityHashTest: PASS");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
