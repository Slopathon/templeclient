package nz.temple.security;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.KeyAgreement;

public final class XEcdh {
	private XEcdh() {
	}
	public static KeyPair generate() {
		try {
			return KeyPairGenerator.getInstance("X25519").generateKeyPair();
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("X25519 unavailable", e);
		}
	}
	public static byte[] publicSpki(KeyPair pair) {
		return pair.getPublic().getEncoded();
	}
	public static byte[] sharedFromSpki(PrivateKey ourPrivate, byte[] peerPublicSpki) throws GeneralSecurityException {
		PublicKey peer = KeyFactory.getInstance("X25519").generatePublic(new X509EncodedKeySpec(peerPublicSpki));
		KeyAgreement ka = KeyAgreement.getInstance("X25519");
		ka.init(ourPrivate);
		ka.doPhase(peer, true);
		return ka.generateSecret();
	}
	public static PrivateKey privateFromPkcs8(byte[] pkcs8) throws GeneralSecurityException {
		return KeyFactory.getInstance("X25519").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
	}
}
