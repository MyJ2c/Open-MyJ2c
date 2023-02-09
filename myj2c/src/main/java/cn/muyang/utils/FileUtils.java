package cn.muyang.utils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;

public class FileUtils {
    private static final String ENCRYPT_ALGO = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128; // must be one of {128, 120, 112, 104, 96}
    private static final int IV_LENGTH_BYTE = 12;
    private static final int SALT_LENGTH_BYTE = 16;

    private static final Charset UTF_8 = StandardCharsets.UTF_8;
    public static final Locale locale = Locale.getDefault();

    public static byte[] encrypt(byte[] pText, String password) throws Exception {

        // 16 bytes salt
        byte[] salt = CryptoUtils.getRandomNonce(SALT_LENGTH_BYTE);

        // GCM recommended 12 bytes iv?
        byte[] iv = CryptoUtils.getRandomNonce(IV_LENGTH_BYTE);

        // secret key from password
        SecretKey aesKeyFromPassword = CryptoUtils.getAESKeyFromPassword(password.toCharArray(), salt);

        Cipher cipher = Cipher.getInstance(ENCRYPT_ALGO);

        // ASE-GCM needs GCMParameterSpec
        cipher.init(Cipher.ENCRYPT_MODE, aesKeyFromPassword, new GCMParameterSpec(TAG_LENGTH_BIT, iv));

        byte[] cipherText = cipher.doFinal(pText);

        // prefix IV and Salt to cipher text
        byte[] cipherTextWithIvSalt = ByteBuffer.allocate(iv.length + salt.length + cipherText.length)
                .put(iv)
                .put(salt)
                .put(cipherText)
                .array();

        return cipherTextWithIvSalt;

    }

    // we need the same password, salt and iv to decrypt it
    private static byte[] decrypt(byte[] cText, String password) throws Exception {

        // get back the iv and salt that was prefixed in the cipher text
        ByteBuffer bb = ByteBuffer.wrap(cText);

        byte[] iv = new byte[12];
        bb.get(iv);

        byte[] salt = new byte[16];
        bb.get(salt);

        byte[] cipherText = new byte[bb.remaining()];
        bb.get(cipherText);

        // get back the aes key from the same password and salt
        SecretKey aesKeyFromPassword = CryptoUtils.getAESKeyFromPassword(password.toCharArray(), salt);

        Cipher cipher = Cipher.getInstance(ENCRYPT_ALGO);

        cipher.init(Cipher.DECRYPT_MODE, aesKeyFromPassword, new GCMParameterSpec(TAG_LENGTH_BIT, iv));

        byte[] plainText = cipher.doFinal(cipherText);

        return plainText;

    }

    public static void encryptFile(Path fromFile, Path toFile, String password) throws Exception {

        // read a normal txt file
        byte[] fileContent = Files.readAllBytes(fromFile);

        // encrypt with a password
        byte[] encryptedText = FileUtils.encrypt(fileContent, password);

        // save a file

        Files.write(toFile, encryptedText);

    }

    public static byte[] decryptFile(String fromEncryptedFile, String password) throws Exception {

        // read a file
        byte[] fileContent = Files.readAllBytes(Paths.get(fromEncryptedFile));

        return FileUtils.decrypt(fileContent, password);

    }

    public static void decryptToFile(Path fromFile, Path toFile, String password) throws Exception {

        // read a file
        byte[] fileContent = Files.readAllBytes(fromFile);

        Files.write(toFile, FileUtils.decrypt(fileContent, password));
    }

    public static void clearDirectory(String path) {
        // 1、判断文件夹是否存在
        File directory = new File(path);
        if (!directory.exists()) {
            return;
        }

        final Path basePath = directory.toPath();
        try {
            Files.walkFileTree(basePath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    // 根据情况看需不需要删除根目录
                    //if (!dir.toString().equals(basePath.toString())) {
                    Files.delete(dir);
                    //}
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) {
            if (locale.getLanguage().contains("zh")) {
                System.out.println("删除" + path + "失败,请先手动删除后再运行本程序!");
            } else {
                System.out.println("Failed to delete " + path + ". Please delete it manually before running this program!");
            }
        }
    }

}
