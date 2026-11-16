import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT / "teste_antes_de_aplicar") not in sys.path:
    sys.path.insert(0, str(ROOT / "teste_antes_de_aplicar"))

from test_env_paths import find_jdk_tool

JAVAC8 = find_jdk_tool("javac.exe", (8,), ("JAVA8_HOME",))
JAVA8 = JAVAC8.with_name("java.exe")
JSON_JAR = ROOT / "verificador-integridade" / "dependencies" / "json-20260719.jar"


class Fase4AppliedValidationTest(unittest.TestCase):
    def test_f01_production_verifier_rejects_unquoted_quote_bypass_in_jar_and_exe(self):
        with tempfile.TemporaryDirectory(prefix="f04-f01-") as tmp:
            classes = Path(tmp) / "classes"
            classes.mkdir()
            probe = Path(tmp) / "AppliedF01Probe.java"
            probe.write_text(
                r'''
import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;
public class AppliedF01Probe {
    public static void main(String[] args) throws Exception {
        Field cspF = IntegrityVerifier.class.getDeclaredField("CAPTURE_CSP"); cspF.setAccessible(true);
        Field jsF = IntegrityVerifier.class.getDeclaredField("CAPTURE_MEDIA_STOP_SCRIPT"); jsF.setAccessible(true);
        String csp = (String) cspF.get(null);
        String js = (String) jsF.get(null);
        Method val = IntegrityVerifier.class.getDeclaredMethod("validateRenderedCaptureHtml", String.class, Set.class, Map.class, Set.class, int.class);
        val.setAccessible(true);
        String key = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        Set<String> keys = Collections.singleton(key);
        String ok = "<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\"><meta http-equiv=\"Content-Security-Policy\" content=\"" + csp + "\">"
                + "<style>body{}</style><style>.c{}</style></head><body><div data-capture-message-key=\"" + key + "\" title=\">\">2 &lt; 3</div><script>" + js + "</script></body></html>";
        val.invoke(null, ok, Collections.emptySet(), Collections.emptyMap(), keys, 2);

        String[] payloads = {
            ok.replace("<style>body{}</style>", "<meta http-equiv=\"refresh\" content=\"0;url=https://evil.invalid\" a=b'><style>body{}</style>"),
            ok.replace("2 &lt; 3", "<iframe src=\"evil.html\" a=b'></iframe>"),
            ok.replace("2 &lt; 3", "<a href=\"https://evil.invalid\" a=b'>phish</a>"),
            ok.replace("2 &lt; 3", "<svg onload=\"alert(1)\" a=b'>")
        };
        for (String p : payloads) {
            boolean rejected = false;
            try {
                val.invoke(null, p, Collections.emptySet(), Collections.emptyMap(), keys, 2);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof IOException) rejected = true;
                else throw e;
            }
            if (!rejected) throw new AssertionError("Bypass payload accepted: " + p);
        }
        System.out.println("APPLIED_F01_PASS");
    }
}
''',
                encoding="utf-8",
            )
            subprocess.run(
                [str(JAVAC8), "-encoding", "UTF-8", "-cp", str(JSON_JAR), "-d", str(classes),
                 str(ROOT / "verificador-integridade" / "src" / "IntegrityVerifier.java"), str(probe)],
                check=True, capture_output=True, text=True,
            )
            res = subprocess.run(
                [str(JAVA8), "-cp", f"{classes};{JSON_JAR}", "AppliedF01Probe"],
                check=True, capture_output=True, text=True,
            )
            self.assertIn("APPLIED_F01_PASS", res.stdout)

    def test_f02_f03_source_contracts_present(self):
        linked_src = (ROOT / "src" / "iped" / "viewers" / "ChatCaptureLinkedWindow.java").read_text(encoding="utf-8")
        self.assertIn("WindowConstants.DISPOSE_ON_CLOSE", linked_src)
        self.assertIn("closed.get()", linked_src)
        viewer_src = (ROOT / "src" / "iped" / "viewers" / "HtmlLinkViewer.java").read_text(encoding="utf-8")
        self.assertIn("shouldSelectRecaptureCheckBox", viewer_src)
        self.assertIn("collectSelectedRecaptureFolders", viewer_src)
        self.assertIn("filterValidRecaptureFolders", viewer_src)
        self.assertIn("if (disposed)", viewer_src)

    def test_f04_f06_production_bat_and_localization_in_bang_path(self):
        inst_src = ROOT / "instalar_captura_chat_zap"
        with tempfile.TemporaryDirectory(prefix="f04-f06-e2e-") as tmp:
            base = Path(tmp) / "Caso!2026!Teste"
            inst = base / "instalar_captura_chat_zap"
            iped = base / "iped-4.3.1"
            shutil.copytree(inst_src, inst)
            (iped / "lib").mkdir(parents=True)
            (iped / "localization").mkdir(parents=True)
            (iped / "iped.exe").write_bytes(b"MZ")
            shutil.copy2(inst / "iped-viewers-impl-4.3.1.jar", iped / "lib" / "iped-viewers-impl-4.3.1.jar")
            (iped / "lib" / "json-20250517.jar").write_bytes(b"oldjson")
            (iped / "localization" / "iped-viewer-messages.properties").write_bytes(
                b"HexViewerPlus.appName=Hex\r\nTrailingKey=abc\\\r\n"
            )
            (iped / "localization" / "iped-viewer-messages_pt_BR.properties").write_bytes(
                b"HexViewerPlus.appName=Hex\r\nTrailingEscaped=Trail\\\\\r\n"
            )
            res = subprocess.run(
                ["cmd.exe", "/c", str(inst / "instalar_captura_chat_zap.bat")],
                input="\n\n\n", text=True, capture_output=True, timeout=60,
            )
            self.assertEqual(res.returncode, 0, f"stdout:\n{res.stdout}\nstderr:\n{res.stderr}")
            self.assertIn("Instalacao concluida com sucesso", res.stdout)
            self.assertTrue((iped / "verificador-integridade" / "integridade-verifier.exe").is_file())
            self.assertTrue((iped / "verificador-integridade" / "integridade-verifier.jar").is_file())
            self.assertTrue((iped / "opencv_java4120.dll").is_file())
            self.assertTrue((iped / "plugins" / "opencv_java4120.dll").is_file())

            prop_check = Path(tmp) / "PropVerify.java"
            prop_check.write_text(
                r'''
import java.io.*; import java.nio.charset.StandardCharsets; import java.util.Properties;
public class PropVerify {
    public static void main(String[] a) throws Exception {
        Properties p = new Properties();
        try (Reader r = new InputStreamReader(new FileInputStream(a[0]), StandardCharsets.UTF_8)) { p.load(r); }
        System.out.println("INC_OK=" + (p.getProperty("ChatCapturePanel.IncludeAnnotations") != null));
        System.out.println("END_OK=" + (p.getProperty("ChatCapturePanel.EndDateNotAllowed") != null));
    }
}
''',
                encoding="utf-8",
            )
            subprocess.run([str(JAVAC8), "-d", tmp, str(prop_check)], check=True, capture_output=True, text=True, encoding="utf-8", errors="replace")
            for fname in ("iped-viewer-messages.properties", "iped-viewer-messages_pt_BR.properties"):
                loaded = subprocess.run(
                    [str(JAVA8), "-cp", tmp, "PropVerify", str(iped / "localization" / fname)],
                    check=True, capture_output=True, text=True, encoding="utf-8", errors="replace",
                ).stdout
                self.assertIn("INC_OK=true", loaded, f"Missing IncludeAnnotations in {fname}: {loaded}")
                self.assertIn("END_OK=true", loaded, f"Missing EndDateNotAllowed in {fname}: {loaded}")


if __name__ == "__main__":
    unittest.main(verbosity=2)
