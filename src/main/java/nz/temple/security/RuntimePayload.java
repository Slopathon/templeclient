package nz.temple.security;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class RuntimePayload {
	private static final int NONCE_BYTES = 12;
	private static final int TAG_BITS = 128;
	private static final SecureRandom RANDOM = new SecureRandom();
	private RuntimePayload() {
	}
	public static ClassLoader load(byte[] encrypted, byte[] payloadKey, String associatedData, ClassLoader parent)
			throws GeneralSecurityException {
		byte[] jar = decrypt(encrypted, payloadKey, associatedData);
		try {
			return new InMemoryLoader(readClasses(jar), parent);
		} finally {

			Arrays.fill(jar, (byte) 0);
		}
	}
	public static byte[] encrypt(byte[] payloadJar, byte[] payloadKey, String associatedData) throws GeneralSecurityException {
		byte[] nonce = new byte[NONCE_BYTES];
		RANDOM.nextBytes(nonce);
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(Cipher.ENCRYPT_MODE, keyFor(payloadKey), new GCMParameterSpec(TAG_BITS, nonce));
		cipher.updateAAD(aad(associatedData));
		byte[] body = cipher.doFinal(payloadJar);
		byte[] blob = new byte[nonce.length + body.length];
		System.arraycopy(nonce, 0, blob, 0, nonce.length);
		System.arraycopy(body, 0, blob, nonce.length, body.length);
		return blob;
	}
	static byte[] decrypt(byte[] blob, byte[] payloadKey, String associatedData) throws GeneralSecurityException {
		if (blob == null || blob.length <= NONCE_BYTES) {
			throw new GeneralSecurityException("payload too short");
		}
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(Cipher.DECRYPT_MODE, keyFor(payloadKey),
				new GCMParameterSpec(TAG_BITS, blob, 0, NONCE_BYTES));
		cipher.updateAAD(aad(associatedData));
		return cipher.doFinal(blob, NONCE_BYTES, blob.length - NONCE_BYTES);
	}
	private static SecretKeySpec keyFor(byte[] payloadKey) throws GeneralSecurityException {
		if (payloadKey == null || payloadKey.length != 32) {
			throw new GeneralSecurityException("payload key must be 32 bytes");
		}
		return new SecretKeySpec(payloadKey.clone(), "AES");
	}
	private static byte[] aad(String associatedData) {
		return (associatedData == null ? "" : associatedData).getBytes(java.nio.charset.StandardCharsets.UTF_8);
	}
	static Map<String, byte[]> readClasses(byte[] jarBytes) throws GeneralSecurityException {
		Map<String, byte[]> classes = new HashMap<>();
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(jarBytes))) {
			ZipEntry entry;
			byte[] buffer = new byte[8192];
			while ((entry = zip.getNextEntry()) != null) {
				String name = entry.getName();
				if (entry.isDirectory() || !name.endsWith(".class")) {
					continue;
				}
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				int read;
				while ((read = zip.read(buffer)) != -1) {
					out.write(buffer, 0, read);
				}
				String binaryName = name.substring(0, name.length() - ".class".length()).replace('/', '.');
				classes.put(binaryName, out.toByteArray());
			}
		} catch (Exception e) {
			throw new GeneralSecurityException("payload is not a readable archive", e);
		}
		return classes;
	}
	static byte[] packClasses(Map<String, byte[]> classes) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(out)) {
			for (Map.Entry<String, byte[]> e : classes.entrySet()) {
				zip.putNextEntry(new ZipEntry(e.getKey().replace('.', '/') + ".class"));
				zip.write(e.getValue());
				zip.closeEntry();
			}
		} catch (Exception e) {
			throw new IllegalStateException("couldn't pack payload", e);
		}
		return out.toByteArray();
	}

	static final class InMemoryLoader extends ClassLoader {
		private final Map<String, byte[]> classes;
		InMemoryLoader(Map<String, byte[]> classes, ClassLoader parent) {
			super(parent);
			this.classes = classes;
		}
		@Override
		protected Class<?> findClass(String name) throws ClassNotFoundException {
			byte[] bytecode = classes.remove(name);
			if (bytecode == null) {
				throw new ClassNotFoundException(name);
			}
			try {
				return defineClass(name, bytecode, 0, bytecode.length);
			} finally {
				Arrays.fill(bytecode, (byte) 0);
			}
		}
		@Override
		protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			synchronized (getClassLoadingLock(name)) {
				Class<?> loaded = findLoadedClass(name);
				if (loaded == null && classes.containsKey(name)) {
					loaded = findClass(name);
				}
				if (loaded == null) {
					loaded = super.loadClass(name, false);
				}
				if (resolve) {
					resolveClass(loaded);
				}
				return loaded;
			}
		}
	}
}
