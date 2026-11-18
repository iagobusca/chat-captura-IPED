// Versão 1.5.6 — corte automatizado + empacotamento determinístico (.NET 10.0.15).
using System.Diagnostics;
using System.IO.Compression;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;
using System.Windows.Forms;

internal static class Program
{
    private const string LockFileName = ".integridade-verifier.lock";
    private const int ATTACH_PARENT_PROCESS = -1;

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool AttachConsole(int dwProcessId);

    [STAThread]
    private static int Main(string[] args)
    {
        bool selfTest = IsSelfTest(args);
        bool isCliMode = args.Any(IsCliFlag);
        bool hasAttachedConsole = false;
        if (isCliMode || selfTest)
        {
            hasAttachedConsole = AttachParentConsoleIfAvailable();
        }
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
                ExtractArchiveSafely(archive, tempRoot);
            }
            VerifyExtractedFiles(tempRoot);

            if (selfTest)
            {
                RunLauncherSelfTest();
                int selfTestExit = RunBundledJarCli(tempRoot, captureRoot, hasAttachedConsole, new[] { "--self-test" });
                if (selfTestExit != 0)
                {
                    return selfTestExit;
                }
                int delay = GetSelfTestDelay(args);
                if (delay > 0) Thread.Sleep(delay);
                return 0;
            }

            if (isCliMode)
            {
                List<string> cliArgs = new List<string>(args);
                bool needsCaptureRoot = args.Any(a =>
                    string.Equals(a, "--verify-only", StringComparison.OrdinalIgnoreCase) ||
                    string.Equals(a, "--export-history", StringComparison.OrdinalIgnoreCase));
                bool hasPositional = args.Any(a => !IsCliFlag(a));
                if (needsCaptureRoot && !hasPositional)
                {
                    cliArgs.Add(captureRoot);
                }
                return RunBundledJarCli(tempRoot, captureRoot, hasAttachedConsole, cliArgs);
            }

            string verifier = Path.Combine(tempRoot, "integridade-verifier.exe");
            var start = new ProcessStartInfo(verifier) { WorkingDirectory = captureRoot, UseShellExecute = false };
            if (args.Length > 0)
            {
                foreach (string arg in args)
                {
                    start.ArgumentList.Add(arg);
                }
            }
            else
            {
                start.ArgumentList.Add(captureRoot);
            }
            using var process = Process.Start(start) ?? throw new InvalidOperationException("Não foi possível iniciar o verificador Java empacotado.");
            process.WaitForExit();
            return process.ExitCode;
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine("ERRO: " + ex.Message);
            if (!isCliMode && !selfTest)
            {
                try
                {
                    using var topOwner = new Form { TopMost = true, ShowInTaskbar = false, StartPosition = FormStartPosition.CenterScreen };
                    MessageBox.Show(topOwner, "Não foi possível preparar o verificador. Nenhum Java instalado é necessário; o runtime vem dentro deste EXE.\n\n" + ex.Message,
                        "Erro no verificador de integridade", MessageBoxButtons.OK, MessageBoxIcon.Error);
                }
                catch { }
            }
            return 1;
        }
        finally
        {
            instanceLock?.Dispose();
            if (tempRoot != null) { try { DeleteDirectorySafely(tempRoot); } catch { } }
        }
    }

    private static bool IsCliFlag(string? arg)
    {
        if (string.IsNullOrWhiteSpace(arg))
        {
            return false;
        }
        string trimmed = arg.Trim();
        return trimmed.StartsWith("-", StringComparison.Ordinal) || string.Equals(trimmed, "/?", StringComparison.Ordinal);
    }

    private static bool IsSelfTest(string[] args) =>
        args.Any(a => string.Equals(a, "--self-test", StringComparison.OrdinalIgnoreCase)
            || a.StartsWith("--self-test-hold-ms=", StringComparison.OrdinalIgnoreCase));

    private static bool AttachParentConsoleIfAvailable()
    {
        try
        {
            if (Console.IsOutputRedirected && Console.IsErrorRedirected)
            {
                return false;
            }
            if (!AttachConsole(ATTACH_PARENT_PROCESS))
            {
                return false;
            }
            if (!Console.IsOutputRedirected)
            {
                Console.SetOut(new StreamWriter(Console.OpenStandardOutput(), new UTF8Encoding(false)) { AutoFlush = true });
            }
            if (!Console.IsErrorRedirected)
            {
                Console.SetError(new StreamWriter(Console.OpenStandardError(), new UTF8Encoding(false)) { AutoFlush = true });
            }
            return true;
        }
        catch
        {
            return false;
        }
    }

    private static int RunBundledJarCli(string tempRoot, string captureRoot, bool hasAttachedConsole, IEnumerable<string> cliArgs)
    {
        string javaExe = Path.Combine(tempRoot, "runtime", "bin", "java.exe");
        string verifierJar = Path.Combine(tempRoot, "app", "integridade-verifier.jar");
        if (!File.Exists(javaExe) || !File.Exists(verifierJar))
        {
            throw new FileNotFoundException("Runtime ou JAR CLI empacotado não encontrado na instância extraída.");
        }
        Encoding ioEncoding = Encoding.UTF8;
        if (hasAttachedConsole || Console.IsOutputRedirected)
        {
            try { Console.OutputEncoding = ioEncoding; } catch { }
        }
        var start = new ProcessStartInfo(javaExe)
        {
            WorkingDirectory = captureRoot,
            UseShellExecute = false,
            CreateNoWindow = true,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            StandardOutputEncoding = ioEncoding,
            StandardErrorEncoding = ioEncoding
        };
        start.ArgumentList.Add("-Dfile.encoding=UTF-8");
        start.ArgumentList.Add("-jar");
        start.ArgumentList.Add(verifierJar);
        foreach (string arg in cliArgs)
        {
            start.ArgumentList.Add(arg);
        }
        using var process = Process.Start(start) ?? throw new InvalidOperationException("Não foi possível iniciar o verificador Java CLI.");
        var stdoutTask = PumpStreamAsync(process.StandardOutput, Console.Out);
        var stderrTask = PumpStreamAsync(process.StandardError, Console.Error);
        process.WaitForExit();
        Task.WaitAll(stdoutTask, stderrTask);
        Console.Out.Flush();
        Console.Error.Flush();
        return process.ExitCode;
    }

    private static async Task PumpStreamAsync(StreamReader reader, TextWriter writer)
    {
        char[] buffer = new char[4096];
        int read;
        while ((read = await reader.ReadAsync(buffer, 0, buffer.Length).ConfigureAwait(false)) > 0)
        {
            await writer.WriteAsync(buffer, 0, read).ConfigureAwait(false);
        }
        await writer.FlushAsync().ConfigureAwait(false);
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
        DateTime nowUtc = DateTime.UtcNow;
        foreach (string directory in Directory.GetDirectories(tempBase))
        {
            string full = Path.GetFullPath(directory).TrimEnd(Path.DirectorySeparatorChar);
            if (full.Equals(currentFull, StringComparison.OrdinalIgnoreCase) || !IsVerifierTempDirectory(Path.GetFileName(full))) continue;
            try
            {
                if (!ShouldCleanupStaleTempDirectory(full, nowUtc)) continue;
                DeleteDirectorySafely(full);
            }
            catch { /* Preserve anything that is locked, inaccessible, or not fully stale. */ }
        }
    }

    private const int MaxArchiveEntries = 4096;
    private const long MaxArchiveTotalBytes = 512L * 1024L * 1024L;

    private static void EnsureNoReparsePointHierarchy(string rootDir, string targetPath)
    {
        string normalizedRoot = Path.GetFullPath(rootDir).TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
        string normalizedTarget = Path.GetFullPath(targetPath).TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
        var rootInfo = new DirectoryInfo(normalizedRoot);
        if (rootInfo.Exists && (rootInfo.Attributes & FileAttributes.ReparsePoint) != 0)
            throw new InvalidDataException("Diretório raiz não pode ser um link simbólico ou junção NTFS.");
        bool sameRoot = normalizedTarget.Equals(normalizedRoot, StringComparison.OrdinalIgnoreCase);
        if (!sameRoot && !normalizedTarget.StartsWith(normalizedRoot + Path.DirectorySeparatorChar, StringComparison.OrdinalIgnoreCase))
            throw new InvalidDataException("O pacote contém um caminho inválido.");
        if (sameRoot) return;
        string relative = normalizedTarget.Substring(normalizedRoot.Length).TrimStart(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
        string current = normalizedRoot;
        foreach (string segment in relative.Split(new[] { Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar }, StringSplitOptions.RemoveEmptyEntries))
        {
            current = Path.Combine(current, segment);
            if (File.Exists(current) || Directory.Exists(current))
            {
                FileAttributes attrs = File.GetAttributes(current);
                if ((attrs & FileAttributes.ReparsePoint) != 0)
                    throw new InvalidDataException("Link simbólico ou junção NTFS não permitido durante a extração/verificação.");
            }
        }
    }

    private static void ExtractArchiveSafely(ZipArchive archive, string rootDir, int maxEntries = MaxArchiveEntries, long maxTotalBytes = MaxArchiveTotalBytes)
    {
        string normalizedRoot = Path.GetFullPath(rootDir).TrimEnd(Path.DirectorySeparatorChar) + Path.DirectorySeparatorChar;
        EnsureNoReparsePointHierarchy(rootDir, rootDir);
        int entryCount = 0;
        long totalBytes = 0;
        byte[] copyBuffer = new byte[81920];
        foreach (ZipArchiveEntry entry in archive.Entries)
        {
            entryCount++;
            if (entryCount > maxEntries)
                throw new InvalidDataException("O pacote excede o número máximo de entradas permitidas.");
            string destination = Path.GetFullPath(Path.Combine(rootDir, entry.FullName));
            if (!destination.StartsWith(normalizedRoot, StringComparison.OrdinalIgnoreCase))
                throw new InvalidDataException("O pacote contém um caminho inválido.");
            bool isDir = entry.FullName.EndsWith("/") || entry.FullName.EndsWith("\\") || string.IsNullOrEmpty(entry.Name);
            string destPath = destination.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
            EnsureNoReparsePointHierarchy(rootDir, destPath);
            if (isDir)
            {
                Directory.CreateDirectory(destPath);
                EnsureNoReparsePointHierarchy(rootDir, destPath);
            }
            else
            {
                string parentDir = Path.GetDirectoryName(destPath)!;
                Directory.CreateDirectory(parentDir);
                EnsureNoReparsePointHierarchy(rootDir, destPath);
                using Stream entryStream = entry.Open();
                using var output = new FileStream(destPath, FileMode.Create, FileAccess.Write, FileShare.None);
                int read;
                while ((read = entryStream.Read(copyBuffer, 0, copyBuffer.Length)) > 0)
                {
                    totalBytes += read;
                    if (totalBytes > maxTotalBytes)
                        throw new InvalidDataException("O pacote excede o limite máximo de bytes descomprimidos.");
                    output.Write(copyBuffer, 0, read);
                }
            }
        }
    }

    private static bool ShouldCleanupStaleTempDirectory(string fullPath, DateTime nowUtc)
    {
        var info = new DirectoryInfo(fullPath);
        if (!info.Exists || (info.Attributes & FileAttributes.ReparsePoint) != 0) return false;
        string lockPath = Path.Combine(fullPath, LockFileName);
        if (File.Exists(lockPath))
        {
            // A marker held by another process throws on exclusive open.
            using (var probe = new FileStream(lockPath, FileMode.Open, FileAccess.ReadWrite, FileShare.None))
            {
            }
            return true;
        }
        DateTime lastTouchUtc = info.CreationTimeUtc > info.LastWriteTimeUtc ? info.CreationTimeUtc : info.LastWriteTimeUtc;
        return nowUtc - lastTouchUtc >= TimeSpan.FromMinutes(2);
    }

    private static void DeleteDirectorySafely(string rootPath)
    {
        var dir = new DirectoryInfo(rootPath);
        if (!dir.Exists || (dir.Attributes & FileAttributes.ReparsePoint) != 0) return;
        foreach (FileInfo file in dir.GetFiles())
        {
            if ((file.Attributes & FileAttributes.ReparsePoint) == 0 && (file.Attributes & FileAttributes.ReadOnly) != 0)
            {
                file.Attributes &= ~FileAttributes.ReadOnly;
            }
            file.Delete();
        }
        foreach (DirectoryInfo sub in dir.GetDirectories())
        {
            if ((sub.Attributes & FileAttributes.ReparsePoint) != 0) continue;
            DeleteDirectorySafely(sub.FullName);
        }
        if ((dir.Attributes & FileAttributes.ReadOnly) != 0)
        {
            dir.Attributes &= ~FileAttributes.ReadOnly;
        }
        if (dir.GetFileSystemInfos().Length == 0)
        {
            dir.Delete(false);
        }
    }

    private static void RunLauncherSelfTest()
    {
        string scratch = Path.Combine(Path.GetTempPath(), $"integridade-launcher-selftest-{Guid.NewGuid():N}");
        try
        {
            string extractRoot = Path.Combine(scratch, "extract");
            Directory.CreateDirectory(extractRoot);
            using (var buffer = new MemoryStream())
            {
                using (var creator = new ZipArchive(buffer, ZipArchiveMode.Create, leaveOpen: true))
                {
                    creator.CreateEntry("sub/");
                    creator.CreateEntry("sub/empty.txt");
                    ZipArchiveEntry dataEntry = creator.CreateEntry("sub/ok.txt");
                    using var writer = new StreamWriter(dataEntry.Open(), new UTF8Encoding(false));
                    writer.Write("ok");
                }
                buffer.Position = 0;
                using var readerArchive = new ZipArchive(buffer, ZipArchiveMode.Read);
                ExtractArchiveSafely(readerArchive, extractRoot);
            }
            string emptyFile = Path.Combine(extractRoot, "sub", "empty.txt");
            string dataFile = Path.Combine(extractRoot, "sub", "ok.txt");
            if (!Directory.Exists(Path.Combine(extractRoot, "sub")) || !File.Exists(emptyFile) || new FileInfo(emptyFile).Length != 0)
                throw new InvalidOperationException("Falha no autoteste de extração de arquivo ZIP vazio.");
            if (!File.Exists(dataFile) || File.ReadAllText(dataFile, Encoding.UTF8) != "ok")
                throw new InvalidOperationException("Falha no autoteste de extração de arquivo ZIP.");

            using (var overflowBuffer = new MemoryStream())
            {
                using (var creator = new ZipArchive(overflowBuffer, ZipArchiveMode.Create, leaveOpen: true))
                {
                    ZipArchiveEntry bigEntry = creator.CreateEntry("big.txt");
                    using var w = new StreamWriter(bigEntry.Open(), new UTF8Encoding(false));
                    w.Write("1234567890");
                }
                overflowBuffer.Position = 0;
                using var readerOverflow = new ZipArchive(overflowBuffer, ZipArchiveMode.Read);
                bool rejected = false;
                try { ExtractArchiveSafely(readerOverflow, Path.Combine(scratch, "overflow"), maxTotalBytes: 4); }
                catch (InvalidDataException) { rejected = true; }
                if (!rejected) throw new InvalidOperationException("Falha no autoteste de limite de descompressão ZIP.");
            }

            string freshDir = Path.Combine(scratch, "instance-fresh");
            string staleDir = Path.Combine(scratch, "instance-stale");
            Directory.CreateDirectory(freshDir);
            Directory.CreateDirectory(staleDir);
            DateTime oldTime = DateTime.UtcNow.AddMinutes(-5);
            Directory.SetCreationTimeUtc(staleDir, oldTime);
            Directory.SetLastWriteTimeUtc(staleDir, oldTime);
            if (ShouldCleanupStaleTempDirectory(freshDir, DateTime.UtcNow))
                throw new InvalidOperationException("Diretório recente sem lock não deve ser removido antes da janela de tolerância.");
            if (!ShouldCleanupStaleTempDirectory(staleDir, DateTime.UtcNow))
                throw new InvalidOperationException("Diretório antigo sem lock deve ser elegível para limpeza.");

            DeleteDirectorySafely(scratch);
            if (Directory.Exists(scratch))
                throw new InvalidOperationException("Falha na limpeza segura de diretório temporário.");

            bool siblingRejected = false;
            try { EnsureNoReparsePointHierarchy(Path.Combine(scratch, "root"), Path.Combine(scratch, "root-sibling", "x")); }
            catch (InvalidDataException) { siblingRejected = true; }
            if (!siblingRejected)
                throw new InvalidOperationException("Falha no autoteste de fronteira de caminho irmão.");
        }
        finally
        {
            if (Directory.Exists(scratch)) { try { DeleteDirectorySafely(scratch); } catch { } }
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
            string normalizedRoot = Path.GetFullPath(root).TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar) + Path.DirectorySeparatorChar;
            string full = Path.GetFullPath(Path.Combine(root, path));
            if (!full.StartsWith(normalizedRoot, StringComparison.OrdinalIgnoreCase) || !File.Exists(full))
                throw new InvalidDataException("Arquivo do runtime ausente: " + path);
            EnsureNoReparsePointHierarchy(root, full);
            using var input = File.OpenRead(full);
            string actual = Convert.ToHexString(SHA256.HashData(input));
            if (!actual.Equals(fields[0], StringComparison.OrdinalIgnoreCase)) throw new InvalidDataException("Hash inválido no runtime extraído: " + path);
            checkedFiles++;
        }
        if (checkedFiles == 0) throw new InvalidDataException("O manifesto interno está vazio.");
    }
}
