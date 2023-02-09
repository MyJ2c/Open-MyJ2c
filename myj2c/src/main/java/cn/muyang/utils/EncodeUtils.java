package cn.muyang.utils;


import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

public class EncodeUtils {

    public static final String UTF_8 = "UTF-8";

    private static final char[] BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    /**
     * Hex编码.
     */
    public static String encodeHex(byte[] input) {
        return HexUtil.encodeToString(input);
    }

    /**
     * Hex解码.
     */
    public static byte[] decodeHex(String input) {
        return HexUtil.decode(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Base64编码.
     */
    public static String encodeBase64(byte[] input) {
        return Base64Util.encrypt(input);
    }

    /**
     * Base64编码.
     */
    public static String encodeBase64(String input) {
        if (StringUtils.isBlank(input)) {
            return "";
        }
        try {
            return Base64Util.encrypt(input.getBytes(EncodeUtils.UTF_8));
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }


    /**
     * Base64解码.
     */
    public static byte[] decodeBase64(String input) {
        try {
            return Base64Util.decrypt(input);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Base64解码.
     */
    public static String decodeBase64String(String input) {
        if (StringUtils.isBlank(input)) {
            return "";
        }
        try {
            return new String(Base64Util.decrypt(input), EncodeUtils.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Base62编码。
     */
    public static String encodeBase62(byte[] input) {
        char[] chars = new char[input.length];
        for (int i = 0; i < input.length; i++) {
            chars[i] = BASE62[((input[i] & 0xFF) % BASE62.length)];
        }
        return new String(chars);
    }

}
