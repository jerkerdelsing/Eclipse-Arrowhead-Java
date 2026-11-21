package eu.arrowhead.localcloudconfigurator.tls;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXRevocationChecker;
import java.security.cert.X509CRL;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.net.ssl.CertPathTrustManagerParameters;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.security.auth.x500.X500Principal;

/**
 * Mutual TLS between the LocalCloudConfigurator API and its host worker. Both sides present an Arrowhead system certificate of the
 * cloud CA (PKCS12), validate the peer chain against the cloud truststore (with the cloud CRL when configured) and accept only peers
 * whose certificate names an expected system ("&lt;System&gt;.&lt;cloud&gt;.&lt;organization&gt;.arrowhead.eu").
 */
public final class MutualTls {

	private MutualTls() {
		throw new UnsupportedOperationException();
	}

	public static SSLContext context(final InputStream keyStore, final char[] keyStorePassword, final char[] keyPassword, final InputStream trustStore,
			final char[] trustStorePassword, final Path crlFile, final Set<String> acceptedPeerSystems) throws IOException, GeneralSecurityException {
		if (acceptedPeerSystems == null || acceptedPeerSystems.isEmpty()) {
			throw new GeneralSecurityException("No accepted peer system configured");
		}

		final KeyStore keys = KeyStore.getInstance("PKCS12");
		keys.load(keyStore, keyStorePassword);
		final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		kmf.init(keys, keyPassword);

		final KeyStore trust = KeyStore.getInstance("PKCS12");
		trust.load(trustStore, trustStorePassword);

		final SSLContext context = SSLContext.getInstance("TLS");
		context.init(kmf.getKeyManagers(), new X509ExtendedTrustManager[] { trustManager(trust, crlFile, acceptedPeerSystems) }, null);
		return context;
	}

	/**
	 * The peer check: PKIX against the truststore, with the cloud CRL when configured, then the accepted system names. The CRL file is
	 * re-read whenever it changes, so a reissued CRL (a new nextUpdate or new revocations) applies to later handshakes without a restart.
	 */
	static X509ExtendedTrustManager trustManager(final KeyStore trust, final Path crlFile, final Set<String> acceptedPeerSystems) throws IOException, GeneralSecurityException {
		final X509ExtendedTrustManager pkix = crlFile == null ? pkix(trust, null) : new CrlReloadingTrustManager(trust, crlFile);
		return new PeerNameTrustManager(pkix, acceptedPeerSystems);
	}

	private static X509ExtendedTrustManager pkix(final KeyStore trust, final X509CRL crl) throws GeneralSecurityException {
		final PKIXBuilderParameters parameters = new PKIXBuilderParameters(trust, new X509CertSelector());
		if (crl != null) {
			parameters.addCertStore(CertStore.getInstance("Collection", new CollectionCertStoreParameters(List.of(crl))));
			final PKIXRevocationChecker checker = (PKIXRevocationChecker) CertPathBuilder.getInstance("PKIX").getRevocationChecker();
			// the CRL file of the cloud CA only; no online lookups
			checker.setOptions(EnumSet.of(PKIXRevocationChecker.Option.PREFER_CRLS, PKIXRevocationChecker.Option.NO_FALLBACK));
			parameters.addCertPathChecker(checker);
			parameters.setRevocationEnabled(true);
		} else {
			parameters.setRevocationEnabled(false);
		}

		final TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
		tmf.init(new CertPathTrustManagerParameters(parameters));
		return (X509ExtendedTrustManager) tmf.getTrustManagers()[0];
	}

	private static X509CRL readCrl(final Path crlFile) throws IOException, GeneralSecurityException {
		try (InputStream in = Files.newInputStream(crlFile)) {
			return (X509CRL) CertificateFactory.getInstance("X.509").generateCRL(in);
		}
	}

	/** PKIX with the CRL file, rebuilt when the file's modification time or size changes; an unreadable new file keeps the previous CRL. */
	private static final class CrlReloadingTrustManager extends X509ExtendedTrustManager {

		private final KeyStore trust;
		private final Path crlFile;
		private volatile String version;
		private volatile X509ExtendedTrustManager current;

		private CrlReloadingTrustManager(final KeyStore trust, final Path crlFile) throws IOException, GeneralSecurityException {
			this.trust = trust;
			this.crlFile = crlFile;
			this.version = version(crlFile);
			this.current = pkix(trust, readCrl(crlFile));
		}

		private static String version(final Path file) throws IOException {
			return Files.getLastModifiedTime(file).toMillis() + ":" + Files.size(file);
		}

		private X509ExtendedTrustManager current() {
			try {
				final String now = version(crlFile);
				if (!now.equals(version)) {
					synchronized (this) {
						if (!now.equals(version)) {
							current = pkix(trust, readCrl(crlFile));
							version = now;
						}
					}
				}
			} catch (final IOException | GeneralSecurityException | RuntimeException ex) {
				// keep the previous CRL; a CRL past its nextUpdate still fails the PKIX check
			}
			return current;
		}

		@Override
		public void checkClientTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
			current().checkClientTrusted(chain, authType);
		}

		@Override
		public void checkServerTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
			current().checkServerTrusted(chain, authType);
		}

		@Override
		public void checkClientTrusted(final X509Certificate[] chain, final String authType, final Socket socket) throws CertificateException {
			current().checkClientTrusted(chain, authType, socket);
		}

		@Override
		public void checkServerTrusted(final X509Certificate[] chain, final String authType, final Socket socket) throws CertificateException {
			current().checkServerTrusted(chain, authType, socket);
		}

		@Override
		public void checkClientTrusted(final X509Certificate[] chain, final String authType, final SSLEngine engine) throws CertificateException {
			current().checkClientTrusted(chain, authType, engine);
		}

		@Override
		public void checkServerTrusted(final X509Certificate[] chain, final String authType, final SSLEngine engine) throws CertificateException {
			current().checkServerTrusted(chain, authType, engine);
		}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return current().getAcceptedIssuers();
		}
	}

	/** The system name of an Arrowhead certificate: the first label of its CN, or null. */
	public static String systemName(final X509Certificate certificate) {
		try {
			for (final Rdn rdn : new LdapName(certificate.getSubjectX500Principal().getName(X500Principal.RFC2253)).getRdns()) {
				if ("CN".equalsIgnoreCase(rdn.getType())) {
					final String cn = rdn.getValue().toString();
					final int dot = cn.indexOf('.');
					return dot < 0 ? cn : cn.substring(0, dot);
				}
			}
		} catch (final InvalidNameException ex) {
			return null;
		}

		return null;
	}

	static void checkPeer(final X509Certificate[] chain, final Set<String> accepted) throws CertificateException {
		if (chain == null || chain.length == 0) {
			throw new CertificateException("No peer certificate");
		}

		final String name = systemName(chain[0]);
		if (name == null || accepted.stream().noneMatch(a -> a.toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT)))) {
			throw new CertificateException("Peer certificate does not belong to an accepted system: " + name);
		}
	}

	private static final class PeerNameTrustManager extends X509ExtendedTrustManager {

		private final X509ExtendedTrustManager delegate;
		private final Set<String> accepted;

		private PeerNameTrustManager(final X509ExtendedTrustManager delegate, final Set<String> accepted) {
			this.delegate = delegate;
			this.accepted = Set.copyOf(accepted);
		}

		@Override
		public void checkClientTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
			delegate.checkClientTrusted(chain, authType);
			checkPeer(chain, accepted);
		}

		@Override
		public void checkServerTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
			delegate.checkServerTrusted(chain, authType);
			checkPeer(chain, accepted);
		}

		@Override
		public void checkClientTrusted(final X509Certificate[] chain, final String authType, final Socket socket) throws CertificateException {
			delegate.checkClientTrusted(chain, authType, socket);
			checkPeer(chain, accepted);
		}

		@Override
		public void checkServerTrusted(final X509Certificate[] chain, final String authType, final Socket socket) throws CertificateException {
			delegate.checkServerTrusted(chain, authType, socket);
			checkPeer(chain, accepted);
		}

		@Override
		public void checkClientTrusted(final X509Certificate[] chain, final String authType, final SSLEngine engine) throws CertificateException {
			delegate.checkClientTrusted(chain, authType, engine);
			checkPeer(chain, accepted);
		}

		@Override
		public void checkServerTrusted(final X509Certificate[] chain, final String authType, final SSLEngine engine) throws CertificateException {
			delegate.checkServerTrusted(chain, authType, engine);
			checkPeer(chain, accepted);
		}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return delegate.getAcceptedIssuers();
		}
	}
}
