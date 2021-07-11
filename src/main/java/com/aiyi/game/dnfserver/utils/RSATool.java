package com.aiyi.game.dnfserver.utils;

import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import javax.crypto.Cipher;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * RSA加密工具类
 * 使用PKCS1_PADDING填充，密钥长度1024
 * 加解密结果在这里测试通过：http://tool.chacuo.net/cryptrsaprikey
 * 注意加密内容的编码要一致，统一UTF-8比较好
 * @author daxi
 */
public class RSATool {
    public static final String KEY_ALGORTHM="RSA";
    public static final String SIGNATURE_ALGORITHM="MD5withRSA";

    public static final String PUBLIC_KEY = "RSAPublicKey";//公钥
    public static final String PRIVATE_KEY = "RSAPrivateKey";//私钥

    /**
     * 初始化密钥
     * RSA加密解密的实现，需要有一对公私密钥，公私密钥的初始化如下
     * 非对称加密一般都用于加密对称加密算法的密钥，而不是直接加密内容
     * @return
     * @throws Exception
     */
    public static Map<String,Object> initKey()throws Exception{
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGORTHM);
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        //公钥
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        //私钥
        RSAPrivateKey privateKey =  (RSAPrivateKey) keyPair.getPrivate();

        Map<String,Object> keyMap = new HashMap<String, Object>(2);
        keyMap.put(PUBLIC_KEY, publicKey);
        keyMap.put(PRIVATE_KEY, privateKey);

        return keyMap;
    }

    /**
     * 取得公钥，并转化为String类型
     * @param keyMap
     * @return
     * @throws Exception
     */
    public static String getPublicKey(Map<String, Object> keyMap)throws Exception{
        Key key = (Key) keyMap.get(PUBLIC_KEY);
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /**
     * 取得私钥，并转化为String类型
     * @param keyMap
     * @return
     * @throws Exception
     */
    public static String getPrivateKey(Map<String, Object> keyMap) throws Exception{
        Key key = (Key) keyMap.get(PRIVATE_KEY);
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /**
     * 用私钥加密
     * @param data	加密数据
     * @param key	密钥
     * @return
     * @throws Exception
     */
    public static byte[] encryptByPrivateKey(byte[] data,String key){
        //解密密钥
        byte[] keyBytes = Base64.getDecoder().decode(key);
        try{
            //取私钥
            PKCS8EncodedKeySpec pkcs8EncodedKeySpec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORTHM);
            Key privateKey = keyFactory.generatePrivate(pkcs8EncodedKeySpec);

            //对数据加密
            Cipher cipher = Cipher.getInstance(keyFactory.getAlgorithm());
            cipher.init(Cipher.ENCRYPT_MODE, privateKey);

            return cipher.doFinal(data);
        }catch (Exception e){
            throw new RuntimeException(e);
        }

    }

    /**
     * 用私钥加密(PKCS1)
     * @param data	加密数据
     * @param key	密钥
     * @return
     * @throws Exception
     */
    public static byte[] encryptByPrivateKey(byte[] data,byte[] key){
        try{
            //取私钥
            PEMParser pemParser = new PEMParser(new InputStreamReader(new ByteArrayInputStream(key)));
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
            Object object = pemParser.readObject();
            KeyPair kp = converter.getKeyPair((PEMKeyPair) object);
            PrivateKey privateKey = kp.getPrivate();
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORTHM);

            //对数据加密
            Cipher cipher = Cipher.getInstance(keyFactory.getAlgorithm());
            cipher.init(Cipher.ENCRYPT_MODE, privateKey);

            return cipher.doFinal(data);
        }catch (Exception e){
            throw new RuntimeException(e);
        }

    }

    /**
     * 用私钥解密 * @param data 	加密数据
     * @param key	密钥
     * @return
     * @throws Exception
     */
    public static byte[] decryptByPrivateKey(byte[] data,String key)throws Exception{
        //对私钥解密
        byte[] keyBytes = Base64.getDecoder().decode(key);

        PKCS8EncodedKeySpec pkcs8EncodedKeySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORTHM);
        Key privateKey = keyFactory.generatePrivate(pkcs8EncodedKeySpec);
        //对数据解密
        Cipher cipher = Cipher.getInstance(keyFactory.getAlgorithm());
        cipher.init(Cipher.DECRYPT_MODE, privateKey);

        return cipher.doFinal(data);
    }

    /**
     * 用公钥加密
     * @param data	加密数据
     * @param key	密钥
     * @return
     * @throws Exception
     */
    public static byte[] encryptByPublicKey(byte[] data,String key)throws Exception{
        //对公钥解密
        byte[] keyBytes = Base64.getDecoder().decode(key);
        //取公钥
        X509EncodedKeySpec x509EncodedKeySpec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORTHM);
        Key publicKey = keyFactory.generatePublic(x509EncodedKeySpec);

        //对数据解密
        Cipher cipher = Cipher.getInstance(keyFactory.getAlgorithm());
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);

        return cipher.doFinal(data);
    }

    /**
     * 用公钥解密
     * @param data	加密数据
     * @param key	密钥
     * @return
     * @throws Exception
     */
    public static byte[] decryptByPublicKey(byte[] data,String key)throws Exception{
        //对私钥解密
        byte[] keyBytes = Base64.getDecoder().decode(key);
        X509EncodedKeySpec x509EncodedKeySpec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORTHM);
        Key publicKey = keyFactory.generatePublic(x509EncodedKeySpec);

        //对数据解密
        Cipher cipher = Cipher.getInstance(keyFactory.getAlgorithm());
        cipher.init(Cipher.DECRYPT_MODE, publicKey);

        return cipher.doFinal(data);
    }


    /**************************************************************************
     * 通过RSA加密解密算法，我们可以实现数字签名的功能。我们可以用私钥对信息生
     * 成数字签名，再用公钥来校验数字签名，当然也可以反过来公钥签名，私钥校验。
     * *************************************************************************/

    /**
     *	用私钥对信息生成数字签名
     * @param data	//加密数据
     * @param privateKey	//私钥
     * @return
     * @throws Exception
     */
    public static String sign(byte[] data,String privateKey)throws Exception{
        //解密私钥
        byte[] keyBytes = Base64.getDecoder().decode(privateKey);
        //构造PKCS8EncodedKeySpec对象
        PKCS8EncodedKeySpec pkcs8EncodedKeySpec = new PKCS8EncodedKeySpec(keyBytes);
        //指定加密算法
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORTHM);
        //取私钥匙对象
        PrivateKey privateKey2 = keyFactory.generatePrivate(pkcs8EncodedKeySpec);
        //用私钥对信息生成数字签名
        Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
        signature.initSign(privateKey2);
        signature.update(data);

        return Base64.getEncoder().encodeToString(signature.sign());
    }

    /**
     * 校验数字签名
     * @param data	加密数据
     * @param publicKey	公钥
     * @param sign	数字签名
     * @return
     * @throws Exception
     */
    public static boolean verify(byte[] data,String publicKey,String sign)throws Exception{
        //解密公钥
        byte[] keyBytes = Base64.getDecoder().decode(publicKey);
        //构造X509EncodedKeySpec对象
        X509EncodedKeySpec x509EncodedKeySpec = new X509EncodedKeySpec(keyBytes);
        //指定加密算法
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORTHM);
        //取公钥匙对象
        PublicKey publicKey2 = keyFactory.generatePublic(x509EncodedKeySpec);

        Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
        signature.initVerify(publicKey2);
        signature.update(data);
        //验证签名是否正常
        return signature.verify(Base64.getDecoder().decode(sign));

    }

    public static void main(String[] args) throws Exception {
        // 生产密钥
        Map<String, Object> keys = RSATool.initKey();

        // 打印私钥
        System.out.println("↓↓↓↓↓the following is private key↓↓↓↓↓");
        System.out.println("-----BEGIN PRIVATE KEY-----");
        System.out.println(RSATool.getPrivateKey(keys));
        System.out.println("-----END PRIVATE KEY-----");
        System.out.println("↑↑↑↑↑the above is private key↑↑↑↑↑");

        System.out.println();

        // 打印公钥
        System.out.println("↓↓↓↓↓the following is public key↓↓↓↓↓");
        System.out.println("-----BEGIN PUBLIC KEY-----");
        System.out.println(RSATool.getPublicKey(keys));
        System.out.println("-----END PUBLIC KEY-----");
        System.out.println("↑↑↑↑↑the above is public key↑↑↑↑↑");

        System.out.println(Base64.getEncoder().encodeToString(MinFieldUtil.readFile(new File("/Users/gsk/Desktop/PHP/pkcs8_der.key"))));


    }
}