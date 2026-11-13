package iped.viewers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public final class VerifierIntegrity {

    private static final String JAR_NAME = "integridade-verifier.jar";
    private static final String EXE_NAME = "integridade-verifier.exe";

    private static final Set<String> VALID_JAR_HASHES = Set.of("F7990400A98445B8FA718626DA08145CD974866487650BD443E95427DD015FAC");
    private static final Set<String> VALID_EXE_HASHES = Set.of("24E84B1B014D9677E238A94FD9EBFFB5CD2E8CF5CCA244AF8D47D1ED48C704D4");
    private static volatile boolean alertShown = false;

    /** Copia os arquivos do verificador (jar/exe) para a pasta de captura, se encontrados. */
    public static void installVerifierFiles(Path outputFolder) {
        if (outputFolder == null) return;
        try {
            Files.createDirectories(outputFolder);
        } catch (IOException e) {
            return;
        }
        for (String name : new String[] { JAR_NAME, EXE_NAME }) {
            Path src = findTrustedVerifierFile(name);
            if (src == null) continue;
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
            if (!needCopy) continue;
            Path temp = outputFolder.resolve(name + ".tmp-" + UUID.randomUUID().toString()); //$NON-NLS-1$
            try {
                Files.copy(src, temp);
                if (!isValid(name, sha256(temp))) continue;
                try {
                    Files.move(temp, dst, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    Files.move(temp, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ignored) {
                // A falha ao copiar o verificador nao deve impedir a captura.
            } finally {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                }
            }
        }
    }

    /** Procura somente artefatos com SHA-256 explicitamente autorizado pelo plugin. */
    private static Path findTrustedVerifierFile(String name) {
        return findTrustedVerifierFile(name, verifierSourceDirs());
    }

    static Path findTrustedVerifierFile(String name, List<Path> sourceDirs) {
        if (name == null || name.isBlank() || sourceDirs == null) {
            return null;
        }
        for (Path dir : sourceDirs) {
            if (dir == null) {
                continue;
            }
            Path candidate = dir.resolve(name);
            if (!Files.isRegularFile(candidate)) continue;
            try {
                if (isValid(name, sha256(candidate))) return candidate;
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    /** Locais conhecidos relativos ao JAR instalado do plugin e overrides explicitos. */
    private static List<Path> verifierSourceDirs() {
        Path codeLocation = null;
        try {
            java.security.CodeSource cs = VerifierIntegrity.class.getProtectionDomain().getCodeSource();
            if (cs != null && cs.getLocation() != null && cs.getLocation().toURI() != null) {
                codeLocation = java.nio.file.Paths.get(cs.getLocation().toURI());
            }
        } catch (Exception ignored) {
        }
        LinkedHashSet<Path> candidates = new LinkedHashSet<>(verifierSourceDirs(codeLocation));
        candidates.add(java.nio.file.Paths.get("verificador-integridade").toAbsolutePath().normalize()); //$NON-NLS-1$
        return new ArrayList<>(candidates);
    }

    static List<Path> verifierSourceDirs(Path codeLocation) {
        LinkedHashSet<Path> candidates = new LinkedHashSet<>();
        String explicitDir = System.getProperty("iped.verifier.dir"); //$NON-NLS-1$
        if (explicitDir != null && !explicitDir.isBlank()) {
            candidates.add(java.nio.file.Paths.get(explicitDir));
        }
        if (codeLocation != null) {
            if (Files.isDirectory(codeLocation)) {
                candidates.add(codeLocation.resolve("verificador-integridade")); //$NON-NLS-1$
                candidates.add(codeLocation.resolveSibling("verificador-integridade")); //$NON-NLS-1$
            }
            Path ipedRoot = codeLocation.getParent();
            if (ipedRoot != null) {
                candidates.add(ipedRoot.resolve("verificador-integridade")); //$NON-NLS-1$
                ipedRoot = ipedRoot.getParent();
            }
            if (ipedRoot != null) {
                candidates.add(ipedRoot.resolve("verificador-integridade")); //$NON-NLS-1$
                candidates.add(ipedRoot.resolve("lib")); //$NON-NLS-1$
                candidates.add(ipedRoot.resolve("plugins").resolve("chat-captura IPED").resolve("verificador-integridade")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                candidates.add(ipedRoot.resolveSibling("verificador-integridade")); //$NON-NLS-1$
                candidates.add(ipedRoot.resolveSibling("plugins").resolve("chat-captura IPED").resolve("verificador-integridade")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                if (ipedRoot.getParent() != null) {
                    candidates.add(ipedRoot.getParent().resolve("plugins").resolve("chat-captura IPED").resolve("verificador-integridade")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    candidates.add(ipedRoot.getParent().resolve("verificador-integridade")); //$NON-NLS-1$
                    if (ipedRoot.getParent().getParent() != null) {
                        candidates.add(ipedRoot.getParent().getParent().resolve("plugins").resolve("chat-captura IPED").resolve("verificador-integridade")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    }
                }
            }
        }
        List<Path> normalized = new ArrayList<>();
        for (Path candidate : candidates) {
            Path path = candidate.toAbsolutePath().normalize();
            if (!normalized.contains(path)) normalized.add(path);
        }
        return normalized;
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
