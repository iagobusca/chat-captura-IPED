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

    private static final Set<String> VALID_JAR_HASHES = Set.of("CC528C445C408FEC084F04A971C44BC09947AEF1BDE571666BA4B32978707D55");
    private static final Set<String> VALID_EXE_HASHES = Set.of("52D3CC6079B8AEE8A9F7A105D36FCD82DE48CBCC83DDAB0F0AA9DF5FE83CF757");
    private static volatile boolean alertShown = false;

    /** Garante o verificador (jar/exe) atualizado na pasta de destino: adiciona se ausente ou substitui se desatualizado. */
    public static void installVerifierFiles(Path outputFolder) {
        if (outputFolder == null) return;
        Path normalizedRoot = outputFolder.toAbsolutePath().normalize();
        try {
            CaptureIntegrity.createDirectoriesSafe(normalizedRoot, normalizedRoot);
        } catch (IOException e) {
            return;
        }
        Path[] extraExistingDirs = new Path[] {
                normalizedRoot.resolve("integridade"), //$NON-NLS-1$
                normalizedRoot.resolve("verificador-integridade") //$NON-NLS-1$
        };
        for (String name : new String[] { JAR_NAME, EXE_NAME }) {
            ensureVerifierBinary(normalizedRoot, name, false);
            for (Path subDir : extraExistingDirs) {
                if (isNonLinkDirectory(subDir)) {
                    ensureVerifierBinary(subDir, name, true);
                }
            }
        }
    }

    private static void ensureVerifierBinary(Path targetDir, String name, boolean onlyIfExists) {
        if (targetDir == null || !isNonLinkDirectory(targetDir)) {
            return;
        }
        Path dst = targetDir.resolve(name);
        try {
            if (CaptureIntegrity.isLinkOrReparse(dst)) {
                return;
            }
        } catch (IOException e) {
            return;
        }
        boolean exists = Files.isRegularFile(dst, java.nio.file.LinkOption.NOFOLLOW_LINKS);
        if (!exists && (onlyIfExists || Files.exists(dst, java.nio.file.LinkOption.NOFOLLOW_LINKS))) {
            return;
        }
        if (exists) {
            try {
                if (isValid(name, sha256(dst))) {
                    return;
                }
            } catch (IOException ignored) {
                // Substitui o arquivo se a leitura/hash falhar.
            }
        }
        Path src = findTrustedVerifierFile(name);
        if (src == null) {
            return;
        }
        Path temp = null;
        try {
            temp = Files.createTempFile(targetDir, "." + name + ".", ".tmp"); //$NON-NLS-1$ //$NON-NLS-2$
            try (java.io.InputStream in = Files.newInputStream(src, java.nio.file.LinkOption.NOFOLLOW_LINKS);
                 java.io.OutputStream out = Files.newOutputStream(temp,
                         java.nio.file.StandardOpenOption.WRITE,
                         java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                         java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) >= 0) {
                    if (n > 0) out.write(buf, 0, n);
                }
            }
            if (!isValid(name, sha256(temp))) {
                return;
            }
            if (CaptureIntegrity.isLinkOrReparse(targetDir) || CaptureIntegrity.isLinkOrReparse(dst)) {
                return;
            }
            if (exists) {
                clearReadOnlyAttribute(dst);
            }
            moveReplacingWithRetry(temp, dst);
        } catch (IOException ignored) {
            // A falha ao copiar o verificador nao deve impedir a captura.
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static void clearReadOnlyAttribute(Path target) {
        try {
            Files.setAttribute(target, "dos:readonly", Boolean.FALSE, java.nio.file.LinkOption.NOFOLLOW_LINKS); //$NON-NLS-1$
        } catch (UnsupportedOperationException | IOException ignored) {
        }
    }

    private static void moveReplacingWithRetry(Path src, Path dst) throws IOException {
        IOException lastError = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                try {
                    Files.move(src, dst, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    Files.move(src, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            } catch (IOException e) {
                lastError = e;
                try {
                    Thread.sleep(50L * (attempt + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw lastError;
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
            try {
                if (CaptureIntegrity.isLinkOrReparse(dir)) continue;
                Path candidate = dir.resolve(name);
                if (CaptureIntegrity.isLinkOrReparse(candidate)
                        || !Files.isRegularFile(candidate, java.nio.file.LinkOption.NOFOLLOW_LINKS)) continue;
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
            if (isNonLinkDirectory(codeLocation)) {
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
                candidates.add(ipedRoot);
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
            if (isNonLinkDirectory(dir)) {
                if (isRegularNonLink(jar)) found.add(jar);
                if (isRegularNonLink(exe)) found.add(exe);
                // um nivel de subpasta "integridade"
                Path sub = dir.resolve("integridade");
                if (isNonLinkDirectory(sub)) {
                    Path j2 = sub.resolve(JAR_NAME);
                    Path e2 = sub.resolve(EXE_NAME);
                    if (isRegularNonLink(j2)) found.add(j2);
                    if (isRegularNonLink(e2)) found.add(e2);
                }
            }
            dir = dir.getParent();
        }
        return found;
    }

    private static boolean isRegularNonLink(Path path) {
        try {
            return path != null && !CaptureIntegrity.isLinkOrReparse(path)
                    && Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean isNonLinkDirectory(Path path) {
        try {
            return path != null && !CaptureIntegrity.isLinkOrReparse(path)
                    && Files.isDirectory(path, java.nio.file.LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            return false;
        }
    }

    private static String sha256(Path file) throws IOException {
        if (CaptureIntegrity.isLinkOrReparse(file)
                || !Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Arquivo de verificador invalido ou link simbolico: " + file);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (java.io.InputStream in = Files.newInputStream(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
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

    static String htmlEscape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
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
                sb.append("<br>- ").append(htmlEscape(String.valueOf(f.getFileName()))).append(": ")
                        .append(valid ? "VALIDO" : "INVALIDO/DESCONHECIDO").append(" (")
                        .append(htmlEscape(hash)).append(")");
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
                        java.net.URI uri = e.getURL().toURI();
                        if ("https".equalsIgnoreCase(uri.getScheme())) {
                            java.awt.Desktop.getDesktop().browse(uri);
                        }
                    } catch (Exception ignored) {
                    }
                }
            });
            JOptionPane.showMessageDialog(p, pane, "Informacao sobre o verificador de integridade",
                    JOptionPane.INFORMATION_MESSAGE);
        });
    }
}
