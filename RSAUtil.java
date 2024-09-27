package cn.yl.common.utils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;


/**
 * RSA 非对称加密算法工具类
 *
 * @author YL
 * @since 2024-09-26 13:23:55
 */
@SuppressWarnings("all")
public abstract class RSAUtil {

    /**
     * 加密算法
     */
    private static final String KEY_ALGORITHM = "RSA";


    /**
     * RSA位数，采用2048，则加密密文和解密密文需要使用245和256
     */
    private static final Integer INITIALIZE_LENGTH = 2048;

    /**
     * 公钥
     */
    public static String PUBLICKEY = "";

    /**
     * 私钥
     */
    public static String PRIVATEKEY = "";


    /**
     * 生成公钥和私钥
     */
    public static void generateKey() {
        // 1.初始化秘钥
        KeyPairGenerator keyPairGenerator;
        try {
            keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGORITHM);
            // 随机数生成器
            SecureRandom sr = new SecureRandom();
            // 设置秘钥长度
            keyPairGenerator.initialize(INITIALIZE_LENGTH, sr);
            // 开始创建
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            RSAPublicKey rsaPublicKey = (RSAPublicKey) keyPair.getPublic();
            RSAPrivateKey rsaPrivateKey = (RSAPrivateKey) keyPair.getPrivate();
            // 进行转码
            PUBLICKEY = Base64.getEncoder().encodeToString(rsaPublicKey.getEncoded());
            // 进行转码
            PRIVATEKEY = Base64.getEncoder().encodeToString(rsaPrivateKey.getEncoded());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    /**
     * 私钥匙加密或解密
     *
     * @param: content 需要解密的字符串
     * @param: privateKeyStr 私钥
     * @param: opmode Cipher.DECRYPT_MODE=2  解密
     * @return: 解密后的字符串
     */
    public static String encryptByPrivateKey(String content, String privateKeyStr, int opmode) {
        // 私钥要用PKCS8进行处理
        PKCS8EncodedKeySpec pkcs8EncodedKeySpec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyStr));
        KeyFactory keyFactory;
        PrivateKey privateKey;
        Cipher cipher;
        byte[] result;
        String text = null;
        try {
            keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
            // 还原Key对象
            privateKey = keyFactory.generatePrivate(pkcs8EncodedKeySpec);
            cipher = Cipher.getInstance(KEY_ALGORITHM);
            cipher.init(opmode, privateKey);
            //加密解密
            text = encryptTxt(opmode, cipher, content);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return text;
    }

    private static String encryptTxt(int opmode, Cipher cipher, String content) {
        byte[] result;
        String text = null;
        try {
            // 加密
            if (opmode == Cipher.ENCRYPT_MODE) {
                result = cipher.doFinal(content.getBytes());
                text = Base64.getEncoder().encodeToString(result);
            } else if (opmode == Cipher.DECRYPT_MODE) {
                // 解密
                result = cipher.doFinal(Base64.getDecoder().decode(content));
                text = new String(result, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return text;
    }

    /**
     * 公钥匙加密或解密
     *
     * @param: content 需要加密的字符串
     * @param: publicKeyStr 公钥字符串
     * @param: opmode Cipher.ENCRYPT_MODE=1  加密
     * @return: 密文
     */
    public static String encryptByPublicKey(String content, String publicKeyStr, int opmode) {
        // 公钥要用X509进行处理
        X509EncodedKeySpec x509EncodedKeySpec = new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyStr));
        KeyFactory keyFactory;
        PublicKey publicKey;
        Cipher cipher;
        byte[] result;
        String text = null;
        try {
            keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
            // 还原Key对象
            publicKey = keyFactory.generatePublic(x509EncodedKeySpec);
            cipher = Cipher.getInstance(KEY_ALGORITHM);
            cipher.init(opmode, publicKey);
            text = encryptTxt(opmode, cipher, content);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return text;
    }
    /**
     * 上面这个对称加密可能因为长度问题出现加解密失败可以使用下面这个
     */
    public static class AESCipher {

        private static final String KEY_GENERATION_ALG = "PBKDF2WithHmacSHA1";
        private static final int HASH_ITERATIONS = 10000;
        private static final int KEY_LENGTH = 128;
        private static final char[] HUMANPASSPHRASE = "_S3cNrM*C3mBd2014".toCharArray(); // 系统加解密的公共密匙
        private static final byte[] SALT = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0xA, 0xB, 0xC, 0xD, 0xE, 0xF};
        private static final PBEKeySpec MYKEYSPEC = new PBEKeySpec(HUMANPASSPHRASE, SALT, HASH_ITERATIONS, KEY_LENGTH);
        private static final String CIPHERMODEPADDING = "AES/ECB/PKCS5Padding";
        private static final AESCipher CIPHER = new AESCipher();
        private SecretKeySpec skforAES = null;
        private Cipher cipher = null;

        public static AESCipher getInstance() {
            return CIPHER;
        }

        private AESCipher() {
            try {
                SecretKeyFactory keyfactory = SecretKeyFactory.getInstance(KEY_GENERATION_ALG);
                SecretKey sk = keyfactory.generateSecret(MYKEYSPEC);
                cipher = Cipher.getInstance(CIPHERMODEPADDING);// "算法/模式/补码方式"
                byte[] skAsByteArray = sk.getEncoded();
                skforAES = new SecretKeySpec(skAsByteArray, "AES");

            } catch (Exception e) {

            }
        }

        // 加密
        public String Encrypt(String sSrc) {
            try {
                cipher.init(Cipher.ENCRYPT_MODE, skforAES);
                byte[] encrypted = cipher.doFinal(sSrc.getBytes(StandardCharsets.UTF_8));
                return org.apache.commons.codec.binary.Base64.encodeBase64String(encrypted);// 此处使用BASE64做转码功能，同时能起到2次加密的作用。
            } catch (Exception e) {

            }
            return null;
        }

        // 解密
        public String Decrypt(String sSrc) {
            try {
                cipher.init(Cipher.DECRYPT_MODE, skforAES);
                byte[] encrypted1 = org.apache.commons.codec.binary.Base64.decodeBase64(sSrc);// 先用base64解密
                byte[] original = cipher.doFinal(encrypted1);
                return new String(original, StandardCharsets.UTF_8);
            } catch (Exception ex) {

            }
            return null;
        }
    }
}
