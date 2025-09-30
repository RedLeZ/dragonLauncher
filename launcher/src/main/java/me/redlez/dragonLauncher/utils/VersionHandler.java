package me.redlez.dragonLauncher.utils;

import java.net.URI;
import java.net.http.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class VersionHandler {
	
	private static Path baseDir = Paths.get(System.getProperty("user.home"), ".minecraft");
    private static final Path CACHE_FILE = baseDir.resolve("versions_cache.json");
    private static final String VERSION_MANIFEST = "https://launchermeta.mojang.com/mc/game/version_manifest.json";
    private static final LoggerUtil LOGGER = new LoggerUtil("VersionHandler");
    private static final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    public static class Downloadable {
        public String url;
        public String sha1;
        public long size;
        public Path path;

        public Downloadable(String url, String sha1, long size, Path path) {
            this.url = url;
            this.sha1 = sha1;
            this.size = size;
            this.path = path;
        }
    }

    public static class VersionInfo {
        public String id;
        public String url;
        public Instant releaseTime;
    }

    public static class VersionMetadata {
        public List<Downloadable> libraries = new ArrayList<>();
        public List<Downloadable> assets = new ArrayList<>();
        public String assetsIndexId;
        public Downloadable clientJar;
        public Downloadable assetIndex;
        public Downloadable versionJson;
        public long totalSize;
        
    }
    
    public Map<String, String> getForgeVersions() throws Exception {
    	return ForgeFetcher.getForgeInstallers();
    }

    public static JsonObject getVersionManifest() throws Exception {
        if (Files.exists(CACHE_FILE)) {
            FileTime lastModified = Files.getLastModifiedTime(CACHE_FILE);
            if (Instant.now().minus(Duration.ofHours(1)).isBefore(lastModified.toInstant())) {
                return JsonParser.parseString(Files.readString(CACHE_FILE)).getAsJsonObject();
            }
        }

        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(VERSION_MANIFEST)).build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        Files.writeString(CACHE_FILE, resp.body());
        return JsonParser.parseString(resp.body()).getAsJsonObject();
    }
    
    public JsonObject fetchVersions() throws Exception {
        if (Files.exists(CACHE_FILE)) {
            FileTime lastModified = Files.getLastModifiedTime(CACHE_FILE);
            if (Instant.now().minus(Duration.ofHours(1)).isBefore(lastModified.toInstant())) {
                return JsonParser.parseString(Files.readString(CACHE_FILE)).getAsJsonObject();
            }
        }

        // Download latest version manifest
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(VERSION_MANIFEST)).build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        // Cache locally
        Files.writeString(CACHE_FILE, resp.body());
        return JsonParser.parseString(resp.body()).getAsJsonObject();
    }
    
    public static VersionInfo getVersionInfo(String version) throws Exception {
    	
        if (version.toLowerCase().contains("forge")) {
            Path localVersionJson = baseDir.resolve("versions").resolve(version).resolve(version + ".json");
            if (!Files.exists(localVersionJson))
                throw new IllegalStateException("Forge version JSON not found locally: " + version);

            String jsonContent = Files.readString(localVersionJson);
            JsonObject obj = JsonParser.parseString(jsonContent).getAsJsonObject();

            VersionInfo info = new VersionInfo();
            info.id = version;
            info.url = localVersionJson.toString(); // not used online
            info.releaseTime = Instant.now(); // arbitrary
            return info;
        }

        // Regular Mojang version
        JsonObject manifest = getVersionManifest();
        for (JsonElement e : manifest.getAsJsonArray("versions")) {
            JsonObject obj = e.getAsJsonObject();
            if (obj.get("id").getAsString().equals(version)) {
                VersionInfo info = new VersionInfo();
                info.id = obj.get("id").getAsString();
                info.url = obj.get("url").getAsString();
                info.releaseTime = Instant.parse(obj.get("releaseTime").getAsString());
                return info;
            }
        }
        throw new IllegalArgumentException("Version not found: " + version);
    }

    
    public static VersionMetadata getVersionMetadata(String version, boolean online) throws Exception {
        VersionInfo info;

        if (!online) {
            // --- OFFLINE: load version JSON from disk ---
            Path localVersionJson = baseDir.resolve("versions").resolve(version).resolve(version + ".json");
            if (!Files.exists(localVersionJson)) {
                throw new IllegalStateException("Offline mode: version JSON not found locally for " + version);
            }

            JsonObject meta = JsonParser.parseString(Files.readString(localVersionJson)).getAsJsonObject();
            info = new VersionInfo();
            info.id = version;
            info.url = localVersionJson.toString(); // just to satisfy VersionInfo.url usage
            info.releaseTime = Instant.now(); // arbitrary

            return parseVersionMetadataFromJson(meta, version);

        } else {
            // --- ONLINE: fetch version JSON from Mojang servers ---
            info = getVersionInfo(version);
            Path localVersionJson = baseDir.resolve("versions").resolve(version).resolve(version + ".json");
            
            if (!Files.exists(localVersionJson)) {
            	HttpRequest req = HttpRequest.newBuilder().uri(URI.create(info.url)).build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                JsonObject meta = JsonParser.parseString(resp.body()).getAsJsonObject();
                Path versionDir = baseDir.resolve("versions").resolve(version);
                Files.createDirectories(versionDir);
                Files.writeString(versionDir.resolve(version + ".json"), resp.body());
                return parseVersionMetadataFromJson(meta, version);
            }else {
            	JsonObject meta = JsonParser.parseString(Files.readString(localVersionJson)).getAsJsonObject();
                info = new VersionInfo();
                info.id = version;
                info.url = localVersionJson.toString(); // just to satisfy VersionInfo.url usage
                info.releaseTime = Instant.now(); // arbitrary

                return parseVersionMetadataFromJson(meta, version);
            }
            
            
        }
    }

    // --- helper method to parse the version JSON into VersionMetadata ---
    private static VersionMetadata parseVersionMetadataFromJson(JsonObject meta, String version) throws Exception {
        VersionMetadata result = new VersionMetadata();

     // --- Libraries ---
        JsonArray libs = meta.getAsJsonArray("libraries");
        if (libs != null) {
            for (JsonElement e : libs) {
                JsonObject lib = e.getAsJsonObject();
                if (!lib.has("downloads")) continue;

                JsonObject downloads = lib.getAsJsonObject("downloads");

                // OS-specific keys (multiple options for macOS)
                List<String> osKeys = switch (OSUtils.getOS()) {
                    case WINDOWS -> List.of("natives-windows");
                    case LINUX   -> List.of("natives-linux");
                    case MAC     -> List.of("natives-macos", "natives-osx");
                    default      -> List.of("natives-linux");
                };

                // Add arm64 variants if needed
                if (OSUtils.isArm()) {
                    osKeys = osKeys.stream()
                            .map(k -> k + "-arm64")
                            .collect(Collectors.toList());
                }

                // --- Artifact ---
                if (downloads.has("artifact")) {
                    JsonObject artifact = downloads.getAsJsonObject("artifact");
                    String url = artifact.get("url").getAsString();

                    // Accept universal or OS-specific artifact
                    if (!url.isEmpty() && (osKeys.stream().anyMatch(url::contains) || !url.contains("natives"))) {
                        result.libraries.add(new Downloadable(
                                url,
                                artifact.get("sha1").getAsString(),
                                artifact.get("size").getAsLong(),
                                Paths.get("libraries", artifact.get("path").getAsString())
                        ));
                        LOGGER.info("added artifact lib : " + url);
                    }
                }

                // --- Classifiers ---
                if (downloads.has("classifiers")) {
                    JsonObject classifiers = downloads.getAsJsonObject("classifiers");

                    for (String key : osKeys) {
                        if (classifiers.has(key)) {
                            JsonObject nativeObj = classifiers.getAsJsonObject(key);
                            result.libraries.add(new Downloadable(
                                    nativeObj.get("url").getAsString(),
                                    nativeObj.get("sha1").getAsString(),
                                    nativeObj.get("size").getAsLong(),
                                    Paths.get("libraries", nativeObj.get("path").getAsString())
                            ));
                            LOGGER.info("added native classifier : " + nativeObj.get("url").getAsString());
                        }
                    }
                }
            }
        }


        // --- Client jar ---
        if (meta.has("downloads") && meta.getAsJsonObject("downloads").has("client")) {
            JsonObject clientJson = meta.getAsJsonObject("downloads").getAsJsonObject("client");
            result.clientJar = new Downloadable(
                    clientJson.get("url").getAsString(),
                    clientJson.get("sha1").getAsString(),
                    clientJson.get("size").getAsLong(),
                    Paths.get("versions", version, version + ".jar")
            );
        }

     // --- Assets ---
        if (meta.has("assetIndex")) {
            JsonObject assetIndexJson = meta.getAsJsonObject("assetIndex");
            Path assetIndexPath = baseDir.resolve("assets").resolve("indexes")
                    .resolve(assetIndexJson.get("id").getAsString() + ".json");
            result.assetsIndexId = assetIndexJson.get("id").getAsString();

            if (!Files.exists(assetIndexPath)) {
                try {
                    // Ensure directories exist
                    Files.createDirectories(assetIndexPath.getParent());

                    // Download using your DownloadManager
                    DownloadManager.downloadFile(assetIndexJson.get("url").getAsString(), assetIndexPath);

                    LOGGER.info("Downloaded asset index via DownloadManager: " + assetIndexPath);
                } catch (Exception e) {
                    LOGGER.error("Failed to fetch asset index: " + e);
                    throw new RuntimeException(e);
                }
            }

            JsonObject assetsJson = JsonParser.parseString(Files.readString(assetIndexPath)).getAsJsonObject();
            JsonObject objects = assetsJson.getAsJsonObject("objects");

            for (Map.Entry<String, JsonElement> entry : objects.entrySet()) {
                JsonObject obj = entry.getValue().getAsJsonObject();
                String hash = obj.get("hash").getAsString();
                long size = obj.get("size").getAsLong();

                Path path = baseDir.resolve("assets").resolve("objects").resolve(hash.substring(0, 2)).resolve(hash);
                String url = "https://resources.download.minecraft.net/" + hash.substring(0, 2) + "/" + hash;

                result.assets.add(new Downloadable(url, hash, size, path));
            }
        }


        
        long total = result.libraries.stream().mapToLong(d -> d.size).sum();
        total += result.assets.stream().mapToLong(d -> d.size).sum();
        if (result.clientJar != null) total += result.clientJar.size;
        result.totalSize = total;

        return result;
    }
}