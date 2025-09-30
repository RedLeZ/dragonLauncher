package me.redlez.bootstrap;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.time.Duration;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class Main {

    private static final String REPO_URL =
            "https://github.com/RedLeZ/dragonLauncher/releases/download/dev/launcher.jar";
    private static final Path INSTALL_DIR = Paths.get(System.getProperty("user.home"), ".dragonLauncher");
    private static final Path LAUNCHER_JAR = INSTALL_DIR.resolve("launcher.jar");
    private static final Path LAUNCHER_LIB = INSTALL_DIR.resolve("libs");

    private static JProgressBar progressBar;
    private static JFrame frame;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::createUI);
        new Thread(Main::checkAndUpdate).start();
    }

    private static void createUI() {
        frame = new JFrame("DragonLauncher Bootstrap");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 250);
        frame.setLayout(new BorderLayout());
        frame.setUndecorated(true);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(30, 30, 30));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        ImageIcon originalIcon = new ImageIcon(Main.class.getResource("/icons/launcher.png"));
        Image scaledImage = originalIcon.getImage().getScaledInstance(128, 128, Image.SCALE_SMOOTH);
        JLabel logoLabel = new JLabel(new ImageIcon(scaledImage));
        logoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel title = new JLabel("Updating DragonLauncher...");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Arial", Font.BOLD, 16));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setBorder(BorderFactory.createEmptyBorder(10, 0, 20, 0));

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setForeground(new Color(0x4CAF50));
        progressBar.setBackground(new Color(50, 50, 50));
        progressBar.setPreferredSize(new Dimension(300, 30));
        progressBar.setAlignmentX(Component.CENTER_ALIGNMENT);

        panel.add(logoLabel);
        panel.add(title);
        panel.add(progressBar);

        frame.add(panel, BorderLayout.CENTER);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void checkAndUpdate() {
        try {
            Files.createDirectories(INSTALL_DIR);
            Files.createDirectories(LAUNCHER_LIB);

            // Download JavaFX modules dynamically
            for (String url : JavaFXDownload.getAllUrls()) {
                Path dest = LAUNCHER_LIB.resolve(url.substring(url.lastIndexOf('/') + 1));
                if (!Files.exists(dest)) {
                    LOGGER("Downloading JavaFX: " + dest.getFileName());
                    downloadWithRetries(url, dest);
                }
            }

            // Download main launcher
            downloadWithRetries(REPO_URL, LAUNCHER_JAR);

            // Launch main launcher
            ProcessBuilder pb = new ProcessBuilder(
            		Paths.get(System.getProperty("java.home"), "bin", "java").toString(),
                    "--module-path", LAUNCHER_LIB.toString(),
                    "--add-modules", "javafx.controls,javafx.graphics,javafx.base",
                    "-jar", LAUNCHER_JAR.toString()
            );// 
            pb.inheritIO();
            pb.start();

            SwingUtilities.invokeLater(() -> frame.dispose());

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(frame,
                    "Update failed: " + e.getMessage(),
                    "Bootstrap Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private static void downloadWithRetries(String urlStr, Path targetPath) throws IOException {
        Path tempPath = targetPath.resolveSibling(targetPath.getFileName() + ".tmp");
        int maxRetries = 3;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                if (Files.exists(targetPath)) {
                    LOGGER("Already exists: " + targetPath.getFileName());
                    return;
                }

                Files.createDirectories(targetPath.getParent());

                HttpClient client = HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.ALWAYS)
                        .build();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(urlStr))
                        .timeout(Duration.ofSeconds(60))
                        .header("User-Agent", "Mozilla/5.0 (compatible; DragonLauncher Bootstrap/1.0)")
                        .build();

                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                LOGGER("Response Code: " + response.statusCode());
                response.headers().map().forEach((k, v) -> LOGGER(k + ": " + v));
                long total = response.headers().firstValueAsLong("Content-Length").orElse(-1L);

                try (InputStream in = response.body();
                     OutputStream out = Files.newOutputStream(tempPath,
                             StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

                    byte[] buffer = new byte[8192];
                    int read;
                    long fileDownloaded = 0;

                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                        fileDownloaded += read;

                        long finalFileDownloaded = fileDownloaded;
                        SwingUtilities.invokeLater(() -> {
                            if (total > 0) {
                                progressBar.setValue((int) (finalFileDownloaded * 100 / total));
                            }
                        });
                    }
                    out.flush();
                }

                Files.move(tempPath, targetPath,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);

                LOGGER("Downloaded: " + targetPath.getFileName());
                return;

            } catch (Exception e) {
                LOGGER("Download failed (attempt " + attempt + "/" + maxRetries + "): " + urlStr);
                try { Files.deleteIfExists(tempPath); } catch (IOException ignored) {}
                if (attempt == maxRetries) throw new IOException("Failed after " + maxRetries + " attempts", e);
                try { Thread.sleep(1000L * attempt); } catch (InterruptedException ignored) {}
            }
        }
    }


    private static void LOGGER(String msg) {
        System.out.println("[Bootstrap] " + msg);
    }
}
