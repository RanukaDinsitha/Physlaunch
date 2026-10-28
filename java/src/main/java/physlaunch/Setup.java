package physlaunch;

import java.awt.*;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.Locale;
import java.util.prefs.Preferences;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Installs the self-contained LauncherHub bundle without requiring administrator access. */
public final class Setup {
  private static final Preferences PREFERENCES = Preferences.userNodeForPackage(Setup.class);
  private static JTextField location;
  private static JCheckBox shortcut, runner, association;
  private static JFrame frame;

  private Setup() {}

  public static void main(String[] args) {
    if (args.length == 1 && args[0].equals("--check-package")) {
      checkPackage();
      return;
    }
    SwingUtilities.invokeLater(Setup::showWindow);
  }

  private static void checkPackage() {
    try {
      Path image = findLauncherImage();
      if (image == null) throw new IOException("LauncherHub is not beside this Setup bundle.");
      Path executable = launcherExecutable(image);
      if (!Files.isRegularFile(executable))
        throw new IOException("LauncherHub executable is missing: " + executable);
      System.out.println("Setup found LauncherHub at " + executable);
    } catch (Exception e) {
      System.err.println("Setup bundle check failed: " + e.getMessage());
      System.exit(1);
    }
  }

  private static void showWindow() {
    frame = new JFrame("Physlaunch Setup");
    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    frame.setSize(620, 570);
    frame.setMinimumSize(new Dimension(560, 520));
    frame.setLocationRelativeTo(null);
    frame.setIconImage(icon());
    JPanel root = new JPanel();
    root.setBackground(new Color(23, 26, 33));
    root.setBorder(new EmptyBorder(24, 28, 24, 28));
    root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
    JPanel header = new JPanel(new BorderLayout(12, 0));
    header.setOpaque(false);
    JLabel logo = new JLabel(new ImageIcon(icon()));
    JLabel title = new JLabel("Physlaunch Setup");
    title.setForeground(Color.WHITE);
    title.setFont(new Font("Segoe UI", Font.BOLD, 24));
    JLabel tag = new JLabel("Turn any drive into a game launcher");
    tag.setForeground(new Color(143, 152, 160));
    JPanel captions = new JPanel();
    captions.setOpaque(false);
    captions.setLayout(new BoxLayout(captions, BoxLayout.Y_AXIS));
    captions.add(title);
    captions.add(tag);
    header.add(logo, BorderLayout.WEST);
    header.add(captions, BorderLayout.CENTER);
    root.add(header);
    root.add(Box.createVerticalStrut(25));
    JLabel intro =
        new JLabel("Install Physlaunch for your account. No administrator access is needed.");
    intro.setForeground(new Color(199, 213, 224));
    intro.setAlignmentX(Component.LEFT_ALIGNMENT);
    root.add(intro);
    root.add(Box.createVerticalStrut(18));
    JLabel locLabel = new JLabel("Install location");
    style(locLabel, true);
    root.add(locLabel);
    JPanel pathRow = new JPanel(new BorderLayout(8, 0));
    pathRow.setOpaque(false);
    location = new JTextField(PREFERENCES.get("installDir", defaultInstall().toString()));
    location.setBackground(new Color(27, 40, 56));
    location.setForeground(Color.WHITE);
    location.setCaretColor(Color.WHITE);
    pathRow.add(location, BorderLayout.CENTER);
    JButton browse = new JButton("Browse…");
    browse.addActionListener(e -> browse());
    pathRow.add(browse, BorderLayout.EAST);
    pathRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
    pathRow.setAlignmentX(Component.LEFT_ALIGNMENT);
    root.add(pathRow);
    root.add(Box.createVerticalStrut(20));
    JLabel options = new JLabel("Options");
    style(options, true);
    root.add(options);
    shortcut = check("shortcut", "Create a desktop shortcut");
    runner = check("runner", "Start the drive Runner when I sign in");
    association = check("association", "Open .phys marker files with Physlaunch");
    root.add(shortcut);
    root.add(runner);
    root.add(association);
    root.add(Box.createVerticalGlue());
    JLabel status = new JLabel(" ");
    status.setForeground(new Color(143, 152, 160));
    status.setAlignmentX(Component.LEFT_ALIGNMENT);
    root.add(status);
    root.add(Box.createVerticalStrut(10));
    JButton install = new JButton("Install Physlaunch");
    install.setBackground(new Color(91, 163, 43));
    install.setForeground(Color.WHITE);
    install.setFont(new Font("Segoe UI", Font.BOLD, 15));
    install.setAlignmentX(Component.LEFT_ALIGNMENT);
    install.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
    JButton uninstall = new JButton("Uninstall");
    uninstall.setEnabled(installedImageExists());
    uninstall.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
    install.addActionListener(
        e -> {
          install.setEnabled(false);
          status.setText("Installing…");
          SwingWorker<Void, Void> worker =
              new SwingWorker<>() {
                String error = "";

                @Override
                protected Void doInBackground() {
                  try {
                    installApp();
                  } catch (Exception x) {
                    error = x.getMessage() == null ? x.toString() : x.getMessage();
                  }
                  return null;
                }

                @Override
                protected void done() {
                  install.setEnabled(true);
                  uninstall.setEnabled(installedImageExists());
                  if (error.isEmpty()) {
                    status.setText("Installed successfully.");
                    JOptionPane.showMessageDialog(
                        frame,
                        "Physlaunch is installed.",
                        "Physlaunch Setup",
                        JOptionPane.INFORMATION_MESSAGE);
                  } else {
                    status.setText("Installation failed.");
                    JOptionPane.showMessageDialog(
                        frame, error, "Physlaunch Setup", JOptionPane.ERROR_MESSAGE);
                  }
                }
              };
          worker.execute();
        });
    uninstall.addActionListener(e -> uninstallApp(status, uninstall, install));
    JPanel actions = new JPanel(new GridLayout(1, 2, 8, 0));
    actions.setOpaque(false);
    actions.setAlignmentX(Component.LEFT_ALIGNMENT);
    actions.add(install);
    actions.add(uninstall);
    root.add(actions);
    frame.setContentPane(root);
    frame.setVisible(true);
  }

  private static void style(JLabel l, boolean bold) {
    l.setForeground(new Color(102, 192, 244));
    l.setFont(new Font("Segoe UI", bold ? Font.BOLD : Font.PLAIN, 13));
    l.setAlignmentX(Component.LEFT_ALIGNMENT);
  }

  private static JCheckBox check(String key, String text) {
    JCheckBox c = new JCheckBox(text, PREFERENCES.getBoolean(key, true));
    c.setOpaque(false);
    c.setForeground(Color.WHITE);
    c.setAlignmentX(Component.LEFT_ALIGNMENT);
    return c;
  }

  private static Image icon() {
    try (var in = Setup.class.getResourceAsStream("/logo.png")) {
      if (in != null)
        return javax.imageio.ImageIO.read(in).getScaledInstance(48, 48, Image.SCALE_SMOOTH);
    } catch (IOException ignored) {
    }
    return new java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_ARGB);
  }

  private static Path defaultInstall() {
    String os = os();
    if (os.contains("win"))
      return Path.of(
          System.getenv().getOrDefault("LOCALAPPDATA", System.getProperty("user.home")),
          "Programs",
          "Physlaunch");
    if (os.contains("mac"))
      return Path.of(System.getProperty("user.home"), "Applications", "Physlaunch");
    return Path.of(
        System.getenv()
            .getOrDefault(
                "XDG_DATA_HOME",
                Path.of(System.getProperty("user.home"), ".local/share").toString()),
        "Physlaunch");
  }

  private static String os() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
  }

  private static void browse() {
    JFileChooser c = new JFileChooser(location.getText());
    c.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    if (c.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION)
      location.setText(c.getSelectedFile().toPath().resolve("Physlaunch").toString());
  }

  private static void installApp() throws IOException, URISyntaxException, InterruptedException {
    stopRunner();
    Path source = findLauncherImage();
    if (source == null)
      throw new IOException(
          "Could not find the LauncherHub package beside Setup. Extract both app folders from the"
              + " Physlaunch release before running Setup.");
    String requestedLocation = location.getText().trim();
    if (requestedLocation.isEmpty()) throw new IOException("Choose an install location.");
    Path destination = Path.of(requestedLocation).toAbsolutePath().normalize();
    if (destination.getParent() == null)
      throw new IOException("Choose a user-writable install folder.");
    Path app = destination.resolve("LauncherHub");
    Files.createDirectories(destination);
    copyTree(source, app);
    Path executable = launcherExecutable(app);
    if (!Files.isRegularFile(executable))
      throw new IOException(
          "The LauncherHub executable is missing from the package: " + executable);
    boolean hadAssociation = PREFERENCES.getBoolean("association", false);
    if (shortcut.isSelected()) createShortcut(executable);
    else removeShortcut();
    if (runner.isSelected()) configureRunner(executable);
    else removeRunner();
    if (association.isSelected()) configureAssociation(executable);
    else if (hadAssociation) removeAssociation();
    if (os().contains("mac")) {
      Path mac = app.resolve("LauncherHub.app");
      if (Files.isDirectory(mac)) {
        Path apps = Path.of(System.getProperty("user.home"), "Applications");
        Files.createDirectories(apps);
        copyTree(mac, apps.resolve("Physlaunch.app"));
      }
    }
    if (runner.isSelected()) new ProcessBuilder(executable.toString(), "--run").start();
    PREFERENCES.put("installDir", destination.toString());
    PREFERENCES.putBoolean("shortcut", shortcut.isSelected());
    PREFERENCES.putBoolean("runner", runner.isSelected());
    PREFERENCES.putBoolean("association", association.isSelected());
  }

  private static boolean installedImageExists() {
    Path installDir = Path.of(PREFERENCES.get("installDir", defaultInstall().toString()));
    return Files.isDirectory(installDir.resolve("LauncherHub"));
  }

  private static void uninstallApp(JLabel status, JButton uninstall, JButton install) {
    int answer =
        JOptionPane.showConfirmDialog(
            frame,
            "Remove Physlaunch from this computer? Your .phys files on drives will be left alone.",
            "Uninstall Physlaunch",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
    if (answer != JOptionPane.YES_OPTION) return;
    uninstall.setEnabled(false);
    install.setEnabled(false);
    status.setText("Removing Physlaunch…");
    SwingWorker<Void, Void> worker =
        new SwingWorker<>() {
          String error = "";

          @Override
          protected Void doInBackground() {
            try {
              removeInstalledApp();
            } catch (Exception e) {
              error = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            return null;
          }

          @Override
          protected void done() {
            install.setEnabled(true);
            uninstall.setEnabled(installedImageExists());
            if (error.isEmpty()) status.setText("Physlaunch was removed.");
            else {
              status.setText("Uninstall failed.");
              JOptionPane.showMessageDialog(
                  frame, error, "Physlaunch Setup", JOptionPane.ERROR_MESSAGE);
            }
          }
        };
    worker.execute();
  }

  private static void removeInstalledApp() throws IOException, InterruptedException {
    Path installDir = Path.of(PREFERENCES.get("installDir", defaultInstall().toString()));
    Path appImage = installDir.resolve("LauncherHub");
    stopRunner();
    if (os().contains("win"))
      runOptional(List.of("taskkill", "/F", "/T", "/IM", "LauncherHub.exe"));
    removeRunner();
    removeShortcut();
    if (PREFERENCES.getBoolean("association", false)) removeAssociation();
    deleteTree(appImage);
    if (os().contains("mac"))
      deleteTree(Path.of(System.getProperty("user.home"), "Applications", "Physlaunch.app"));
    try {
      Files.deleteIfExists(installDir);
    } catch (DirectoryNotEmptyException ignored) {
      // The install folder may contain files the user added after setup.
    }
    for (String key : List.of("installDir", "shortcut", "runner", "association"))
      PREFERENCES.remove(key);
  }

  private static void deleteTree(Path root) throws IOException {
    if (!Files.exists(root)) return;
    Files.walkFileTree(
        root,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult visitFile(
              Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult postVisitDirectory(Path dir, IOException error)
              throws IOException {
            if (error != null) throw error;
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
          }
        });
  }

  private static Path findLauncherImage() throws URISyntaxException {
    Path code =
        Path.of(Setup.class.getProtectionDomain().getCodeSource().getLocation().toURI())
            .toAbsolutePath();
    // On macOS the code sits several levels inside Setup.app; on Windows and Linux
    // it is only one level below the app image. Walk upward to the release folder.
    for (Path ancestor = code; ancestor != null; ancestor = ancestor.getParent()) {
      Path sibling = ancestor.resolve("LauncherHub");
      if (Files.isDirectory(sibling)) return sibling;
      Path mac = ancestor.resolve("LauncherHub.app");
      if (Files.isDirectory(mac)) return mac;
    }
    return null;
  }

  private static Path launcherExecutable(Path image) {
    String os = os();
    if (os.contains("win")) return image.resolve("LauncherHub.exe");
    if (os.contains("mac"))
      return (image.getFileName() != null && image.getFileName().toString().endsWith(".app")
              ? image
              : image.resolve("LauncherHub.app"))
          .resolve("Contents/MacOS/LauncherHub");
    return image.resolve("bin/LauncherHub");
  }

  private static void copyTree(Path source, Path destination) throws IOException {
    if (!Files.isDirectory(source)) throw new IOException("App package not found: " + source);
    // Copying the whole app image also installs its bundled runtime and native launcher.
    Files.walkFileTree(
        source,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult preVisitDirectory(
              Path dir, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
            Files.createDirectories(destination.resolve(source.relativize(dir)));
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(
              Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
            Path out = destination.resolve(source.relativize(file));
            if (Files.isSymbolicLink(file)) {
              Files.deleteIfExists(out);
              Files.createSymbolicLink(out, Files.readSymbolicLink(file));
            } else {
              Files.copy(
                  file,
                  out,
                  StandardCopyOption.REPLACE_EXISTING,
                  StandardCopyOption.COPY_ATTRIBUTES);
            }
            return FileVisitResult.CONTINUE;
          }
        });
  }

  private static void createShortcut(Path executable) throws IOException, InterruptedException {
    String os = os();
    Path home = Path.of(System.getProperty("user.home"));
    if (os.contains("win")) {
      Path desktop =
          Path.of(System.getenv().getOrDefault("USERPROFILE", home.toString()), "Desktop");
      Files.createDirectories(desktop);
      String script =
          "$s=(New-Object -ComObject WScript.Shell).CreateShortcut('"
              + ps(desktop.resolve("Physlaunch.lnk").toString())
              + "');$s.TargetPath='"
              + ps(executable.toString())
              + "';$s.Save()";
      run(List.of("powershell", "-NoProfile", "-NonInteractive", "-Command", script));
    } else if (os.contains("linux")) {
      Path desktop = home.resolve("Desktop");
      Files.createDirectories(desktop);
      String data =
          "[Desktop Entry]\nType=Application\nName=Physlaunch\nExec=\""
              + desktopEscape(executable.toString())
              + "\"\nIcon="
              + launcherIconPath(executable)
              + "\nTerminal=false\nCategories=Game;Utility;\n";
      Path link = desktop.resolve("Physlaunch.desktop");
      Files.writeString(link, data, StandardCharsets.UTF_8);
      link.toFile().setExecutable(true);
    } else if (os.contains("mac")) {
      Path appBundle = executable.getParent().getParent().getParent();
      Path desktop = home.resolve("Desktop");
      Files.createDirectories(desktop);
      Path link = desktop.resolve("Physlaunch.app");
      Files.deleteIfExists(link);
      Files.createSymbolicLink(link, appBundle);
    }
  }

  private static void removeShortcut() throws IOException {
    Path home = Path.of(System.getProperty("user.home"));
    if (os().contains("win")) {
      Path desktop =
          Path.of(System.getenv().getOrDefault("USERPROFILE", home.toString()), "Desktop");
      Files.deleteIfExists(desktop.resolve("Physlaunch.lnk"));
    } else if (os().contains("linux")) {
      Files.deleteIfExists(home.resolve("Desktop/Physlaunch.desktop"));
    } else if (os().contains("mac")) {
      Path link = home.resolve("Desktop/Physlaunch.app");
      if (Files.isSymbolicLink(link)) Files.deleteIfExists(link);
    }
  }

  private static String launcherIconPath(Path executable) {
    for (Path parent = executable.getParent(); parent != null; parent = parent.getParent()) {
      Path candidate = parent.resolve("app/logo.png");
      if (Files.isRegularFile(candidate)) return candidate.toString();
    }
    return executable.toString();
  }

  private static String ps(String s) {
    return s.replace("'", "''");
  }

  private static String desktopEscape(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private static void configureRunner(Path executable) throws IOException, InterruptedException {
    String os = os();
    Path home = Path.of(System.getProperty("user.home"));
    if (os.contains("win"))
      run(
          List.of(
              "reg",
              "add",
              "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
              "/v",
              "PhyslaunchRunner",
              "/t",
              "REG_SZ",
              "/d",
              "\"" + executable + "\" --run",
              "/f"));
    else if (os.contains("mac")) {
      Path p = home.resolve("Library/LaunchAgents/com.physlaunch.runner.plist");
      Files.createDirectories(p.getParent());
      String xml =
          """
          <?xml version="1.0" encoding="UTF-8"?>
          <plist version="1.0">
            <dict>
              <key>Label</key><string>com.physlaunch.runner</string>
              <key>ProgramArguments</key>
              <array><string>%s</string><string>--run</string></array>
              <key>RunAtLoad</key><true/>
            </dict>
          </plist>
          """
              .formatted(xml(executable.toString()));
      Files.writeString(p, xml, StandardCharsets.UTF_8);
    } else {
      Path p = home.resolve(".config/autostart/physlaunch-runner.desktop");
      Files.createDirectories(p.getParent());
      Files.writeString(
          p,
          "[Desktop Entry]\nType=Application\nName=Physlaunch Runner\nExec=\""
              + desktopEscape(executable.toString())
              + "\" --run\nTerminal=false\nX-GNOME-Autostart-enabled=true\n",
          StandardCharsets.UTF_8);
    }
  }

  private static void removeRunner() throws IOException, InterruptedException {
    String os = os();
    Path home = Path.of(System.getProperty("user.home"));
    if (os.contains("win"))
      run(
          List.of(
              "reg",
              "delete",
              "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
              "/v",
              "PhyslaunchRunner",
              "/f"),
          true);
    else if (os.contains("mac"))
      Files.deleteIfExists(home.resolve("Library/LaunchAgents/com.physlaunch.runner.plist"));
    else Files.deleteIfExists(home.resolve(".config/autostart/physlaunch-runner.desktop"));
  }

  private static void stopRunner() throws IOException, InterruptedException {
    Path settings = Path.of(System.getProperty("user.home"), ".physlaunch");
    if (os().contains("win")) {
      settings =
          Path.of(
              System.getenv().getOrDefault("APPDATA", System.getProperty("user.home")),
              "Physlaunch");
    } else if (os().contains("mac")) {
      settings =
          Path.of(System.getProperty("user.home"), "Library", "Application Support", "Physlaunch");
    }
    if (!os().contains("win") && !os().contains("mac"))
      settings =
          Path.of(
              System.getenv()
                  .getOrDefault(
                      "XDG_CONFIG_HOME",
                      Path.of(System.getProperty("user.home"), ".config").toString()),
              "physlaunch");
    Files.createDirectories(settings);
    Path lockPath = settings.resolve("runner.lock");
    Path stopRequest = settings.resolve("runner.stop");
    Files.writeString(stopRequest, "stop", StandardCharsets.US_ASCII);
    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
    while (System.nanoTime() < deadline) {
      try (FileChannel channel =
          FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
        try {
          var lock = channel.tryLock();
          if (lock != null) {
            lock.release();
            Files.deleteIfExists(stopRequest);
            return;
          }
        } catch (java.nio.channels.OverlappingFileLockException ignored) {
          // Another Runner in this process still owns the lock.
        }
      }
      Thread.sleep(250);
    }
    throw new IOException("The Physlaunch Runner did not stop. Close it and try again.");
  }

  private static void configureAssociation(Path executable)
      throws IOException, InterruptedException {
    String os = os();
    Path home = Path.of(System.getProperty("user.home"));
    if (os.contains("win")) {
      run(
          List.of(
              "reg",
              "add",
              "HKCU\\Software\\Classes\\.phys",
              "/ve",
              "/d",
              "Physlaunch.Marker",
              "/f"));
      run(
          List.of(
              "reg",
              "add",
              "HKCU\\Software\\Classes\\Physlaunch.Marker\\DefaultIcon",
              "/ve",
              "/d",
              executable.toString(),
              "/f"));
      run(
          List.of(
              "reg",
              "add",
              "HKCU\\Software\\Classes\\Physlaunch.Marker\\shell\\open\\command",
              "/ve",
              "/d",
              "\"" + executable + "\" --open \"%1\"",
              "/f"));
    } else if (os.contains("linux")) {
      Path apps = home.resolve(".local/share/applications");
      Path mime = home.resolve(".local/share/mime/packages");
      Files.createDirectories(apps);
      Files.createDirectories(mime);
      Path desktop = apps.resolve("physlaunch-marker.desktop");
      Files.writeString(
          desktop,
          "[Desktop Entry]\nType=Application\nName=Physlaunch Marker\nExec=\""
              + desktopEscape(executable.toString())
              + "\" --open %f\nIcon="
              + launcherIconPath(executable)
              + "\nTerminal=false\nMimeType=application/x-physlaunch;\n",
          StandardCharsets.UTF_8);
      Files.writeString(
          mime.resolve("physlaunch.xml"),
          "<?xml version=\"1.0\"?><mime-info"
              + " xmlns=\"http://www.freedesktop.org/standards/shared-mime-info\"><mime-type"
              + " type=\"application/x-physlaunch\"><comment>Physlaunch game marker</comment><glob"
              + " pattern=\"*.phys\"/></mime-type></mime-info>",
          StandardCharsets.UTF_8);
      runOptional(
          List.of("xdg-mime", "default", "physlaunch-marker.desktop", "application/x-physlaunch"));
      runOptional(List.of("update-mime-database", home.resolve(".local/share/mime").toString()));
    } else if (os.contains("mac")) {
      run(
          List.of(
              "defaults",
              "write",
              "com.apple.LaunchServices",
              "LSHandlers",
              "-array-add",
              "{LSHandlerContentTagClass=public.filename-extension; LSHandlerContentTag=phys;"
                  + " LSHandlerRoleAll=com.physlaunch.launcher;}"),
          true);
    }
  }

  private static void removeAssociation() throws IOException, InterruptedException {
    String os = os();
    Path home = Path.of(System.getProperty("user.home"));
    if (os.contains("win")) {
      Process query =
          new ProcessBuilder("reg", "query", "HKCU\\Software\\Classes\\.phys", "/ve")
              .redirectErrorStream(true)
              .start();
      String output = new String(query.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      query.waitFor();
      if (output.contains("Physlaunch.Marker")) {
        run(List.of("reg", "delete", "HKCU\\Software\\Classes\\.phys", "/f"), true);
        run(List.of("reg", "delete", "HKCU\\Software\\Classes\\Physlaunch.Marker", "/f"), true);
      }
    } else if (os.contains("linux")) {
      Files.deleteIfExists(home.resolve(".local/share/applications/physlaunch-marker.desktop"));
      Files.deleteIfExists(home.resolve(".local/share/mime/packages/physlaunch.xml"));
      runOptional(List.of("update-mime-database", home.resolve(".local/share/mime").toString()));
    }
  }

  private static void run(List<String> command) throws IOException, InterruptedException {
    run(command, false);
  }

  private static void run(List<String> command, boolean ignoreFailure)
      throws IOException, InterruptedException {
    Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
    String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    int code = p.waitFor();
    if (code != 0 && !ignoreFailure)
      throw new IOException(String.join(" ", command) + " failed: " + output);
  }

  private static void runOptional(List<String> command) {
    try {
      run(command, true);
    } catch (IOException e) {
      // Some minimal Linux desktops do not include the MIME helper utilities.
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static String xml(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
