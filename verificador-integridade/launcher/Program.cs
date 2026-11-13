// Versão 1.3.6 — endurecimento de segurança (allowlist HTML quote-safe, proteção contra junções NTFS, arquivos extras em html-message-v1 e sanitização de histórico).
using System.Diagnostics;
using System.IO.Compression;
using System.Reflection;
using System.Security.Cryptography;
using System.Text;
using System.Windows.Forms;

internal static class Program
{
    private const string LockFileName = ".integridade-verifier.lock";

    [STAThread]
    private static int Main(string[] args)
    {
        string? tempRoot = null;
        FileStream? instanceLock = null;
        try
        {
            string exe = Environment.ProcessPath ?? throw new InvalidOperationException("Não foi possível localizar o executável.");
            string captureRoot = Path.GetDirectoryName(Path.GetFullPath(exe))!;
            string tempBase = Path.Combine(Path.GetTempPath(), "Avilla", "IntegridadeVerifier");
            Directory.CreateDirectory(tempBase);

            // Cada execução recebe uma pasta própria; processos paralelos nunca reutilizam runtime/app.
            tempRoot = Path.Combine(tempBase, $"instance-{Environment.ProcessId}-{Guid.NewGuid():N}");
            Directory.CreateDirectory(tempRoot);
            instanceLock = new FileStream(Path.Combine(tempRoot, LockFileName), FileMode.CreateNew, FileAccess.ReadWrite, FileShare.None);

            // Só limpa arquivos antigos quando não há outro verificador ativo.
            CleanupStaleTempDirectories(tempBase, tempRoot);

            using (Stream zip = Resource("payload.zip"))
            using (var archive = new ZipArchive(zip, ZipArchiveMode.Read))
            {
                foreach (ZipArchiveEntry entry in archive.Entries)
                {
                    string destination = Path.GetFullPath(Path.Combine(tempRoot, entry.FullName));
                    if (!destination.StartsWith(Path.GetFullPath(tempRoot) + Path.DirectorySeparatorChar, StringComparison.OrdinalIgnoreCase))
                        throw new InvalidDataException("O pacote contém um caminho inválido.");
                    bool isDir = entry.FullName.EndsWith("/") || entry.FullName.EndsWith("\\") || entry.Length == 0;
                    string destPath = destination.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
                    if (isDir)
                    {
                        Directory.CreateDirectory(destPath);
                    }
                    else
                    {
                        Directory.CreateDirectory(Path.GetDirectoryName(destPath)!);
                        entry.ExtractToFile(destPath, true);
                    }
                }
            }
            VerifyExtractedFiles(tempRoot);

            if (args.Contains("--self-test", StringComparer.OrdinalIgnoreCase))
            {
                int delay = GetSelfTestDelay(args);
                if (delay > 0) Thread.Sleep(delay);
                return 0;
            }

            string verifier = Path.Combine(tempRoot, "integridade-verifier.exe");
            var start = new ProcessStartInfo(verifier) { WorkingDirectory = captureRoot, UseShellExecute = false };
            start.ArgumentList.Add(captureRoot);
            using var process = Process.Start(start) ?? throw new InvalidOperationException("Não foi possível iniciar o verificador Java empacotado.");
            process.WaitForExit();
            return process.ExitCode;
        }
        catch (Exception ex)
        {
            Console.WriteLine("ERRO: " + ex);
            try { MessageBox.Show("Não foi possível preparar o verificador. Nenhum Java instalado é necessário; o runtime vem dentro deste EXE.\n\n" + ex.Message,
                "Erro no verificador de integridade", MessageBoxButtons.OK, MessageBoxIcon.Error); } catch { }
            return 1;
        }
        finally
        {
            instanceLock?.Dispose();
            if (tempRoot != null) { try { Directory.Delete(tempRoot, true); } catch { } }
        }
    }

    private static int GetSelfTestDelay(string[] args)
    {
        const string prefix = "--self-test-hold-ms=";
        string? value = args.FirstOrDefault(a => a.StartsWith(prefix, StringComparison.OrdinalIgnoreCase));
        return value != null && int.TryParse(value.Substring(prefix.Length), out int delay) ? Math.Clamp(delay, 0, 60000) : 0;
    }

    private static void CleanupStaleTempDirectories(string tempBase, string currentRoot)
    {
        if (HasOtherVerifierProcess(tempBase)) return;
        string currentFull = Path.GetFullPath(currentRoot).TrimEnd(Path.DirectorySeparatorChar);
        foreach (string directory in Directory.GetDirectories(tempBase))
        {
            string full = Path.GetFullPath(directory).TrimEnd(Path.DirectorySeparatorChar);
            if (full.Equals(currentFull, StringComparison.OrdinalIgnoreCase) || !IsVerifierTempDirectory(Path.GetFileName(full))) continue;
            try
            {
                var info = new DirectoryInfo(full);
                if ((info.Attributes & FileAttributes.ReparsePoint) != 0) continue;

                // A marker held by another process means that directory is still in use.
                string lockPath = Path.Combine(full, LockFileName);
                if (File.Exists(lockPath))
                {
                    using var probe = new FileStream(lockPath, FileMode.Open, FileAccess.ReadWrite, FileShare.None);
                }
                Directory.Delete(full, true);
            }
            catch { /* Preserve anything that is locked, inaccessible, or not fully stale. */ }
        }
    }

    private static bool IsVerifierTempDirectory(string name) =>
        name.Equals("execucao-atual", StringComparison.OrdinalIgnoreCase) ||
        name.Equals("app", StringComparison.OrdinalIgnoreCase) ||
        name.Equals("runtime", StringComparison.OrdinalIgnoreCase) ||
        name.StartsWith("app_", StringComparison.OrdinalIgnoreCase) ||
        name.StartsWith("runtime_", StringComparison.OrdinalIgnoreCase) ||
        name.StartsWith("instance-", StringComparison.OrdinalIgnoreCase) ||
        name.StartsWith("instancia-", StringComparison.OrdinalIgnoreCase);

    private static bool HasOtherVerifierProcess(string tempBase)
    {
        string ownName = Path.GetFileNameWithoutExtension(Environment.ProcessPath ?? "integridade-verifier");
        string fullTempBase = Path.GetFullPath(tempBase).TrimEnd(Path.DirectorySeparatorChar) + Path.DirectorySeparatorChar;
        foreach (Process process in Process.GetProcesses())
        {
            using (process)
            {
                if (process.Id == Environment.ProcessId) continue;
                try
                {
                    string processName = process.ProcessName;
                    if (processName.Equals(ownName, StringComparison.OrdinalIgnoreCase) ||
                        processName.Equals("integridade-verifier", StringComparison.OrdinalIgnoreCase)) return true;
                    if (processName.Equals("java", StringComparison.OrdinalIgnoreCase) || processName.Equals("javaw", StringComparison.OrdinalIgnoreCase))
                    {
                        string? executable = process.MainModule?.FileName;
                        if (executable != null && Path.GetFullPath(executable).StartsWith(fullTempBase, StringComparison.OrdinalIgnoreCase)) return true;
                    }
                }
                catch { /* Some system processes do not expose their name or executable path. */ }
            }
        }
        return false;
    }

    private static Stream Resource(string name) => Assembly.GetExecutingAssembly().GetManifestResourceStream(name)
        ?? throw new InvalidOperationException("Recurso embutido ausente: " + name);

    private static void VerifyExtractedFiles(string root)
    {
        using Stream manifest = Resource("payload.sha256");
        using var reader = new StreamReader(manifest, Encoding.UTF8);
        int checkedFiles = 0;
        while (reader.ReadLine() is string line)
        {
            if (string.IsNullOrWhiteSpace(line)) continue;
            string[] fields = line.Split('|', 2);
            if (fields.Length != 2 || fields[0].Length != 64) throw new InvalidDataException("Manifesto interno de hashes inválido.");
            string path = fields[1].Replace('/', Path.DirectorySeparatorChar);
            if (Path.IsPathRooted(path) || path.Split(Path.DirectorySeparatorChar).Any(p => p == "..")) throw new InvalidDataException("Caminho inseguro no manifesto.");
            string full = Path.GetFullPath(Path.Combine(root, path));
            if (!full.StartsWith(Path.GetFullPath(root) + Path.DirectorySeparatorChar, StringComparison.OrdinalIgnoreCase) || !File.Exists(full))
                throw new InvalidDataException("Arquivo do runtime ausente: " + path);
            using var input = File.OpenRead(full);
            string actual = Convert.ToHexString(SHA256.HashData(input));
            if (!actual.Equals(fields[0], StringComparison.OrdinalIgnoreCase)) throw new InvalidDataException("Hash inválido no runtime extraído: " + path);
            checkedFiles++;
        }
        if (checkedFiles == 0) throw new InvalidDataException("O manifesto interno está vazio.");
    }
}
