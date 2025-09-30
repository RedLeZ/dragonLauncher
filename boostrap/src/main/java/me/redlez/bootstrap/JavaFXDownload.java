package me.redlez.bootstrap;

public class JavaFXDownload {

    private static final String BASE_URL = "https://repo1.maven.org/maven2/org/openjfx/";
    private static final String VERSION = "21.0.1";
    private static final String[] MODULES = {"javafx-base", "javafx-graphics", "javafx-controls"};
    
    public static String getDownloadUrl(String module) {
        String osSuffix = getOSSuffix();
        return BASE_URL + module + "/" + VERSION + "/" + module + "-" + VERSION + "-" + osSuffix + ".jar";
    }

    private static String getOSSuffix() {
        String osArch = System.getProperty("os.arch").toLowerCase();
        switch (OSUtils.getOS()) {
            case WINDOWS -> {
                return "win";
            }
            case LINUX -> {
                if (osArch.contains("aarch64") || osArch.contains("arm")) {
                    return "linux-aarch64";
                } else {
                    return "linux";
                }
            }
            case MAC -> {
                if (osArch.contains("aarch64") || osArch.contains("arm")) {
                    return "mac-aarch64";
                } else {
                    return "mac";
                }
            }
            default -> throw new IllegalStateException("Unsupported OS");
        }
    }

    public static String[] getAllUrls() {
        String[] urls = new String[MODULES.length];
        for (int i = 0; i < MODULES.length; i++) {
            urls[i] = getDownloadUrl(MODULES[i]);
        }
        return urls;
    }
    
}
