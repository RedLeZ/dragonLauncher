package me.redlez.dragonLauncher.utils;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class JavaManager {

    private static final Path JAVA_DIR = Paths.get(System.getProperty("user.home"), ".dragonLauncher", "java");

    public static void ensureJavaFor(String mcVersion, String configVersion) throws Exception {
        String requiredJava = (configVersion != null) ? configVersion : getJavaFor(mcVersion);

        Path javaHome = JAVA_DIR.resolve("jdk-" + requiredJava);
        Path javaBin = javaHome.resolve("bin").resolve("java");

        if (!Files.exists(javaBin)) {
            System.out.println("Java " + requiredJava + " not found, downloading...");
            downloadJava(requiredJava);
        }
    }

    private static String getJavaFor(String mcVersion) {
        if (mcVersion.startsWith("1.18") || mcVersion.startsWith("1.17")) return "17";
        if (mcVersion.startsWith("1.19") || mcVersion.startsWith("1.20.4")) return "17";
        if (mcVersion.startsWith("1.20.5") || mcVersion.startsWith("1.21")) return "21";
        return "8";
    }

    private static void downloadJava(String version) throws Exception {
        String os = OSUtils.getOS().toString().toLowerCase();
        String arch = OSUtils.isArm() ? "aarch64" : "x64";
        String ext = os.equals("windows") ? "zip" : "tar.gz";

        String javaUrl = String.format(
            "https://api.adoptium.net/v3/binary/latest/%s/ga/%s/%s/jdk/hotspot/normal/eclipse",
            version, os, arch
        );

        System.out.println("Downloading JDK " + version + " from " + javaUrl);

        Path javaDir = JAVA_DIR.resolve("jdk-" + version);
        Files.createDirectories(javaDir);

        Path archive = JAVA_DIR.resolve("jdk-" + version + "." + ext);
        DownloadManager.downloadFile(javaUrl, archive);

        ArchiveUtils.unzipOrUntar(archive, javaDir);
        Files.deleteIfExists(archive);

        if (os.equals("mac")) {
            // Fix nested Contents/Home
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(javaDir)) {
                for (Path p : stream) {
                    if (Files.isDirectory(p) && p.getFileName().toString().startsWith("jdk-")) {
                        Path home = p.resolve("Contents").resolve("Home");
                        if (Files.exists(home)) {
                            Files.walk(home).forEach(src -> {
                                try {
                                    Path dest = javaDir.resolve(home.relativize(src));
                                    if (!Files.exists(dest)) {
                                        Files.copy(src, dest);
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            });
                            deleteDirectory(p);
                            break;
                        }
                    }
                }
            }

            Files.walk(javaDir)
                .filter(Files::isRegularFile)
                .forEach(p -> p.toFile().setExecutable(true, false));

            // Remove quarantine attribute
            new ProcessBuilder("xattr", "-r", "-d", "com.apple.quarantine", javaDir.toString())
                .inheritIO()
                .start()
                .waitFor();
        }

        // Linux/Windows: nothing extra needed
    }

    // Recursive delete helper
    private static void deleteDirectory(Path path) throws IOException {
        Files.walk(path)
            .sorted((a, b) -> b.compareTo(a))
            .forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
    }

    private static boolean isInstalled(String javaVersion) {
        Path javaBin = JAVA_DIR.resolve("jdk-" + javaVersion).resolve("bin").resolve("java");
        return Files.exists(javaBin);
    }

    public static Path JavaPath(String mcVersion) throws Exception {
        String javaVersion = getJavaFor(mcVersion);
        Path javaBin = JAVA_DIR.resolve("jdk-" + javaVersion).resolve("bin").resolve("java");
        if (Files.exists(javaBin)) return javaBin;
        downloadJava(javaVersion);
        return JAVA_DIR.resolve("jdk-" + javaVersion).resolve("bin").resolve("java");
    }
}
