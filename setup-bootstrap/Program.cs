using System;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using System.Runtime.InteropServices;

internal static class Program
{
    private const int ErrorIcon = 0x10;
    private const int OkButton = 0x00;

    private static readonly PayloadFile[] Payload = new PayloadFile[]
    {
        new PayloadFile("Setup.hta", "Setup.hta"),
        new PayloadFile("Physlaunch.hta", Path.Combine("app", "Physlaunch.hta")),
        new PayloadFile("PhyslaunchRunner.vbs", Path.Combine("app", "PhyslaunchRunner.vbs")),
        new PayloadFile("assets/physlaunch.ico", Path.Combine("assets", "physlaunch.ico")),
        new PayloadFile("assets/physlaunch.png", Path.Combine("assets", "physlaunch.png")),
        new PayloadFile("LICENSE", "LICENSE")
    };

    [STAThread]
    private static int Main(string[] args)
    {
        if (args.Length == 1 && args[0] == "--check-package")
        {
            return CheckPayload();
        }

        string? stagingDirectory = null;
        try
        {
            string mshta = Path.Combine(Environment.SystemDirectory, "mshta.exe");
            if (!File.Exists(mshta))
            {
                throw new FileNotFoundException("Windows MSHTA could not be found.", mshta);
            }

            string parent = Path.Combine(Path.GetTempPath(), "PhyslaunchSetup");
            Directory.CreateDirectory(parent);
            stagingDirectory = Path.Combine(parent, Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(stagingDirectory);
            ExtractPayload(stagingDirectory);

            string setupPage = Path.Combine(stagingDirectory, "Setup.hta");
            ProcessStartInfo startInfo = new ProcessStartInfo();
            startInfo.FileName = mshta;
            startInfo.Arguments = QuoteArgument(setupPage);
            startInfo.WorkingDirectory = stagingDirectory;
            startInfo.UseShellExecute = false;

            Process? process = Process.Start(startInfo);
            if (process == null)
            {
                throw new InvalidOperationException("Windows could not start Setup.");
            }

            using (process)
            {
                process.WaitForExit();
                return process.ExitCode;
            }
        }
        catch (Exception error)
        {
            ShowError("Physlaunch Setup could not start.\n\n" + error.Message);
            return 1;
        }
        finally
        {
            if (stagingDirectory != null)
            {
                try
                {
                    Directory.Delete(stagingDirectory, true);
                }
                catch (IOException)
                {
                    // Windows can briefly hold an extracted file open after MSHTA exits.
                }
                catch (UnauthorizedAccessException)
                {
                    // A later cleanup can remove files left by antivirus scanning.
                }
            }
        }
    }

    private static int CheckPayload()
    {
        Assembly assembly = Assembly.GetExecutingAssembly();
        foreach (PayloadFile file in Payload)
        {
            using (Stream? resource = assembly.GetManifestResourceStream(file.ResourceName))
            {
                if (resource == null || resource.Length == 0)
                {
                    return 1;
                }
            }
        }

        return 0;
    }

    private static void ExtractPayload(string destinationRoot)
    {
        Assembly assembly = Assembly.GetExecutingAssembly();
        string fullRoot = Path.GetFullPath(destinationRoot).TrimEnd(Path.DirectorySeparatorChar)
            + Path.DirectorySeparatorChar;

        foreach (PayloadFile file in Payload)
        {
            string destination = Path.GetFullPath(Path.Combine(destinationRoot, file.RelativePath));
            if (!destination.StartsWith(fullRoot, StringComparison.OrdinalIgnoreCase))
            {
                throw new InvalidOperationException("The setup payload contains an invalid path.");
            }

            string? destinationDirectory = Path.GetDirectoryName(destination);
            if (destinationDirectory == null)
            {
                throw new InvalidOperationException("Setup could not determine a payload folder.");
            }

            Directory.CreateDirectory(destinationDirectory);
            using (Stream? resource = assembly.GetManifestResourceStream(file.ResourceName))
            {
                if (resource == null)
                {
                    throw new InvalidOperationException("Setup is missing " + file.ResourceName + ".");
                }

                using (FileStream output = new FileStream(destination, FileMode.CreateNew, FileAccess.Write))
                {
                    resource.CopyTo(output);
                }
            }
        }
    }

    private static string QuoteArgument(string value)
    {
        return "\"" + value.Replace("\"", "\\\"") + "\"";
    }

    private static void ShowError(string message)
    {
        MessageBox(IntPtr.Zero, message, "Physlaunch Setup", OkButton | ErrorIcon);
    }

    [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern int MessageBox(IntPtr window, string text, string caption, int type);

    private sealed class PayloadFile
    {
        public string ResourceName { get; private set; }
        public string RelativePath { get; private set; }

        public PayloadFile(string resourceName, string relativePath)
        {
            ResourceName = resourceName;
            RelativePath = relativePath;
        }
    }
}
