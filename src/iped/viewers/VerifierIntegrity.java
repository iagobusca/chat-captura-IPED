package iped.viewers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public final class VerifierIntegrity {

    private static final String JAR_NAME = "integridade-verifier.jar";
    private static final String EXE_NAME = "integridade-verifier.exe";

    private static final Set<String> VALID_JAR_HASHES = Set.of("906705C1A69F41505B0A794AA93F7E87D606FD62E5895A8FD2D3A9E0AA9E4B96");
    private static final Set<String> VALID_EXE_HASHES = Set.of("4F591C102058B0046814E9A5C9019E7483DB82A1E286BFF80DDC4CEA7E85A4EF");
    private static volatile boolean alertShown = false;

    /** Copia os arquivos do verificador (jar/exe) para a pasta de captura, se encontrados. */
    public static void installVerifierFiles(Path outputFolder) {
        if (outputFolder == null) return;
        Path srcDir = findVerifierSourceDir();
        if (srcDir == null) return;
        for (String name : new String[] { JAR_NAME, EXE_NAME }) {
            Path src = srcDir.resolve(name);
            Path dst = outputFolder.resolve(name);
            boolean needCopy = !Files.isRegularFile(dst);
            if (!needCopy) {
                try {
                    String h = sha256(dst);
                    if (!isValid(name, h)) needCopy = true;
                } catch (IOException e) {
                    needCopy = true;
                }
            }
            if (Files.isRegularFile(src) && needCopy) {
                try {
                    Files.copy(src, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {
                }
            }
        }
    }

    /** Procura a pasta do verificador (verificador-integridade) em locais conhecidos. */
    private static Path findVerifierSourceDir() {
        try {
            Path loc = VerifierIntegrity.class.getProtectionDomain().getCodeSource().getLocation().toURI() != null
                    ? java.nio.file.Paths.get(VerifierIntegrity.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    : null;
            if (loc != null) {
                Path ipedRoot = loc.getParent();
                if (ipedRoot != null) ipedRoot = ipedRoot.getParent();
                if (ipedRoot != null) {
                    Path cand = ipedRoot.resolve("lib");
                    if (Files.isRegularFile(cand.resolve(JAR_NAME)) || Files.isRegularFile(cand.resolve(EXE_NAME))) return cand;
                    cand = ipedRoot.resolve("verificador-integridade");
                    if (Files.isDirectory(cand)) return cand;
                    cand = ipedRoot.resolveSibling("verificador-integridade");
                    if (Files.isDirectory(cand)) return cand;
                    cand = ipedRoot.resolveSibling("plugins").resolve("chat-captura IPED").resolve("verificador-integridade");
                    if (Files.isDirectory(cand)) return cand;
                    if (ipedRoot.getParent() != null) {
                        cand = ipedRoot.getParent().resolve("plugins").resolve("chat-captura IPED").resolve("verificador-integridade");
                        if (Files.isDirectory(cand)) return cand;
                        cand = ipedRoot.getParent().resolve("verificador-integridade");
                        if (Files.isDirectory(cand)) return cand;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        // caminhos absolutos conhecidos
        for (String p : new String[] {
                "C:/Avilla-Forensics-Free/IPED-4.3.1_and_java_plugins/plugins/chat-captura IPED/verificador-integridade",
                "C:/Avilla-Forensics-Free/IPED-4.3.1_and_java_plugins/IPED_testes_chat_real/iped/lib",
                "F:/Download/Avilla-Acquisitions/IPED_v11/iped/../plugins/chat-captura IPED/verificador-integridade",
                "F:/Download/Avilla-Acquisitions/Prova_v3/iped/../plugins/chat-captura IPED/verificador-integridade",
                "F:/Download/Avilla-Acquisitions/Prova_v3/iped/lib" }) {
            Path cand = java.nio.file.Paths.get(p);
            if (Files.isDirectory(cand)) return cand;
        }
        return null;
    }

    /** Procura arquivos do verificador em outputFolder e ate 3 niveis de pais. */
    public static List<Path> findVerifierFiles(Path start) {
        List<Path> found = new ArrayList<>();
        if (start == null) {
            return found;
        }
        Path dir = start.toAbsolutePath().normalize();
        for (int level = 0; level <= 3 && dir != null; level++) {
            Path jar = dir.resolve(JAR_NAME);
            Path exe = dir.resolve(EXE_NAME);
            if (Files.isRegularFile(jar)) found.add(jar);
            if (Files.isRegularFile(exe)) found.add(exe);
            // um nivel de subpasta "integridade"
            Path sub = dir.resolve("integridade");
            if (Files.isDirectory(sub)) {
                Path j2 = sub.resolve(JAR_NAME);
                Path e2 = sub.resolve(EXE_NAME);
                if (Files.isRegularFile(j2)) found.add(j2);
                if (Files.isRegularFile(e2)) found.add(e2);
            }
            dir = dir.getParent();
        }
        return found;
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (java.io.InputStream in = Files.newInputStream(file)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) >= 0) {
                    if (n > 0) digest.update(buf, 0, n);
                }
            }
            byte[] h = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b & 0xff));
            return sb.toString().toUpperCase();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 indisponivel", e);
        }
    }

    private static boolean isValid(String name, String hash) {
        if (JAR_NAME.equalsIgnoreCase(name)) return VALID_JAR_HASHES.contains(hash.toUpperCase());
        if (EXE_NAME.equalsIgnoreCase(name)) return VALID_EXE_HASHES.contains(hash.toUpperCase());
        return false;
    }

    /** Exibe informativo uma vez por sessao (sempre, ao selecionar pasta de destino). */
    public static void showAlertIfNeeded(java.awt.Component parent, Path outputFolder) {
        if (alertShown) return;
        alertShown = true;
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='font-family:Segoe UI,Arial;font-size:11px;width:460px'>");
        sb.append("<b>Informacao sobre o verificador de integridade</b><br><br>");
        sb.append("Se voce recebeu este caso (pasta zipada) de terceiros e nao confia plenamente na origem, ");
        sb.append("o verificador presente pode ter sido adulterado ou substituido.<br><br>");
        sb.append("Para ter certeza da validade:<br>");
        sb.append("- Baixe o verificador do repositorio oficial: ");
        sb.append("<a href='https://github.com/iagobusca/chat-captura-IPED'>https://github.com/iagobusca/chat-captura-IPED</a><br>");
        sb.append("- Compare o hash SHA-256 do arquivo existente com o hash publicado no repositorio.<br>");
        sb.append("- Se quiser validar com maxima garantia, use o modo \"Integridade Publico\" do verificador, ");
        sb.append("informando a hash obtida de uma fonte em que voce confie.<br><br>");
        sb.append("Esta mensagem e apenas informativa: a captura/re-extracao continuara normalmente. ");
        sb.append("Nenhum arquivo foi alterado.");
        java.util.List<Path> files = findVerifierFiles(outputFolder);
        if (!files.isEmpty()) {
            sb.append("<br><br><b>Verificadores encontrados na pasta:</b>");
            for (Path f : files) {
                String hash = "(erro ao calcular)";
                boolean valid = false;
                try {
                    hash = sha256(f);
                    valid = isValid(f.getFileName().toString(), hash);
                } catch (IOException ignored) {
                }
                sb.append("<br>- ").append(f.getFileName()).append(": ")
                        .append(valid ? "VALIDO" : "INVALIDO/DESCONHECIDO").append(" (").append(hash).append(")");
            }
        }
        sb.append("</body></html>");
        final java.awt.Component p = parent;
        final String html = sb.toString();
        SwingUtilities.invokeLater(() -> {
            javax.swing.JTextPane pane = new javax.swing.JTextPane();
            pane.setContentType("text/html");
            pane.setText(html);
            pane.setEditable(false);
            pane.addHyperlinkListener(e -> {
                if (e.getEventType() == javax.swing.event.HyperlinkEvent.EventType.ACTIVATED) {
                    try {
                        java.awt.Desktop.getDesktop().browse(e.getURL().toURI());
                    } catch (Exception ignored) {
                    }
                }
            });
            JOptionPane.showMessageDialog(p, pane, "Informacao sobre o verificador de integridade",
                    JOptionPane.INFORMATION_MESSAGE);
        });
    }
}
