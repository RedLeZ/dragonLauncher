package me.redlez.dragonLauncher.utils;

import java.net.http.*;
import java.net.URI;
import java.io.IOException;
import java.util.*;
import com.google.gson.*;

public class ForgeFetcher {

    private static final String PROMOTIONS_URL = "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";
    private static final String FORGE_MAVEN = "https://maven.minecraftforge.net/net/minecraftforge/forge/";

    public static Map<String, String> getForgeInstallers() throws IOException, InterruptedException {
        Map<String, String> installers = new LinkedHashMap<>();

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(PROMOTIONS_URL))
                .header("User-Agent", "Mozilla/5.0")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonObject promos = root.getAsJsonObject("promos");

        for (Map.Entry<String, JsonElement> entry : promos.entrySet()) {
            String key = entry.getKey(); // e.g. "1.20.1-latest"
            String mcVersion = key.split("-")[0]; // e.g. "1.20.1"
            String forgeVersion = entry.getValue().getAsString(); // e.g. "47.1.44"
            String fullVersion = mcVersion + "-" + forgeVersion;

            String url = FORGE_MAVEN + fullVersion + "/forge-" + fullVersion + "-installer.jar";
            installers.put(fullVersion, url);
        }

        return installers;
    }

    public Map<String, String>  getForgeVersionsaa() throws Exception{
        return getForgeInstallers();
    }

    public static String getForgeInstallerUrl(String forgeKey) throws Exception {
    	return getForgeInstallers().get(forgeKey);
    }
}
