package nz.temple.security;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class XEcdhTest {
	private static final String SRV_PUB_SPKI_B64 = "MCowBQYDK2VuAyEA/IgXGj3VsNO7xGrEynYlWl2Jpx5UOv6DC2mH+Yz2LXc=";
	private static final String CLI_PRIV_PKCS8_B64 = "MC4CAQAwBQYDK2VuBCIEIOj34SZ22BMgHypDcALj9yFssgUwqCfdG9P9JZN+0757";
	private static final String SHARED_HEX = "4d0ff8057812223c0ffcca5c6b5fe98470430a8b8313e9bdc03c2872b0afd47c";

	@Test
	void matches_a_secret_computed_by_the_bots_python() throws Exception {
		PrivateKey priv = XEcdh.privateFromPkcs8(Base64.getDecoder().decode(CLI_PRIV_PKCS8_B64));
		byte[] shared = XEcdh.sharedFromSpki(priv, Base64.getDecoder().decode(SRV_PUB_SPKI_B64));
		assertEquals(SHARED_HEX, HexFormat.of().formatHex(shared));
	}

	@Test
	void both_sides_derive_the_same_secret() throws Exception {
		KeyPair a = XEcdh.generate();
		KeyPair b = XEcdh.generate();
		byte[] ab = XEcdh.sharedFromSpki(a.getPrivate(), XEcdh.publicSpki(b));
		byte[] ba = XEcdh.sharedFromSpki(b.getPrivate(), XEcdh.publicSpki(a));
		assertArrayEquals(ab, ba);
		assertEquals(32, ab.length);
	}
}
