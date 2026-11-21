package eu.arrowhead.localcloudconfigurator.tls;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Set;

import javax.net.ssl.X509ExtendedTrustManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// A long-running worker must follow a reissued CRL: before, the CRL was read once at startup, so once its nextUpdate passed every
// client was refused (certificate_unknown) until a restart, and new revocations were never seen.
public class MutualTlsCrlReloadTest {

	@TempDir
	Path temp;

	@Test
	public void reissuedCrlAppliesWithoutRestart() throws Exception {
		final TestPki pki = new TestPki("reload");
		final X509Certificate client = pki.issue("LocalCloudConfigurator").certificate();
		final KeyStore trust = KeyStore.getInstance("PKCS12");
		trust.load(new ByteArrayInputStream(pki.trustStore()), TestPki.PASSWORD);
		final Path crl = pki.crl(temp.resolve("ca.crl"));

		final X509ExtendedTrustManager tm = MutualTls.trustManager(trust, crl, Set.of("LocalCloudConfigurator"));
		assertDoesNotThrow(() -> tm.checkClientTrusted(new X509Certificate[] { client }, "UNKNOWN"));

		pki.crl(crl, client);
		Files.setLastModifiedTime(crl, FileTime.fromMillis(System.currentTimeMillis() + 5_000));
		assertThrows(CertificateException.class, () -> tm.checkClientTrusted(new X509Certificate[] { client }, "UNKNOWN"));

		pki.crl(crl);
		Files.setLastModifiedTime(crl, FileTime.fromMillis(System.currentTimeMillis() + 10_000));
		assertDoesNotThrow(() -> tm.checkClientTrusted(new X509Certificate[] { client }, "UNKNOWN"));
	}

	@Test
	public void unreadableNewCrlKeepsThePreviousOne() throws Exception {
		final TestPki pki = new TestPki("keep");
		final X509Certificate client = pki.issue("LocalCloudConfigurator").certificate();
		final KeyStore trust = KeyStore.getInstance("PKCS12");
		trust.load(new ByteArrayInputStream(pki.trustStore()), TestPki.PASSWORD);
		final Path crl = pki.crl(temp.resolve("ca.crl"));
		final X509ExtendedTrustManager tm = MutualTls.trustManager(trust, crl, Set.of("LocalCloudConfigurator"));

		Files.write(crl, new byte[] { 1, 2, 3 });
		assertDoesNotThrow(() -> tm.checkClientTrusted(new X509Certificate[] { client }, "UNKNOWN"));
	}
}
