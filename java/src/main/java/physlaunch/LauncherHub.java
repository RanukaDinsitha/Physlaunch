package physlaunch;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.DosFileAttributeView;
import java.util.*;
import java.util.List;
import java.util.regex.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileSystemView;

/** Desktop game picker and optional background monitor for Physlaunch drive markers. */
public final class LauncherHub {
  private static final Color BG = new Color(23, 26, 33),
      PANEL = new Color(27, 40, 56),
      CARD = new Color(34, 52, 71);
  private static final Color BLUE = new Color(102, 192, 244), MUTED = new Color(143, 152, 160);
  private static final Path CONFIG = configDir().resolve("settings.properties");
  private static final Properties SETTINGS = loadSettings();
  private static final List<Game> GAMES = new ArrayList<>();
  private static final List<Path> LIBRARIES = new ArrayList<>();
  private static final Map<String, ImageIcon> ART = new java.util.concurrent.ConcurrentHashMap<>();
  private static final Set<String> ART_PENDING = java.util.concurrent.ConcurrentHashMap.newKeySet();
  private static final Set<String> ART_ATTEMPTED =
      java.util.concurrent.ConcurrentHashMap.newKeySet();
  private static JFrame frame;
  private static DefaultListModel<Game> gameModel;
  private static JList<Game> gameList;
  private static JComboBox<Volume> driveList;
  private static JTextField search;
  private static JLabel status;
  private static FileChannel runnerLockChannel;
  private static FileLock runnerLock;

  private record Game(String name, String appId, Path steamRoot) {
    @Override
    public String toString() {
      return name + "  ·  " + appId;
    }
  }

  private record Volume(Path root, String label, String identity, long freeBytes, long totalBytes) {
    @Override
    public String toString() {
      return String.format(
          Locale.ROOT,
          "%s  —  %s  ·  %.1f GB free of %.1f GB",
          label,
          root,
          freeBytes / 1_073_741_824.0,
          totalBytes / 1_073_741_824.0);
    }
  }

  private LauncherHub() {}

  public static void main(String[] args) {
    if (args.length > 0 && args[0].equals("--run")) {
      runWatcher();
      return;
    }
    if (args.length > 1 && args[0].equals("--open")) {
      launchMarker(Path.of(args[1]));
      return;
    }
    SwingUtilities.invokeLater(LauncherHub::showWindow);
  }

  private static void showWindow() {
    frame = new JFrame("Physlaunch");
    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    frame.setMinimumSize(new Dimension(860, 590));
    frame.setSize(1050, 720);
    frame.setLocationRelativeTo(null);
    frame.setIconImage(loadLogo());
    frame.getContentPane().setBackground(BG);
    frame.setLayout(new BorderLayout(0, 12));
    frame.add(header(), BorderLayout.NORTH);

    JPanel content = new JPanel(new BorderLayout(14, 0));
    content.setOpaque(false);
    content.setBorder(new EmptyBorder(0, 22, 20, 22));
    JPanel games = cardPanel();
    games.setLayout(new BorderLayout(8, 10));
    games.setBorder(new EmptyBorder(16, 16, 16, 16));
    JLabel gamesTitle = label("1   Choose a game", Color.WHITE, 16, true);
    JPanel gameTop = new JPanel(new BorderLayout(8, 8));
    gameTop.setOpaque(false);
    gameTop.add(gamesTitle, BorderLayout.WEST);
    JPanel gameButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
    gameButtons.setOpaque(false);
    JButton addLibrary = button("Add library…", CARD, Color.WHITE);
    JButton rescan = button("Rescan", CARD, Color.WHITE);
    gameButtons.add(addLibrary);
    gameButtons.add(rescan);
    gameTop.add(gameButtons, BorderLayout.EAST);
    search = new JTextField();
    search.setFont(new Font("Segoe UI", Font.PLAIN, 14));
    search.setBackground(BG);
    search.setForeground(Color.WHITE);
    search.setCaretColor(BLUE);
    search.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(42, 71, 94), 2),
            new EmptyBorder(8, 10, 8, 10)));
    gameModel = new DefaultListModel<>();
    gameList = new JList<>(gameModel);
    gameList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    gameList.setBackground(PANEL);
    gameList.setForeground(Color.WHITE);
    gameList.setFont(new Font("Segoe UI", Font.PLAIN, 14));
    gameList.setFixedCellHeight(48);
    gameList.setCellRenderer(new GameRenderer());
    JScrollPane scroll = new JScrollPane(gameList);
    scroll.setBorder(BorderFactory.createLineBorder(new Color(42, 71, 94), 1));
    games.add(gameTop, BorderLayout.NORTH);
    JPanel gameBody = new JPanel(new BorderLayout(0, 8));
    gameBody.setOpaque(false);
    gameBody.add(search, BorderLayout.NORTH);
    gameBody.add(scroll, BorderLayout.CENTER);
    games.add(gameBody, BorderLayout.CENTER);

    JPanel side = new JPanel();
    side.setOpaque(false);
    side.setPreferredSize(new Dimension(340, 10));
    side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
    JPanel selected = cardPanel();
    selected.setLayout(new BoxLayout(selected, BoxLayout.Y_AXIS));
    selected.setBorder(new EmptyBorder(16, 16, 16, 16));
    JLabel gameHeading = label("SELECTED GAME", BLUE, 11, true);
    JLabel selectedName = label("Choose a game", Color.WHITE, 17, true);
    JLabel selectedId = label("App ID —", MUTED, 12, false);
    selected.add(gameHeading);
    selected.add(Box.createVerticalStrut(12));
    selected.add(selectedName);
    selected.add(Box.createVerticalStrut(5));
    selected.add(selectedId);

    JPanel drives = cardPanel();
    drives.setLayout(new BorderLayout(6, 10));
    drives.setBorder(new EmptyBorder(16, 16, 16, 16));
    JPanel driveTitle = new JPanel(new BorderLayout());
    driveTitle.setOpaque(false);
    driveTitle.add(label("2   Choose a drive", Color.WHITE, 15, true), BorderLayout.WEST);
    JButton refresh = button("Refresh", CARD, Color.WHITE);
    driveTitle.add(refresh, BorderLayout.EAST);
    driveList = new JComboBox<>();
    driveList.setBackground(CARD);
    driveList.setForeground(Color.WHITE);
    driveList.setFont(new Font("Segoe UI", Font.PLAIN, 13));
    JLabel help =
        label("The marker is written as a hidden .phys file at the drive root.", MUTED, 12, false);
    help.setAlignmentX(Component.LEFT_ALIGNMENT);
    drives.add(driveTitle, BorderLayout.NORTH);
    drives.add(driveList, BorderLayout.CENTER);
    drives.add(help, BorderLayout.SOUTH);

    JPanel write = cardPanel();
    write.setLayout(new BorderLayout(6, 10));
    write.setBorder(new EmptyBorder(16, 16, 16, 16));
    write.add(label("3   Write launcher", Color.WHITE, 15, true), BorderLayout.NORTH);
    JButton writeButton = button("Write Game File", new Color(91, 163, 43), Color.WHITE);
    write.add(writeButton, BorderLayout.CENTER);
    status = label("Ready", MUTED, 12, false);
    write.add(status, BorderLayout.SOUTH);
    side.add(selected);
    side.add(Box.createVerticalStrut(12));
    side.add(drives);
    side.add(Box.createVerticalStrut(12));
    side.add(write);
    content.add(games, BorderLayout.CENTER);
    content.add(side, BorderLayout.EAST);
    frame.add(content, BorderLayout.CENTER);

    search.getDocument().addDocumentListener((SimpleDocumentListener) e -> filterGames());
    gameList.addListSelectionListener(
        e -> {
          if (!e.getValueIsAdjusting()) {
            Game g = gameList.getSelectedValue();
            selectedName.setText(g == null ? "Choose a game" : g.name());
            selectedId.setText("App ID " + (g == null ? "—" : g.appId()));
          }
        });
    addLibrary.addActionListener(e -> chooseLibrary());
    rescan.addActionListener(e -> loadGames(true));
    refresh.addActionListener(e -> loadVolumes());
    writeButton.addActionListener(e -> writeMarker());
    frame.setVisible(true);
    loadVolumes();
    loadGames(false);
  }

  private static JPanel header() {
    JPanel p = new JPanel(new BorderLayout(12, 0));
    p.setOpaque(false);
    p.setBorder(new EmptyBorder(18, 24, 4, 24));
    JLabel logo = new JLabel(new ImageIcon(loadLogo()));
    JLabel title = new JLabel("Physlaunch");
    title.setForeground(Color.WHITE);
    title.setFont(new Font("Segoe UI", Font.BOLD, 25));
    JLabel tagline = new JLabel("Turn any drive into a game launcher");
    tagline.setForeground(MUTED);
    tagline.setFont(new Font("Segoe UI", Font.PLAIN, 12));
    JPanel text = new JPanel();
    text.setOpaque(false);
    text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
    text.add(title);
    text.add(tagline);
    p.add(logo, BorderLayout.WEST);
    p.add(text, BorderLayout.CENTER);
    return p;
  }

  private static JPanel cardPanel() {
    JPanel p = new JPanel();
    p.setBackground(PANEL);
    p.setBorder(BorderFactory.createLineBorder(new Color(42, 71, 94), 2));
    return p;
  }

  private static JLabel label(String text, Color color, int size, boolean bold) {
    JLabel l = new JLabel(text);
    l.setForeground(color);
    l.setFont(new Font("Segoe UI", bold ? Font.BOLD : Font.PLAIN, size));
    return l;
  }

  private static JButton button(String text, Color bg, Color fg) {
    JButton b = new JButton(text);
    b.setBackground(bg);
    b.setForeground(fg);
    b.setFocusPainted(false);
    b.setFont(new Font("Segoe UI", Font.BOLD, 12));
    return b;
  }

  private static Image loadLogo() {
    try (var in = LauncherHub.class.getResourceAsStream("/logo.png")) {
      if (in != null)
        return javax.imageio.ImageIO.read(in).getScaledInstance(48, 48, Image.SCALE_SMOOTH);
    } catch (IOException ignored) {
    }
    return new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
  }

  private static Properties loadSettings() {
    Properties p = new Properties();
    try {
      if (Files.exists(CONFIG))
        try (var in = Files.newInputStream(CONFIG)) {
          p.load(in);
        }
    } catch (IOException ignored) {
    }
    return p;
  }

  private static void saveSettings() {
    try {
      Files.createDirectories(CONFIG.getParent());
      try (var out = Files.newOutputStream(CONFIG)) {
        SETTINGS.store(out, "Physlaunch settings");
      }
    } catch (IOException e) {
      message("Could not save settings: " + e.getMessage());
    }
  }

  private static Path configDir() {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (os.contains("win"))
      return Path.of(
          System.getenv().getOrDefault("APPDATA", System.getProperty("user.home")), "Physlaunch");
    if (os.contains("mac"))
      return Path.of(
          System.getProperty("user.home"), "Library", "Application Support", "Physlaunch");
    return Path.of(
        System.getenv()
            .getOrDefault(
                "XDG_CONFIG_HOME", Path.of(System.getProperty("user.home"), ".config").toString()),
        "physlaunch");
  }

  private static List<Path> steamRoots() {
    LinkedHashSet<Path> roots = new LinkedHashSet<>();
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    Path home = Path.of(System.getProperty("user.home"));
    if (os.contains("win")) {
      roots.add(Path.of("C:/Program Files (x86)/Steam"));
      roots.add(Path.of("C:/Program Files/Steam"));
      String pf = System.getenv("ProgramFiles(x86)");
      if (pf != null) roots.add(Path.of(pf, "Steam"));
      try {
        Process p =
            new ProcessBuilder("reg", "query", "HKCU\\Software\\Valve\\Steam", "/v", "SteamPath")
                .redirectErrorStream(true)
                .start();
        String s = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("(?im)SteamPath\\s+REG_SZ\\s+(.+)$").matcher(s);
        if (m.find()) roots.add(Path.of(m.group(1).trim()));
      } catch (Exception ignored) {
      }
    } else if (os.contains("mac")) {
      roots.add(home.resolve("Library/Application Support/Steam"));
      roots.add(home.resolve(".steam/steam"));
    } else {
      roots.add(home.resolve(".steam/steam"));
      roots.add(home.resolve(".local/share/Steam"));
      roots.add(Path.of("/usr/share/steam"));
    }
    String env = System.getenv("STEAM_DIR");
    if (env != null && !env.isBlank()) roots.add(Path.of(env));
    return new ArrayList<>(roots);
  }

  private static void loadGames(boolean showMessage) {
    GAMES.clear();
    LIBRARIES.clear();
    LinkedHashSet<Path> libraries = new LinkedHashSet<>();
    for (Path root : steamRoots()) addLibrary(root, libraries);
    String extra = SETTINGS.getProperty("libraries", "");
    for (String s : extra.split(Pattern.quote(File.pathSeparator)))
      if (!s.isBlank()) addLibrary(Path.of(s), libraries);
    for (Path lib : libraries) scanLibrary(lib);
    GAMES.sort(Comparator.comparing(Game::name, String.CASE_INSENSITIVE_ORDER));
    filterGames();
    if (status != null)
      status.setText(
          GAMES.isEmpty()
              ? "No installed Steam games found. Add a Steam library folder and rescan."
              : GAMES.size() + " installed games found");
  }

  private static void addLibrary(Path path, Set<Path> into) {
    try {
      // Steam lists library roots in both numeric (older) and named (newer) VDF entries.
      Path p = path.toAbsolutePath().normalize();
      if (p.getFileName() != null && p.getFileName().toString().equalsIgnoreCase("steamapps"))
        p = p.getParent();
      Path apps = p.resolve("steamapps");
      if (!Files.isDirectory(apps) || !into.add(p)) return;
      Path vdf = apps.resolve("libraryfolders.vdf");
      if (Files.isRegularFile(vdf)) {
        String text = Files.readString(vdf);
        Matcher m =
            Pattern.compile("(?im)\"(?:path|[0-9]+)\"\\s+\"((?:[A-Za-z]:\\\\\\\\|/)[^\"]+)\"")
                .matcher(text);
        while (m.find()) addLibrary(Path.of(m.group(1).replace("\\\\", "\\")), into);
      }
    } catch (Exception ignored) {
    }
  }

  private static void scanLibrary(Path root) {
    Path apps = root.resolve("steamapps");
    try (DirectoryStream<Path> files = Files.newDirectoryStream(apps, "appmanifest_*.acf")) {
      for (Path f : files)
        try {
          String text = Files.readString(f);
          String id = manifest(text, "appid");
          if (id.isBlank()) {
            Matcher fm =
                Pattern.compile("appmanifest_(\\d+)\\.acf", Pattern.CASE_INSENSITIVE)
                    .matcher(f.getFileName().toString());
            if (fm.find()) id = fm.group(1);
          }
          String name = manifest(text, "name");
          final String appId = id;
          if (appId.matches("\\d{1,10}")
              && !name.isBlank()
              && !isTool(name)
              && GAMES.stream().noneMatch(g -> g.appId().equals(appId)))
            GAMES.add(new Game(name, appId, root));
        } catch (Exception ignored) {
        }
    } catch (IOException ignored) {
    }
  }

  private static String manifest(String text, String key) {
    Matcher m =
        Pattern.compile("(?im)\"" + Pattern.quote(key) + "\"\\s+\"([^\"]*)\"").matcher(text);
    return m.find() ? m.group(1).replace("\\\\", "\\").replace("\\\"", "\"").trim() : "";
  }

  private static boolean isTool(String n) {
    return n.matches(
        "(?i)^(Steamworks Common Redistributables|Proton(?:\\s|$)|Steam Linux Runtime|Steam"
            + " Controller Configs).*");
  }

  private static void filterGames() {
    if (gameModel == null) return;
    String q = search.getText().trim().toLowerCase(Locale.ROOT);
    gameModel.clear();
    for (Game g : GAMES)
      if (q.isEmpty() || g.name().toLowerCase(Locale.ROOT).contains(q)) gameModel.addElement(g);
  }

  private static void chooseLibrary() {
    JFileChooser chooser = new JFileChooser();
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    chooser.setDialogTitle("Choose a Steam folder or Steam library");
    if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
      Path p = chooser.getSelectedFile().toPath();
      if (p.getFileName() != null && p.getFileName().toString().equalsIgnoreCase("steamapps"))
        p = p.getParent();
      String old = SETTINGS.getProperty("libraries", "");
      LinkedHashSet<String> paths = new LinkedHashSet<>();
      if (!old.isBlank()) paths.addAll(Arrays.asList(old.split(Pattern.quote(File.pathSeparator))));
      paths.add(p.toString());
      SETTINGS.setProperty("libraries", String.join(File.pathSeparator, paths));
      saveSettings();
      loadGames(true);
    }
  }

  private static List<Volume> findVolumes() {
    LinkedHashSet<Path> roots = new LinkedHashSet<>();
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    // Each platform exposes removable media differently, so keep native discovery here.
    if (os.contains("win")) {
      String ps =
          "$usb=Get-CimInstance Win32_DiskDrive | Where-Object {$_.InterfaceType -eq 'USB'};"
              + " foreach($d in $usb){Get-CimAssociatedInstance -InputObject $d -Association"
              + " Win32_DiskDriveToDiskPartition | ForEach-Object {Get-CimAssociatedInstance"
              + " -InputObject $_ -Association Win32_LogicalDiskToPartition | ForEach-Object"
              + " {$_.DeviceID}}}; Get-CimInstance Win32_LogicalDisk | Where-Object {$_.DriveType"
              + " -eq 2} | ForEach-Object {$_.DeviceID}";
      try {
        Process p =
            new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", ps)
                .redirectErrorStream(true)
                .start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor();
        for (String s : out.split("\\R"))
          if (s.trim().matches("(?i)[A-Z]:")) roots.add(Path.of(s.trim() + "\\"));
      } catch (Exception ignored) {
      }
    } else if (os.contains("linux")) {
      try {
        Process p =
            new ProcessBuilder("lsblk", "-P", "-n", "-o", "RM,MOUNTPOINT")
                .redirectErrorStream(true)
                .start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor();
        for (String line : out.split("\\R")) {
          Matcher rm = Pattern.compile("RM=\"([^\"]*)\"").matcher(line),
              mp = Pattern.compile("MOUNTPOINT=\"([^\"]*)\"").matcher(line);
          if (rm.find() && rm.group(1).equals("1") && mp.find() && !mp.group(1).isBlank())
            roots.add(Path.of(mp.group(1).replace("\\x20", " ")));
        }
      } catch (Exception ignored) {
      }
    } else if (os.contains("mac")) {
      try {
        Path mountedVolumes = Path.of("/Volumes");
        if (Files.isDirectory(mountedVolumes))
          try (var volumes = Files.list(mountedVolumes)) {
            volumes.filter(Files::isDirectory).forEach(roots::add);
          }
        Process p =
            new ProcessBuilder("diskutil", "list", "external", "physical")
                .redirectErrorStream(true)
                .start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor();
        Matcher m = Pattern.compile("(?m)^\\s*\\d+:.*\\s(/dev/disk\\d+s\\d+)\\s*$").matcher(out);
        while (m.find()) {
          Process info =
              new ProcessBuilder("diskutil", "info", m.group(1)).redirectErrorStream(true).start();
          String txt = new String(info.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
          info.waitFor();
          Matcher mp = Pattern.compile("(?m)^\\s*Mount Point:\\s*(.+)$").matcher(txt);
          if (mp.find() && !mp.group(1).equals("Not mounted"))
            roots.add(Path.of(mp.group(1).trim()));
        }
      } catch (Exception ignored) {
      }
    }
    ArrayList<Volume> result = new ArrayList<>();
    for (Path root : roots)
      try {
        Path p = root.toRealPath();
        if (Files.isDirectory(p) && Files.isWritable(p)) {
          var store = Files.getFileStore(p);
          String name = FileSystemView.getFileSystemView().getSystemDisplayName(p.toFile());
          if (name == null || name.isBlank())
            name = p.getFileName() == null ? p.toString() : p.getFileName().toString();
          String identity = store.name() + "|" + store.type() + "|" + store.getTotalSpace();
          result.add(new Volume(p, name, identity, store.getUsableSpace(), store.getTotalSpace()));
        }
      } catch (IOException ignored) {
      }
    result.sort(Comparator.comparing(v -> v.root().toString(), String.CASE_INSENSITIVE_ORDER));
    return result;
  }

  private static void loadVolumes() {
    if (driveList == null) return;
    Volume prior = (Volume) driveList.getSelectedItem();
    driveList.removeAllItems();
    for (Volume v : findVolumes()) driveList.addItem(v);
    if (prior != null)
      for (int i = 0; i < driveList.getItemCount(); i++)
        if (driveList.getItemAt(i).root().equals(prior.root())
            && driveList.getItemAt(i).identity().equals(prior.identity()))
          driveList.setSelectedIndex(i);
    if (driveList.getItemCount() == 0)
      status.setText("No ready removable drives found. Connect a drive and refresh.");
  }

  private static void writeMarker() {
    Game game = gameList.getSelectedValue();
    Volume volume = (Volume) driveList.getSelectedItem();
    if (game == null) {
      message("Choose a game first.");
      return;
    }
    if (volume == null) {
      message("Connect and choose a removable drive first.");
      return;
    }
    Path root = volume.root();
    Path marker = root.resolve(".phys");
    Path temp = root.resolve(".phys.tmp");
    byte[] expected = game.appId().getBytes(StandardCharsets.US_ASCII);
    try {
      // Write beside the marker, then replace it atomically so an unplugged drive is less
      // likely to be left with a truncated App ID.
      if (!Files.isDirectory(root)
          || !Files.isWritable(root)
          || !findVolumes().stream()
              .anyMatch(v -> v.root().equals(root) && v.identity().equals(volume.identity())))
        throw new IOException(
            "The selected drive is no longer available. Refresh and select it again.");
      Files.write(
          temp,
          expected,
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE);
      try {
        Files.move(
            temp, marker, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temp, marker, StandardCopyOption.REPLACE_EXISTING);
      }
      DosFileAttributeView dos = Files.getFileAttributeView(marker, DosFileAttributeView.class);
      if (dos != null) dos.setHidden(true);
      if (!Arrays.equals(Files.readAllBytes(marker), expected))
        throw new IOException("The marker could not be verified after writing.");
      status.setText("Wrote .phys (App ID " + game.appId() + ") to " + root);
    } catch (Exception e) {
      try {
        Files.deleteIfExists(temp);
      } catch (IOException ignored) {
      }
      message("Could not write the game marker: " + e.getMessage());
    }
  }

  private static void message(String text) {
    if (frame != null)
      JOptionPane.showMessageDialog(frame, text, "Physlaunch", JOptionPane.INFORMATION_MESSAGE);
    else System.err.println(text);
  }

  private static void runWatcher() {
    try {
      Files.createDirectories(configDir());
      runnerLockChannel =
          FileChannel.open(
              configDir().resolve("runner.lock"),
              StandardOpenOption.CREATE,
              StandardOpenOption.WRITE);
      runnerLock = runnerLockChannel.tryLock();
      if (runnerLock == null) return;
    } catch (IOException | OverlappingFileLockException e) {
      logRunner("Another Runner instance is already active.");
      return;
    }

    Map<String, String> active = new HashMap<>();
    Path stopRequest = configDir().resolve("runner.stop");
    while (true) {
      try {
        if (Files.deleteIfExists(stopRequest)) {
          logRunner("Runner stopped by setup.");
          return;
        }
        // Remember a marker per mounted volume to avoid relaunching it on every poll.
        Set<String> present = new HashSet<>();
        for (Volume v : findVolumes()) {
          Path marker = v.root().resolve(".phys");
          if (!Files.isRegularFile(marker)) continue;
          String volumeKey = v.root() + "|" + v.identity();
          present.add(volumeKey);
          String id = readId(marker);
          if (id.isBlank()) continue;
          if (!id.equals(active.get(volumeKey))) {
            active.put(volumeKey, id);
            launchId(id);
          }
        }
        active.keySet().retainAll(present);
        Thread.sleep(4000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      } catch (Exception e) {
        logRunner(e.toString());
        try {
          Thread.sleep(4000);
        } catch (InterruptedException x) {
          Thread.currentThread().interrupt();
          return;
        }
      }
    }
  }

  private static String readId(Path marker) {
    try {
      String s = Files.readString(marker, StandardCharsets.US_ASCII).trim();
      return s.matches("\\d{1,10}") ? s : "";
    } catch (IOException e) {
      return "";
    }
  }

  private static void launchMarker(Path path) {
    String id = readId(path);
    if (id.isBlank()) {
      message("That .phys file does not contain a valid Steam App ID.");
      return;
    }
    launchId(id);
  }

  private static void launchId(String id) {
    try {
      String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
      if (os.contains("win")) {
        Path steamExe =
            steamRoots().stream()
                .map(root -> root.resolve("steam.exe"))
                .filter(Files::isRegularFile)
                .findFirst()
                .orElseThrow(() -> new IOException("Could not find Steam.exe."));
        new ProcessBuilder(steamExe.toString(), "-applaunch", id).start();
      } else if (os.contains("mac")) {
        new ProcessBuilder("open", "-a", "Steam", "--args", "-applaunch", id).start();
      } else {
        new ProcessBuilder("steam", "-applaunch", id).start();
      }
      logRunner("Launching App ID " + id);
    } catch (Exception e) {
      logRunner("Could not launch App ID " + id + ": " + e);
    }
  }

  private static void logRunner(String line) {
    try {
      Path p = configDir().resolve("runner.log");
      Files.createDirectories(p.getParent());
      Files.writeString(
          p,
          java.time.Instant.now() + "  " + line + System.lineSeparator(),
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (IOException ignored) {
    }
  }

  private static final class GameRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(
        JList<?> list, Object value, int index, boolean selected, boolean focus) {
      JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
      l.setBorder(new EmptyBorder(4, 12, 4, 8));
      l.setBackground(selected ? new Color(40, 70, 95) : PANEL);
      l.setForeground(Color.WHITE);
      if (value instanceof Game g) {
        ImageIcon cached = ART.get(g.appId());
        if (cached != null) l.setIcon(cached);
        else {
          Path header = findLocalArt(g);
          try {
            if (header != null) {
              ImageIcon icon = new ImageIcon(ImageTools.read(header, 140, 36));
              ART.put(g.appId(), icon);
              l.setIcon(icon);
            } else {
              l.setIcon(tile(g.name()));
              downloadArt(g);
            }
          } catch (Exception ignored) {
            l.setIcon(tile(g.name()));
          }
        }
      }
      return l;
    }
  }

  private static ImageIcon tile(String name) {
    BufferedImage tile = new BufferedImage(34, 34, BufferedImage.TYPE_INT_ARGB);
    Graphics2D gr = tile.createGraphics();
    gr.setColor(BLUE);
    gr.fillRoundRect(0, 0, 34, 34, 8, 8);
    gr.setColor(PANEL);
    gr.setFont(new Font("Segoe UI", Font.BOLD, 18));
    gr.drawString(name.substring(0, 1).toUpperCase(Locale.ROOT), 11, 24);
    gr.dispose();
    return new ImageIcon(tile);
  }

  private static Path findLocalArt(Game g) {
    Path cache = g.steamRoot().resolve("appcache/librarycache");
    Path direct = cache.resolve(g.appId() + "_header.jpg");
    if (Files.isRegularFile(direct)) return direct;
    Path folder = cache.resolve(g.appId());
    Path[] names = {folder.resolve("header.jpg"), folder.resolve("library_header.jpg")};
    for (Path p : names) if (Files.isRegularFile(p)) return p;
    if (Files.isDirectory(folder))
      try (var children = Files.list(folder)) {
        return children
            .filter(Files::isDirectory)
            .flatMap(
                p ->
                    java.util.stream.Stream.of(
                        p.resolve("header.jpg"), p.resolve("library_header.jpg")))
            .filter(Files::isRegularFile)
            .findFirst()
            .orElse(null);
      } catch (IOException ignored) {
      }
    return null;
  }

  private static void downloadArt(Game g) {
    if (ART_ATTEMPTED.contains(g.appId()) || !ART_PENDING.add(g.appId())) return;
    ART_ATTEMPTED.add(g.appId());
    java.util.concurrent.CompletableFuture.runAsync(
        () -> {
          try {
            Path cache = configDir().resolve("art");
            Files.createDirectories(cache);
            Path target = cache.resolve(g.appId() + "_header.jpg");
            if (!Files.isRegularFile(target)) {
              String[] hosts = {
                "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/",
                "https://shared.cloudflare.steamstatic.com/store_item_assets/steam/apps/"
              };
              for (String host : hosts)
                try {
                  HttpURLConnection c =
                      (HttpURLConnection)
                          new URL(host + g.appId() + "/header.jpg").openConnection();
                  c.setConnectTimeout(3500);
                  c.setReadTimeout(5000);
                  c.setInstanceFollowRedirects(true);
                  c.setRequestProperty("User-Agent", "Physlaunch/1.0");
                  if (c.getResponseCode() == 200)
                    try (var in = c.getInputStream()) {
                      Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                  c.disconnect();
                  if (Files.isRegularFile(target)) break;
                } catch (Exception ignored) {
                }
            }
            if (Files.isRegularFile(target)) {
              ImageIcon icon = new ImageIcon(ImageTools.read(target, 140, 36));
              ART.put(g.appId(), icon);
              SwingUtilities.invokeLater(
                  () -> {
                    if (gameList != null) gameList.repaint();
                  });
            }
          } catch (Exception ignored) {
          } finally {
            ART_PENDING.remove(g.appId());
          }
        });
  }

  private static final class ImageTools {
    static BufferedImage read(Path path, int w, int h) throws IOException {
      Image img =
          javax.imageio.ImageIO.read(path.toFile()).getScaledInstance(w, h, Image.SCALE_SMOOTH);
      BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = b.createGraphics();
      g.drawImage(img, 0, 0, null);
      g.dispose();
      return b;
    }
  }

  @FunctionalInterface
  private interface SimpleDocumentListener extends javax.swing.event.DocumentListener {
    void update(javax.swing.event.DocumentEvent e);

    @Override
    default void insertUpdate(javax.swing.event.DocumentEvent e) {
      update(e);
    }

    @Override
    default void removeUpdate(javax.swing.event.DocumentEvent e) {
      update(e);
    }

    @Override
    default void changedUpdate(javax.swing.event.DocumentEvent e) {
      update(e);
    }
  }
}
