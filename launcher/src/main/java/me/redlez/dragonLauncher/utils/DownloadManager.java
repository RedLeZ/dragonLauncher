package me.redlez.dragonLauncher.utils;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import me.redlez.dragonLauncher.utils.VersionHandler.Downloadable;
import me.redlez.dragonLauncher.utils.VersionHandler.VersionMetadata;

public class DownloadManager {

    private final VersionMetadata versionData;

    private final Downloadable clientJar;
    private final List<Downloadable> libraries;
    private final List<Downloadable> assets;

    private final Path baseDir;
    private final Path versionDir;

    private final ExecutorService downloaderPool = Executors.newFixedThreadPool(8);
    private final Set<Path> completedFiles = ConcurrentHashMap.newKeySet();
    private final AtomicLong downloadedBytes = new AtomicLong(0);
    private long totalBytes;
    private final String version;

    private DownloadListener listener;
    private static final LoggerUtil LOGGER = new LoggerUtil("DownloadManager");

    public DownloadManager(String version) throws Exception {
    	LOGGER.info(isOnline() ? "Fetching Resources.." : "Running Offline");
    	
        this.versionData = VersionHandler.getVersionMetadata(version, isOnline());
        this.version = version;
        this.clientJar = versionData.clientJar;
        this.libraries = versionData.libraries;
        this.assets = versionData.assets;
        this.totalBytes = versionData.totalSize;

        this.baseDir = Paths.get(System.getProperty("user.home"), ".minecraft");
        this.versionDir = baseDir.resolve("versions").resolve(version);
        

        Files.createDirectories(versionDir);
    }

    public static boolean isOnline() {
        try (Socket socket = new Socket()) {
            SocketAddress addr = new InetSocketAddress("8.8.8.8", 53);
            socket.connect(addr, 2000);
            return true; // Connected
        } catch (IOException e) {
            return false; // Not connected
        }
    }

	public void setDownloadListener(DownloadListener listener) {
        this.listener = listener;
    }

    public void submitDownload(Downloadable file, boolean isNative, Path nativesDir) {
    	if (file == null) {
            LOGGER.warning("Skipping download: file is null");
            return;
        }
        Path targetPath = baseDir.resolve(file.path);
        Path tempPath = targetPath.resolveSibling(targetPath.getFileName() + ".tmp");

        downloaderPool.submit(() -> {
            int maxRetries = 3;
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    if (Files.exists(targetPath) && Files.size(targetPath) == file.size) {
                        LOGGER.info("Already exists: " + targetPath);
                        if (isNative && nativesDir != null) {
                            extractJar(targetPath, nativesDir);
                            LOGGER.info("Extracted natives from: " + targetPath);
                        }
                        onTaskComplete(targetPath, file.size);
                        return;
                    }

                    Files.createDirectories(targetPath.getParent());

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(file.url))
                            .timeout(Duration.ofSeconds(60))
                            .build();

                    HttpResponse<InputStream> response = HttpClient.newHttpClient()
                            .send(request, HttpResponse.BodyHandlers.ofInputStream());

                    if (response.statusCode() != 200) {
                        throw new IOException("HTTP " + response.statusCode());
                    }

                    // Download to temp file
                    try (InputStream in = response.body();
                         OutputStream out = Files.newOutputStream(tempPath,
                                 StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

                        byte[] buffer = new byte[8192];
                        int read;
                        long fileDownloaded = 0;

                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                            fileDownloaded += read;
                            long global = downloadedBytes.addAndGet(read);

                            if (listener != null) {
                                listener.onByteProgress(
                                        targetPath.getFileName().toString(),
                                        fileDownloaded,
                                        global,
                                        totalBytes
                                );
                            }
                        }
                        out.flush();
                    }

                    // Verify downloaded size
                    long actualSize = Files.size(tempPath);
                    if (actualSize != file.size) {
                        throw new IOException("Size mismatch after download: " + actualSize + " != " + file.size);
                    }

                    // Atomic rename
                    Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

                    LOGGER.info("Downloaded: " + targetPath.getFileName());
                    if (isNative && nativesDir != null) {
                        extractJar(targetPath, nativesDir);
                        LOGGER.info("Extracted natives from: " + targetPath);
                    }

                    onTaskComplete(targetPath, file.size);
                    return;

                } catch (Exception e) {
                    LOGGER.warning("Download failed (attempt " + attempt + "/" + maxRetries + "): " + file.url);
                    try { Files.deleteIfExists(tempPath); } catch (IOException ignored) {}
                    if (attempt == maxRetries) {
                        LOGGER.error("Giving up on: " + file.url + "\n" + e);
                        onTaskError(targetPath.getFileName().toString(), e);
                    } else {
                        try { Thread.sleep(1000L * attempt); } catch (InterruptedException ignored) {}
                    }
                }
            }
        });
    }
    
    public static void downloadFile(String url, Path target) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<Path> response = client.send(request,
                HttpResponse.BodyHandlers.ofFile(target));

        if (response.statusCode() != 200) {
            throw new IOException("Failed to download file: HTTP " + response.statusCode());
        }
    }


    private void onTaskComplete(Path target, long fileSize) {
        if (completedFiles.add(target)) {
            long global = downloadedBytes.addAndGet(fileSize);

            if (listener != null) {
                listener.onByteProgress(target.getFileName().toString(), fileSize, global, totalBytes);
                if (global >= totalBytes) {
                    listener.onProgress("All files", global, totalBytes);
                }
            }
        }
    }

    private void onTaskError(String fileName, Exception e) {
        if (listener != null) {
            listener.onError(fileName, e, downloadedBytes.get(), totalBytes);
        }
    }

    private void extractJar(Path jarFile, Path outputDir) {
        try (FileSystem fs = FileSystems.newFileSystem(jarFile, (ClassLoader) null)) {
            Path root = fs.getPath("/");
            Files.walk(root).forEach(path -> {
                try {
                    if (!Files.isDirectory(path)
                            && !path.toString().contains("META-INF")
                            && (path.toString().endsWith(".dll")
                            || path.toString().endsWith(".so")
                            || path.toString().endsWith(".dylib"))) {

                        Path relative = root.relativize(path);
                        Path target = outputDir.resolve(relative.toString());

                        Files.createDirectories(target.getParent());
                        Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // === Download stages ===

    public void downloadClient() throws Exception {
        if (clientJar != null) {
            submitDownload(clientJar, false, null);
            submitDownload(versionData.versionJson, false, null);
        }
    }

    public void downloadLibraries() throws IOException {
    	Path nativesDir = baseDir.resolve("versions").resolve(version).resolve(version + "-natives");
    	if (Files.exists(nativesDir)) {
    	    try {
    	        Files.walk(nativesDir)
    	            .sorted(Comparator.reverseOrder())
    	            .map(Path::toFile)
    	            .forEach(File::delete);
    	        long count = Files.walk(nativesDir).count();
    	        LOGGER.info("Deleted " + count + " old native files for version " + version);
    	    } catch (IOException e) {
    	        LOGGER.warning("Failed to clean natives folder: " + e.getMessage());
    	    }
    	}
    	Files.createDirectories(nativesDir);


        for (Downloadable lib : libraries) {
            boolean isNative = lib.path.toString().contains("natives");
            submitDownload(lib, isNative, nativesDir);
        }
    }

    public void downloadAssets() throws IOException {
    	
        Path indexesDir = baseDir.resolve("assets").resolve("indexes");
        Files.createDirectories(indexesDir);
        
        Downloadable indexFile = versionData.assetIndex;
        submitDownload(indexFile, false, null);

        for (Downloadable asset : assets) {
            Path target = baseDir.resolve(asset.path);
            submitDownload(new Downloadable(asset.url, asset.sha1, asset.size, target), false, null);
        }
    }


    public void downloadAll() throws Exception {
        downloadClient();
        downloadLibraries();
        downloadAssets();
    }

    public void waitForCompletion() {
        downloaderPool.shutdown();
        try {
            if (!downloaderPool.awaitTermination(30, TimeUnit.MINUTES)) {
                downloaderPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            downloaderPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void shutdown() {
        downloaderPool.shutdown();
    }
    
    public boolean allFilesAvailableOffline(String version) {
        Path versionDir = baseDir.resolve("versions").resolve(version);
        Path versionJson = versionDir.resolve(version + ".json");
        Path clientJar = versionDir.resolve(version + ".jar");

        // Check version files
        if (!Files.exists(versionJson) || !Files.exists(clientJar)) {
            return false;
        }
        
        // Check libraries
        Path libsDir = baseDir.resolve("libraries");
        try {
            VersionHandler.VersionMetadata meta = VersionHandler.getVersionMetadata(version, true); // offline mode
            for (VersionHandler.Downloadable lib : meta.libraries) {
                Path libPath = baseDir.resolve(lib.path);
                if (!Files.exists(libPath)) {
                    return false;
                }
            }

            for (VersionHandler.Downloadable asset : meta.assets) {
                Path assetPath = baseDir.resolve(asset.path);
                if (!Files.exists(assetPath)) {
                    return false;
                }
            }

            // Check asset index
            if (meta.assetIndex != null && !Files.exists(baseDir.resolve(meta.assetIndex.path))) {
                return false;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true; // all files exist
    }

}
