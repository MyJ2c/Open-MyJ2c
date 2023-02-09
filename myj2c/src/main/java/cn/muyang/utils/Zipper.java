package cn.muyang.utils;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Zipper {

    public static void extract(Path archive, Path target) throws IOException {
        String name = archive.toFile().getName();
        if (name.contains("zip")) {
            unzip(archive, target);
        } else if (name.contains("tar.xz")) {
            unTarXZ(archive, target);
        } else if (name.contains("tar.xz")) {
            unTarXZ(archive, target);
        } else if (name.contains("tar")) {
            unTar(archive, target);
        } else {
            throw new RuntimeException("unsupported content type ");
        }
        /*switch (contentType) {
            case "application/zip":
                unzip(archive, target);
                break;
            case "application/tar":
                unTar(archive, target);
                break;
            case "application/x-xz":
                unTarXZ(archive, target);
                break;
            default:
                throw new RuntimeException("unsupported content type " + contentType);
        }*/
    }


    public static void unzip(Path zip, Path target) throws IOException {
        try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zip)))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                extractEntry(target, zin, entry.getName(), entry.isDirectory());
            }
        }
    }

    public static void unXzip(Path xzip, Path target) throws IOException {
        try (final XZCompressorInputStream xin =
                     new XZCompressorInputStream(new BufferedInputStream(Files.newInputStream(xzip)))) {
            Files.copy(xin, target);
        }
    }

    public static void unTar(Path tar, Path target) throws IOException {
        try (TarArchiveInputStream tin =
                     new TarArchiveInputStream(new BufferedInputStream(Files.newInputStream(tar)))) {
            TarArchiveEntry entry;
            while ((entry = tin.getNextTarEntry()) != null) {
                extractEntry(target, tin, entry.getName(), entry.isDirectory());
            }
        }
    }

    public static void unTarXZ(Path tar, Path target) throws IOException {
        try (XZCompressorInputStream xzcis = new XZCompressorInputStream(new BufferedInputStream(Files.newInputStream(tar)));
             TarArchiveInputStream tin = new TarArchiveInputStream(xzcis, 1024);) {
            TarArchiveEntry entry;
            while ((entry = tin.getNextTarEntry()) != null) {
                extractEntry(target, tin, entry.getName(), entry.isDirectory());
            }
        }
    }

    private static void extractEntry(Path target,
                                     InputStream in,
                                     String entryName,
                                     boolean isDirectory) throws IOException {
        final Path entryPath = target.resolve(entryName);
        if (isDirectory) {
            Files.createDirectories(entryPath);
        } else {
            final Path dir = entryPath.getParent();
            Files.createDirectories(dir);
            Files.copy(in, entryPath);
        }
    }
}
