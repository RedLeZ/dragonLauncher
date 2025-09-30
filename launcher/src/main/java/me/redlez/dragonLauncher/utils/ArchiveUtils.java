package me.redlez.dragonLauncher.utils;
import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

import org.apache.commons.compress.archivers.tar.*;
import org.apache.commons.compress.compressors.gzip.*;

public class ArchiveUtils {

    public static void unzipOrUntar(Path archive, Path targetDir) throws Exception {
        String fileName = archive.getFileName().toString().toLowerCase();

        if (fileName.endsWith(".zip")) {
            unzip(archive, targetDir);
        } else if (fileName.endsWith(".tar.gz")) {
            untarGz(archive, targetDir);
        } else {
            throw new IllegalArgumentException("Unsupported archive format: " + fileName);
        }
    }

    private static void unzip(Path zipFile, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path newPath = targetDir.resolve(entry.getName()).normalize();
                if (!newPath.startsWith(targetDir)) {
                    throw new IOException("Bad zip entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(newPath);
                } else {
                    Files.createDirectories(newPath.getParent());
                    Files.copy(zis, newPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    private static void untarGz(Path tarGzFile, Path targetDir) throws IOException {
        try (InputStream fi = Files.newInputStream(tarGzFile);
             InputStream gzi = new GzipCompressorInputStream(fi);
             TarArchiveInputStream tarIn = new TarArchiveInputStream(gzi)) {

            TarArchiveEntry entry;
            while ((entry = tarIn.getNextTarEntry()) != null) {
                Path newPath = targetDir.resolve(entry.getName()).normalize();
                if (!newPath.startsWith(targetDir)) {
                    throw new IOException("Bad tar entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(newPath);
                } else {
                    Files.createDirectories(newPath.getParent());
                    try (OutputStream out = Files.newOutputStream(newPath)) {
                        tarIn.transferTo(out);
                    }
                }
            }
        }
    }
}
