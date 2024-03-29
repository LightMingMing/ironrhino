package org.ironrhino.core.security.webauthn;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import java.util.Arrays;

import org.ironrhino.core.cache.CacheManager;
import org.ironrhino.core.cache.impl.Cache2kCacheManager;
import org.ironrhino.core.security.webauthn.domain.AuthenticatorAssertionResponse;
import org.ironrhino.core.security.webauthn.domain.AuthenticatorAttestationResponse;
import org.ironrhino.core.security.webauthn.domain.PublicKeyCredential;
import org.ironrhino.core.security.webauthn.impl.DefaultStoredCredentialService;
import org.ironrhino.core.security.webauthn.impl.DefaultWebAuthnService;
import org.ironrhino.core.security.webauthn.internal.Utils;
import org.ironrhino.core.service.HibernateConfiguration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.core.type.TypeReference;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = { VerificationTest.Config.class, HibernateConfiguration.class })
@TestPropertySource(properties = { "annotatedClasses=org.ironrhino.core.security.webauthn.model.WebAuthnCredential",
		"hibernate.show_sql=true" })
public class VerificationTest {

	@Autowired
	private DefaultWebAuthnService webAuthnService;

	@Autowired
	private StoredCredentialService storedCredentialService;

	@Test
	public void test() throws Exception {

		PublicKeyCredential<AuthenticatorAttestationResponse> attestationCredential = Utils.JSON_OBJECTMAPPER.readValue(
				DeserializationTest.ATTESTATION_JSON,
				new TypeReference<PublicKeyCredential<AuthenticatorAttestationResponse>>() {
				});
		PublicKeyCredential<AuthenticatorAssertionResponse> assertionCredential = Utils.JSON_OBJECTMAPPER.readValue(
				DeserializationTest.ASSERTION_JSON,
				new TypeReference<PublicKeyCredential<AuthenticatorAssertionResponse>>() {
				});

		given(webAuthnService.getChallenge("admin"))
				.willReturn(attestationCredential.getResponse().getClientData().getChallenge());
		webAuthnService.verifyAttestation(attestationCredential, "admin");
		then(storedCredentialService).should(times(1))
				.addCredential(argThat(c -> Arrays.equals(c.getCredentialId(), attestationCredential.getId())));
		then(storedCredentialService).should(times(1)).getCredentialById(attestationCredential.getId());

		given(webAuthnService.getChallenge("admin"))
				.willReturn(assertionCredential.getResponse().getClientData().getChallenge());
		webAuthnService.verifyAssertion(assertionCredential, "admin");
		then(storedCredentialService).should(times(2)).getCredentialById(assertionCredential.getId());
		then(storedCredentialService).should(times(1)).updateSignCount(assertionCredential.getId(),
				assertionCredential.getResponse().getAuthenticatorData().getSignCount());
	}

	@Configuration
	static class Config {

		@Bean
		public DefaultWebAuthnService webAuthnService() {
			return Mockito.spy(new DefaultWebAuthnService());
		}

		@Bean
		public CacheManager cacheManager() {
			return new Cache2kCacheManager();
		}

		@Bean
		public StoredCredentialService storedCredentialService() {
			return Mockito.spy(new DefaultStoredCredentialService());
		}

	}

}
