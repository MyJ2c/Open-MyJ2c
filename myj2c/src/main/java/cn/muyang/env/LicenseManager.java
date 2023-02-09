package cn.muyang.env;

import cn.muyang.utils.Base64Util;
import cn.muyang.utils.EncodeUtils;
import cn.muyang.utils.HexUtil;
import cn.muyang.utils.StringUtils;
import com.google.gson.Gson;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public final class LicenseManager {
    public static final Locale locale = Locale.getDefault();
    public static final String KEY_ALGORITHM = "RSA";
    private static final String AES = "AES";

    private static Map<String, String> cache = new HashMap<>();
    private static final String AES_CBC = "AES/CBC/PKCS5Padding";
    private static final String DEFAULT_URL_ENCODING = "UTF-8";

    public static String getValue(String key) {
        if (cache.get(key) == null) {
            String v = r3().get(key);
            v = v == null ? "" : v;
            cache.put(key, v);
            return v;
        } else {
            return cache.get(key);
        }
    }

    public static void printInfo(String code) {
        Map<String, String> licenseInfo = r3();
        String sign = encodeHex(digest(code.getBytes(), "MD5", null, 88));
        if (!sign.equals(licenseInfo.get("sign"))) {
            if (locale.getLanguage().contains("zh")) {
                System.out.println("您当前的版本为试用版 \n机器码:" + code);
            } else {
                System.out.println("Your current version is trial version \nMachine code:" + code);
            }
            return;
        }
        if (licenseInfo.get("type") == null) {
            if (locale.getLanguage().contains("zh")) {
                System.out.println("您当前的版本为试用版 \n机器码:" + code);
            } else {
                System.out.println("Your current version is trial version \nMachine code:" + code);
            }
        } else {
            if (locale.getLanguage().contains("zh")) {
                System.out.println(licenseInfo.get("message") + "\n机器码:" + code);
            } else {
                System.out.println(licenseInfo.get("message") + "\nMachine code:" + code);
            }
        }
    }

    public static String s() {
        String key = null;
        try {
            SystemInfo si = new SystemInfo();
            HardwareAbstractionLayer hal = si.getHardware();
            CentralProcessor processor = hal.getProcessor();
            String cpuModel = processor.getProcessorIdentifier().getName();
            cpuModel = cpuModel == null ? "" : cpuModel;
            String processorID = processor.getProcessorIdentifier().getProcessorID();
            processorID = processorID == null ? "" : processorID;
            List<NetworkIF> networkIFs = hal.getNetworkIFs();
            for (NetworkIF networkIF : networkIFs) {
                int length = 0;
                if (networkIF.getIPv4addr() != null) {
                    length = Array.getLength(networkIF.getIPv4addr());
                }
                if (length > 0) {
                    String address = networkIF.getIPv4addr()[0];
                    if (StringUtils.equals(address, "127.0.0.1") || StringUtils.contains(address, "169.254") || StringUtils.equals(address, "0.0.0.0") || StringUtils.equals("00:00:00:00:00:00", networkIF.getMacaddr())) {
                        continue;
                    }
                    key = encodeHex(digest((cpuModel + processorID + networkIF.getDisplayName() + networkIF.getMacaddr().toUpperCase()).getBytes(), "MD5", null, 76));
                }
                if (key != null) {
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return encodeHex(digest(key.getBytes(), "MD5", null, 66));
    }

    private static Map<String, String> r3() {


        Map<String, String> licenseMap = new HashMap<String, String>();
        try {
            licenseMap.putAll(r2());
            String updateCode = licenseMap.get("updateCode");
            licenseMap.remove("updateCode");
            List<String> list = Arrays.asList(licenseMap.values().toArray(new String[licenseMap.values().size()]));
            Collections.sort(list, String::compareTo);
            if (!StringUtils.equals(encodeHex(digest(join(list.iterator(), "").getBytes(), "MD5", null, 88)), updateCode)) {
                throw new Exception("您当前的版本为未授权版本");
            }
        } catch (Exception e) {
            licenseMap.put("message", e.getMessage());
        }
        try {
            licenseMap.putAll(doValidProduct(licenseMap));
        } catch (Exception e) {
            licenseMap.put("message", e.getMessage());
        }
        return licenseMap;
    }

    public static String join(Iterator<?> iterator, String separator) {
        if (iterator == null) {
            return null;
        } else if (!iterator.hasNext()) {
            return "";
        } else {
            Object first = iterator.next();
            if (!iterator.hasNext()) {
                return Objects.toString(first, "");
            } else {
                StringBuilder buf = new StringBuilder(256);
                if (first != null) {
                    buf.append(first);
                }
                while (iterator.hasNext()) {
                    if (separator != null) {
                        buf.append(separator);
                    }
                    Object obj = iterator.next();
                    if (obj != null) {
                        buf.append(obj);
                    }
                }
                return buf.toString();
            }
        }
    }

    private static Map<String, String> r2() {
        try {
            final String path = System.getProperty("user.dir") + File.separator + "myj2c.licence";
            File file = new File(path);
            byte[] datas = null;
            InputStream in = new FileInputStream(file);  //真正要用到的是FileInputStream类的read()方法
            datas = new byte[in.available()];  //in.available()是得到文件的字节数
            in.read(datas);  //把文件的字节一个一个地填到bytes数组中
            in.close();  //记得要关闭in
            String[] keys = ">>>>QQ:2129575842<<<<`7Km/KZk46sPt7e5xmn6ZIA==`PxXhwSyYKLHQlmcx59Fl6Q==`MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDKO2pNHgavDhPUTQ+i4cUhxJnBrlIzX0YS1106rawF6dmi+hQcvKaupcY5Gs7ROBXKQEYdnzuxTb+ozlv1bkmSwjjDBN2R8zWhXaBn9K59rLgRC8AzjelIu/MHvQ/7B0zsTHBDSMsPfnkB9gn4MEjRaSpiH2TxzNLhPvItmgtD3QIDAQAB".split("`");

            byte[] key = decodeBase64(keys[2]);
            byte[] iv = decodeBase64(keys[1]);

            String licData = new String(decode(datas, key, iv), StandardCharsets.UTF_8);
            //System.out.println("["+licData+"]");
            licData = StringUtils.substringBetween(licData, "\n\n", "\n\n");
            //System.out.println("["+licData+"]");
            licData = StringUtils.replace(licData, "\n", "");
            licData = new String(decodeHex(decrypt(licData, keys[3])), StandardCharsets.UTF_8); //公钥解密
            int beginIndex = 96;
            int endIndex = 96 + 32;
            key = decodeHex(licData.substring(beginIndex, endIndex));
            beginIndex = endIndex;
            iv = decodeHex(licData.substring(beginIndex, endIndex += 32));
            beginIndex = endIndex + 160;
            endIndex = licData.length() - 64 - keys[0].length();
            //System.out.println(beginIndex+","+endIndex+"["+licData.substring(beginIndex, endIndex)+"]");
            String info = new String(decode(decodeHex(licData.substring(beginIndex, endIndex)), key, iv), StandardCharsets.UTF_8);
            Gson gson = new Gson();
            Map<String, String> map = gson.fromJson(info, Map.class);
            String code = map.get("code");
            map.put("sign", encodeHex(digest(code.getBytes(), "MD5", null, 88)));
            return map;
        } catch (Exception e) {
            return new HashMap<>();
        }
    }


    private static String decrypt(String contentBase64, String publicKeyBase64) throws NoSuchAlgorithmException, InvalidKeyException, NoSuchPaddingException, InvalidKeySpecException, IllegalBlockSizeException, BadPaddingException, IOException {
        byte[] decode = decodeBase64(publicKeyBase64);
        X509EncodedKeySpec x509EncodedKeySpec = new X509EncodedKeySpec(decode);
        KeyFactory kf = KeyFactory.getInstance(KEY_ALGORITHM);
        PublicKey publicKey = kf.generatePublic(x509EncodedKeySpec);
        Cipher cipher = Cipher.getInstance(kf.getAlgorithm());
        cipher.init(Cipher.DECRYPT_MODE, publicKey);
        byte[] bytes = decodeHex(new String(decodeBase64(contentBase64), StandardCharsets.UTF_8));
        int inputLen = bytes.length;
        int offLen = 0;
        int i = 0;
        int length = ((RSAPublicKey) publicKey).getModulus().bitLength() / 8;
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        while (inputLen - offLen > 0) {
            byte[] cache;
            if (inputLen - offLen > length) {
                cache = cipher.doFinal(bytes, offLen, length);
            } else {
                cache = cipher.doFinal(bytes, offLen, inputLen - offLen);
            }
            byteArrayOutputStream.write(cache);
            i++;
            offLen = length * i;
        }
        String data = byteArrayOutputStream.toString();
        byteArrayOutputStream.close();
        return decodeBase64String(data);
    }

    private static Map<String, String> doValidProduct(Map<String, String> info) {
        Map<String, String> map = new HashMap<>();
        String type = "9";
        String version = "试用版";

        if (StringUtils.isNotEmpty(info.get("type"))) {
            type = info.get("type");
        }
        map.put("title", "试用版");
        map.put("type", type);
        boolean sameMachine = false;
        String key = null;
        try {
            SystemInfo si = new SystemInfo();
            HardwareAbstractionLayer hal = si.getHardware();
            CentralProcessor processor = hal.getProcessor();
            String cpuModel = processor.getProcessorIdentifier().getName();
            cpuModel = cpuModel == null ? "" : cpuModel;
            String processorID = processor.getProcessorIdentifier().getProcessorID();
            processorID = processorID == null ? "" : processorID;
            List<NetworkIF> networkIFs = hal.getNetworkIFs();
            for (NetworkIF networkIF : networkIFs) {
                if (getLength(networkIF.getIPv4addr()) > 0) {
                    String address = networkIF.getIPv4addr()[0];
                    if (StringUtils.equals(address, "127.0.0.1") || StringUtils.contains(address, "169.254") || StringUtils.equals(address, "0.0.0.0") || StringUtils.equals("00:00:00:00:00:00", networkIF.getMacaddr())) {
                        continue;
                    }
                    key = encodeHex(digest((cpuModel + processorID + networkIF.getDisplayName() + networkIF.getMacaddr().toUpperCase()).getBytes(), "MD5", null, 76));
                }
                if (key != null) {
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (key.equals(StringUtils.trim(info.get("code")))) {
            sameMachine = true;
        }
        if (!sameMachine) {
            throw new RuntimeException("您当前的版本为" + version);
        }
        String sign = encodeHex(digest(info.get("code").getBytes(), "MD5", null, 88));
        if (!sign.equals(info.get("sign"))) {
            throw new RuntimeException("您当前的版本为" + version);
        }
        if (StringUtils.equals("1", type)) {
            version = "个人版";
        } else {
            if (StringUtils.equals("2", type)) {
                version = "专业版";
            }
        }
        String expireDate = info.get("expireDate");
        String authInfo = info.get("name") == null ? " " : "授权用户:" + info.get("name") + " ";
        if (StringUtils.equals("-1", expireDate)) {
            map.put("title", version);
            map.put("message", "您当前的版本为" + version + "," + authInfo + "非常感谢您对我们产品的认可与支持！");
        } else {
            Date date = null;
            try {
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
                date = simpleDateFormat.parse(expireDate);
            } catch (ParseException e) {
                throw new RuntimeException("您当前的版本为" + version);
            }
            long leftTime = date.getTime() - System.currentTimeMillis();
            long leftDay = leftTime / 3600000L / 24L;
            if (leftDay <= 0L) {
                throw new RuntimeException("您的" + version + "许可，于" + formatDate(date, "yyyy年MM月dd日") + "已到期");
            }
            if (leftDay <= 7L) {
                map.put("message", "您当前的版本为" + version + "，" + authInfo + "许可到期时间为：" + formatDate(date, "yyyy年MM月dd日") + " 还剩最后" + leftDay + "天。");
            } else {
                if (leftDay <= 60L) {
                    map.put("message", "您当前的版本为" + version + "，" + authInfo + "许可到期时间为：" + formatDate(date, "yyyy年MM月dd日") + " 还剩余" + leftDay + "天。");
                } else {
                    map.put("message", "您当前的版本为" + version + "，" + authInfo + "许可到期时间为：" + formatDate(date, "yyyy年MM月dd日") + "。");
                }
            }
            map.put("title", version + "（剩余" + leftDay + "天）");
        }
        if (!StringUtils.equals("true", info.get("devlop"))) {
            return map;
        }

        return map;
    }

    public static String formatDate(Date date, String pattern) {
        String formatDate = null;
        if (date != null) {
            if (StringUtils.isBlank(pattern)) {
                pattern = "yyyy-MM-dd";
            }
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
            formatDate = simpleDateFormat.format(date);
        }
        return formatDate;
    }

    /**
     * Hex编码.
     */
    private static String encodeHex(byte[] input) {
        return HexUtil.encodeToString(input);
    }

    /**
     * Hex解码.
     */
    private static byte[] decodeHex(String input) {
        return HexUtil.decode(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Base64解码.
     */
    private static byte[] decodeBase64(String input) {
        try {
            return Base64Util.decrypt(input);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Base64解码.
     */
    private static String decodeBase64String(String input) {
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
     * 使用AES解密字符串, 返回原始字符串.
     *
     * @param input Hex编码的加密字符串
     * @param key   符合AES要求的密钥
     * @param iv    初始向量
     */
    private static byte[] decode(byte[] input, byte[] key, byte[] iv) {
        return aes(input, key, iv, Cipher.DECRYPT_MODE);
    }

    /**
     * 使用AES加密或解密无编码的原始字节数组, 返回无编码的字节数组结果.
     *
     * @param input 原始字节数组
     * @param key   符合AES要求的密钥
     * @param iv    初始向量
     * @param mode  Cipher.ENCRYPT_MODE 或 Cipher.DECRYPT_MODE
     */
    private static byte[] aes(byte[] input, byte[] key, byte[] iv, int mode) {
        try {
            SecretKey secretKey = new SecretKeySpec(key, AES);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            Cipher cipher = Cipher.getInstance(AES_CBC);
            cipher.init(mode, secretKey, ivSpec);
            return cipher.doFinal(input);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 对字符串进行散列, 支持md5与sha1算法.
     *
     * @param input      需要散列的字符串
     * @param algorithm  散列算法（"SHA-1"、"MD5"）
     * @param salt
     * @param iterations 迭代次数
     * @return
     */
    private static byte[] digest(byte[] input, String algorithm, byte[] salt, int iterations) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);

            if (salt != null) {
                digest.update(salt);
            }

            byte[] result = digest.digest(input);

            for (int i = 1; i < iterations; i++) {
                digest.reset();
                result = digest.digest(result);
            }
            return result;
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    private static int getLength(final Object array) {
        if (array == null) {
            return 0;
        }
        return Array.getLength(array);
    }

    public static String v(int i) {
        return encodeHex(digest(getValue("code").getBytes(), "MD5", null, i));
    }
}
