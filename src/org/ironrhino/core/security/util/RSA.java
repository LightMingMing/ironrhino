package org.ironrhino.core.security.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.SoftReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Enumeration;
import java.util.stream.Collectors;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.apache.commons.lang3.StringUtils;
import org.ironrhino.core.util.AppInfo;
import org.ironrhino.core.util.AppInfo.Stage;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RSA {

	public static final String DEFAULT_KEY_LOCATION = "/resources/key/rsa";
	public static final String KEY_DIRECTORY = "/key/";
	// thread safe
	private static final ThreadLocal<SoftReference<RSA>> pool = new ThreadLocal<SoftReference<RSA>>() {
		@Override
		protected SoftReference<RSA> initialValue() {
			try {
				return new SoftReference<>(new RSA());
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	};

	private static URI defaultKeystoreURI;
	private static String defaultPassword;

	static {
		File file = new File(AppInfo.getAppHome() + KEY_DIRECTORY + "rsa");
		if (file.exists()) {
			defaultKeystoreURI = file.toURI();
			log.info("using file " + file.getAbsolutePath());
		} else {
			if (AppInfo.getStage() == Stage.PRODUCTION)
				log.warn("file " + file.getAbsolutePath()
						+ " doesn't exists, please use your own keystore in production!");
			if (RSA.class.getResource(DEFAULT_KEY_LOCATION) != null) {
				try {
					defaultKeystoreURI = RSA.class.getResource(DEFAULT_KEY_LOCATION).toURI();
					log.info("using classpath resource " + RSA.class.getResource(DEFAULT_KEY_LOCATION).toString()
							+ " as default keystore");
				} catch (URISyntaxException e) {
					log.error(e.getMessage(), e);
				}
			}
		}
		String s = System.getProperty(AppInfo.getAppName() + ".rsa.password");
		if (StringUtils.isNotBlank(s)) {
			defaultPassword = s;
			log.info("using system property " + AppInfo.getAppName() + ".rc4 as default key");
		} else {
			try {
				file = new File(AppInfo.getAppHome() + KEY_DIRECTORY + "rsa.password");
				if (file.exists()) {
					try (BufferedReader br = new BufferedReader(
							new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
						defaultPassword = br.lines().collect(Collectors.joining("\n"));
						log.info("using file " + file.getAbsolutePath());
					}
				} else {
					if (AppInfo.getStage() == Stage.PRODUCTION)
						log.warn("file " + file.getAbsolutePath()
								+ " doesn't exists, please use your own default key in production!");
					if (RSA.class.getResource(DEFAULT_KEY_LOCATION) != null) {
						try (BufferedReader br = new BufferedReader(
								new InputStreamReader(RSA.class.getResourceAsStream(DEFAULT_KEY_LOCATION + ".password"),
										StandardCharsets.UTF_8))) {
							defaultPassword = br.lines().collect(Collectors.joining("\n"));
							log.info("using classpath resource "
									+ RSA.class.getResource(DEFAULT_KEY_LOCATION + ".password").toString()
									+ " as default key");
						}
					}
				}
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
		}

	}

	private PrivateKey privateKey;
	private PublicKey publicKey;
	private X509Certificate certificate;

	public RSA() throws Exception {
		this(defaultKeystoreURI.toURL().openStream(), defaultPassword);
	}

	public RSA(InputStream is, String password) throws Exception {
		KeyStore ks = KeyStore.getInstance("pkcs12", "SunJSSE");
		try (InputStream ins = is) {
			ks.load(ins, password.toCharArray());
		}
		Enumeration<String> aliases = ks.aliases();
		if (aliases.hasMoreElements()) {
			String alias = aliases.nextElement();
			privateKey = (PrivateKey) ks.getKey(alias, password.toCharArray());
			Certificate[] cc = ks.getCertificateChain(alias);
			certificate = (X509Certificate) cc[0];
			publicKey = certificate.getPublicKey();
		}
	}

	public X509Certificate getCertificate() {
		return certificate;
	}

	public byte[] encrypt(byte[] input) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException,
			IllegalBlockSizeException, BadPaddingException {
		Cipher cipher = Cipher.getInstance("RSA/ECB/NoPadding");
		cipher.init(Cipher.ENCRYPT_MODE, publicKey);
		return cipher.doFinal(input);
	}

	public byte[] decrypt(byte[] input) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException,
			IllegalBlockSizeException, BadPaddingException {
		Cipher cipher = Cipher.getInstance("RSA/ECB/NoPadding");
		cipher.init(Cipher.DECRYPT_MODE, privateKey);
		return cipher.doFinal(input);
	}

	public byte[] sign(byte[] input) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
		Signature sig = Signature.getInstance("SHA1WithRSA");
		sig.initSign(privateKey);
		sig.update(input);
		return sig.sign();
	}

	public boolean verify(byte[] input, byte[] signature)
			throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
		Signature sig = Signature.getInstance("SHA1WithRSA");
		sig.initVerify(publicKey);
		sig.update(input);
		return sig.verify(signature);
	}

	public String encrypt(String str) {
		if (str == null)
			return null;
		try {
			return new String(
					Base64.getEncoder().withoutPadding().encode(encrypt(str.getBytes(StandardCharsets.UTF_8))),
					StandardCharsets.UTF_8);
		} catch (Exception ex) {
			log.error("encrypt exception!", ex);
			return "";
		}
	}

	public String decrypt(String str) {
		if (str == null)
			return null;
		try {
			return new String(decrypt(Base64.getDecoder().decode(str.getBytes(StandardCharsets.UTF_8))),
					StandardCharsets.UTF_8);
		} catch (Exception ex) {
			log.error("decrypt exception!", ex);
			return "";
		}
	}

	public String sign(String str) {
		if (str == null)
			return null;
		try {
			return new String(Base64.getEncoder().withoutPadding().encode(sign(str.getBytes(StandardCharsets.UTF_8))),
					StandardCharsets.UTF_8);
		} catch (Exception ex) {
			log.error("encrypt exception!", ex);
			return "";
		}
	}

	public boolean verify(String str, String signature) {
		if (str == null)
			return false;
		try {
			return verify(str.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
		} catch (Exception ex) {
			log.error("encrypt exception!", ex);
			return false;
		}
	}

	public static RSA getDefaultInstance() {
		SoftReference<RSA> instanceRef = pool.get();
		RSA instance;
		if (instanceRef == null || (instance = instanceRef.get()) == null) {
			try {
				instance = new RSA();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			instanceRef = new SoftReference<>(instance);
			pool.set(instanceRef);
		}
		return instance;
	}

}
