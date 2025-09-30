package me.redlez.dragonLauncher.launcher.Runners;

import com.google.gson.*;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import me.redlez.dragonLauncher.Main;
import me.redlez.dragonLauncher.utils.*;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class VanillaRunner implements GameRunner {

	private final String version;
	private String forgeVersion = null;
	private static LoggerUtil LOGGER = new LoggerUtil("VanillaRunner");
	private final Path baseDir = Paths.get(System.getProperty("user.home"), ".minecraft");

	public VanillaRunner(String version) {
		this.version = version;
	}

	public void setForgeModLibraries(String forgeVersion) {
		this.forgeVersion = forgeVersion;
		LOGGER = new LoggerUtil("ForgeRunner");
	}

	@Override
	public void launch(String playerName, ProgressBar progressBar, Label progressLabel) throws Exception {
		DownloadManager dM = new DownloadManager(version);
		dM.setDownloadListener(new DownloadListener() {
			@Override
			public void onByteProgress(String fileName, long fileDownloaded, long globalDownloaded, long globalTotal) {
				double progress = (double) globalDownloaded / globalTotal;
				Platform.runLater(() -> {
					progressBar.setProgress(progress);
					progressLabel.setText("Downloaded: (" + GameRunner.humanReadableSize(globalDownloaded) + "/"
							+ GameRunner.humanReadableSize(globalTotal) + ")");
				});
			}

			@Override
			public void onProgress(String fileName, long globalDownloaded, long globalTotal) {
			}

			@Override
			public void onError(String fileName, Exception e, long globalDownloaded, long globalTotal) {
				Platform.runLater(() -> progressLabel.setText("Download failed for: " + fileName));
				e.printStackTrace();
			}
		});

		new Thread(() -> {
			try {
				if (dM.isOnline()) {
					dM.downloadAll();
					dM.waitForCompletion();
				}
				if (dM.allFilesAvailableOffline(version)) {
					Platform.runLater(() -> progressLabel.setText("Download complete! Launching..."));
					new Thread(() -> {
						try {
							launchMinecraft(playerName, progressBar, progressLabel);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}).start();
					Platform.runLater(() -> Main.getmc().toggleDownloadUi(false));
				}
			} catch (Exception e) {
				e.printStackTrace();
				Platform.runLater(() -> progressLabel.setText("Download failed!"));
			}
		}).start();
	}

	private void launchMinecraft(String playerName, ProgressBar progressBar, Label progressLabel) {
		try {
			Path versionJsonPath = baseDir.resolve("versions").resolve(version).resolve(version + ".json");
			if (!Files.exists(versionJsonPath))
				throw new Exception("version.json not found: " + versionJsonPath);

			String jsonContent = Files.readString(versionJsonPath);
			JsonObject versionJson = JsonParser.parseString(jsonContent).getAsJsonObject();
			String mainClass = versionJson.get("mainClass").getAsString();
			LOGGER.info("Main Class: " + mainClass);

			List<String> classpath = buildClasspath(versionJson);
			List<String> jvmArgs = parseJvmArgs(versionJson);
			List<String> gameArgs = parseGameArgs(versionJson, playerName);
			if (forgeVersion != null) {
				String forgeMainClass = injectForge(versionJson, classpath, jvmArgs, gameArgs);
				if (forgeMainClass != null)
					mainClass = forgeMainClass;
			}

			// Deduplicate classpath
			classpath = new ArrayList<>(new LinkedHashSet<>(classpath));
			Path nativesDir = baseDir.resolve("versions").resolve(version).resolve(version + "-natives");
			jvmArgs.removeIf(arg -> arg.contains("-cp") || arg.contains("${classpath}") || arg.contains("-Dos.name=")
					|| arg.startsWith("-Djava.library.path="));

			jvmArgs.add("-Djava.library.path=" + nativesDir);

			// Determine the OS name
			String osName;
			switch (OSUtils.getOS()) {
			case WINDOWS -> osName = "Windows 10";
			case MAC -> osName = "Mac OS X";
			case LINUX -> osName = "Linux";
			default -> osName = "Unknown";
			}

			// Add the OS argument
			jvmArgs.add("-Dos.name=" + osName);

			// Add the classpath
			jvmArgs.add("-cp");
			jvmArgs.add(String.join(File.pathSeparator, classpath));

			// Build placeholder map
			Map<String, String> placeholders = new HashMap<>();
			placeholders.put("version_name", version);
			placeholders.put("library_directory", baseDir.resolve("libraries").toString());
			placeholders.put("classpath_separator", File.pathSeparator);
			placeholders.put("auth_player_name", playerName);
			placeholders.put("game_directory", baseDir.toString());
			placeholders.put("assets_root", baseDir.resolve("assets").toString());
			placeholders.put("assets_index_name", getAssetId(version)); // usually same as version
			placeholders.put("auth_uuid", UUID.randomUUID().toString());
			placeholders.put("auth_access_token", "token");
			placeholders.put("user_type", "msa");
			placeholders.put("version_type", "release");
			placeholders.put("resolution_width", "" + SettingsUtil.getWidth());
			placeholders.put("resolution_height", "" + SettingsUtil.getHeight());
			// TODO : add place holders

			jvmArgs = substitutePlaceholders(jvmArgs, placeholders);
			gameArgs = substitutePlaceholders(gameArgs, placeholders);

			// Determine Java executable
			String javaExec = SettingsUtil.getJavaPath();
			Platform.runLater(() -> progressLabel.setText("Getting Java..."));
			if (javaExec.isEmpty() || !new File(javaExec).exists())
				javaExec = JavaManager.JavaPath(version).toString();

			// Build final command
			List<String> command = new ArrayList<>();
			command.add(javaExec);
			command.addAll(jvmArgs);
			command.add(mainClass);
			command.addAll(gameArgs);

			command.removeIf(arg -> arg.contains("${quickPlay}") || arg.equals("--demo"));

			LOGGER.info("Launching Minecraft...");
			LOGGER.info("Command length: " + command.size());
			LOGGER.info("Command: " + String.join(" ", command));
			
			Platform.runLater(() -> progressLabel.setText("Launching minecraft... : " + version));
			
			ProcessBuilder pb = new ProcessBuilder(command);
			pb.directory(baseDir.toFile());
			pb.inheritIO();
			Process process = pb.start();
			int exitCode = process.waitFor();
			LOGGER.info("Minecraft exited with code: " + exitCode);
			Platform.runLater(() -> progressLabel.setText("Minecraft exited: " + exitCode));

		} catch (Exception e) {
			e.printStackTrace();
			Platform.runLater(() -> progressLabel.setText("Launch failed: " + e.getMessage()));
		}
	}

	private List<String> substitutePlaceholders(List<String> args, Map<String, String> placeholders) {
		return args.stream().map(arg -> {
			for (Map.Entry<String, String> entry : placeholders.entrySet()) {
				arg = arg.replace("${" + entry.getKey() + "}", entry.getValue());
			}
			return arg;
		}).collect(Collectors.toList());
	}

	private List<String> buildClasspath(JsonObject versionJson) throws Exception {
		List<String> classpath = new ArrayList<>();
		if (forgeVersion == null)
			classpath.add(baseDir.resolve("versions").resolve(version).resolve(version + ".jar").toString());
		if (versionJson.has("libraries")) {
			for (JsonElement libEl : versionJson.getAsJsonArray("libraries")) {
				JsonObject lib = libEl.getAsJsonObject();
				if (!isLibraryAllowed(lib))
					continue;
				Path libPath = getLibraryPath(lib.get("name").getAsString());
				if (libPath != null && Files.exists(libPath))
					classpath.add(libPath.toString());
			}
		}
		return classpath;
	}

	private String injectForge(JsonObject versionJson, List<String> classpath, List<String> jvmArgs,
			List<String> gameArgs) throws Exception {
		Path forgeJson = baseDir.resolve("versions").resolve(forgeVersion).resolve(forgeVersion + ".json");
		if (!Files.exists(forgeJson))
			return null;

		String forgeJsonContent = Files.readString(forgeJson);
		JsonObject forgeJsonObj = JsonParser.parseString(forgeJsonContent).getAsJsonObject();

		String forgeMainClass = null;
		if (forgeJsonObj.has("mainClass")) {
			forgeMainClass = forgeJsonObj.get("mainClass").getAsString();
			LOGGER.info("[ForgeInjection] injected MainClass: " + forgeMainClass);
		}

		if (forgeJsonObj.has("arguments") && forgeJsonObj.get("arguments").isJsonObject()) {
			JsonObject arguments = forgeJsonObj.getAsJsonObject("arguments");
			if (arguments.has("jvm"))
				jvmArgs.addAll(extractArgs(arguments.getAsJsonArray("jvm"), "Player"));
			if (arguments.has("game"))
				gameArgs.addAll(extractArgs(arguments.getAsJsonArray("game"), "Player"));
		}

		if (forgeJsonObj.has("libraries")) {
			for (JsonElement libEl : forgeJsonObj.getAsJsonArray("libraries")) {
				JsonObject lib = libEl.getAsJsonObject();
				JsonObject downloads = lib.getAsJsonObject("downloads");
				if (downloads != null && downloads.has("artifact")) {
					JsonObject artifact = downloads.getAsJsonObject("artifact");
					if (artifact != null && artifact.has("path")) {
						Path libPath = baseDir.resolve("libraries").resolve(artifact.get("path").getAsString());
						if (Files.exists(libPath))
							classpath.add(libPath.toString());
					}
				}
			}
		}

		return forgeMainClass;
	}

	private boolean isLibraryAllowed(JsonObject lib) {
		if (!lib.has("rules"))
			return true;
		boolean allowed = true;
		for (JsonElement ruleEl : lib.getAsJsonArray("rules")) {
			JsonObject rule = ruleEl.getAsJsonObject();
			String action = rule.get("action").getAsString();
			if (rule.has("os")) {
				boolean osMatches = rule.getAsJsonObject("os").get("name").getAsString().equals(OSUtils.getMojangOS());
				if (action.equals("allow"))
					allowed = osMatches;
				else if (action.equals("disallow") && osMatches)
					return false;
			} else
				allowed = action.equals("allow");
		}
		return allowed;
	}

	private Path getLibraryPath(String name) {
		String[] parts = name.split(":");
		if (parts.length < 3)
			return null;
		Path path = baseDir.resolve("libraries");
		for (String pkg : parts[0].split("\\."))
			path = path.resolve(pkg);
		path = path.resolve(parts[1]).resolve(parts[2]);
		return path.resolve(parts[1] + "-" + parts[2] + ".jar");
	}

	private List<String> parseJvmArgs(JsonObject versionJson) {
		List<String> jvmArgs = new ArrayList<>();
		if (versionJson.has("arguments") && versionJson.get("arguments").isJsonObject()) {
			JsonObject arguments = versionJson.getAsJsonObject("arguments");
			if (arguments.has("jvm"))
				jvmArgs.addAll(extractArgs(arguments.getAsJsonArray("jvm"), "Player"));
		}
		return jvmArgs;
	}

	private List<String> parseGameArgs(JsonObject versionJson, String playerName) {
		if (!versionJson.has("arguments") || !versionJson.get("arguments").isJsonObject())
			return new ArrayList<>();
		return extractArgs(versionJson.getAsJsonObject("arguments").getAsJsonArray("game"), playerName);
	}

	private List<String> extractArgs(JsonArray array, String playerName) {
		List<String> args = new ArrayList<>();
		for (JsonElement el : array) {
			if (el.isJsonPrimitive())
				args.add(el.getAsString().replace("${player_name}", playerName));
			else if (el.isJsonObject() && el.getAsJsonObject().has("value")) {
				JsonElement val = el.getAsJsonObject().get("value");
				if (val.isJsonArray())
					val.getAsJsonArray().forEach(e -> args.add(e.getAsString().replace("${player_name}", playerName)));
				else
					args.add(val.getAsString().replace("${player_name}", playerName));
			}
		}
		return args;
	}

	private String getAssetId(String version) throws Exception {
		return VersionHandler.getVersionMetadata(version, DownloadManager.isOnline()).assetsIndexId;
	}
}
