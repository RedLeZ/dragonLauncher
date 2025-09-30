package me.redlez.dragonLauncher.utils;


public final class OSUtils {

    public enum OS {
        WINDOWS("windows"),
        MAC("osx"),
        LINUX("linux"),
        OTHER("linux"); // <--- safest fallback for now

        private final String mojangName;

        OS(String mojangName) {
            this.mojangName = mojangName;
        }

        public String getMojangName() {
            return mojangName;
        }
    }

    public enum ARCH {
        X64,
        ARM,
        OTHER
    }

    private static final String OS_NAME_STR = System.getProperty("os.name").toLowerCase();
    private static final String OS_ARCH_STR = System.getProperty("os.arch").toLowerCase();

    private static final OS DETECTED_OS = detectOS();
    private static final ARCH DETECTED_ARCH = detectArch();

    private OSUtils() {}

    private static OS detectOS() {
        if (OS_NAME_STR.contains("win")) {
            return OS.WINDOWS;
        } else if (OS_NAME_STR.contains("mac") || OS_NAME_STR.contains("os x")) {
            return OS.MAC;
        } else if (OS_NAME_STR.contains("nux") || OS_NAME_STR.contains("nix")) {
            return OS.LINUX;
        } else {
            return OS.OTHER;
        }
    }

    private static ARCH detectArch() {
        if (OS_ARCH_STR.contains("aarch64") || OS_ARCH_STR.contains("arm")) {
            return ARCH.ARM;
        } else if (OS_ARCH_STR.contains("64")) {
            return ARCH.X64;
        } else {
            return ARCH.OTHER;
        }
    }

    public static OS getOS() {
        return DETECTED_OS;
    }

    public static ARCH getArch() {
        return DETECTED_ARCH;
    }

    /**
     * Returns Mojang's OS string ("windows", "osx", "linux").
     */
    public static String getMojangOS() {
        return DETECTED_OS.getMojangName();
    }

    public static boolean is(OS os) {
        return DETECTED_OS == os;
    }

    public static boolean isArm() {
        return DETECTED_ARCH == ARCH.ARM;
    }
}
