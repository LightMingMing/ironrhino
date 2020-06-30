package org.ironrhino.security.oauth.server.controller;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.ironrhino.core.event.EventPublisher;
import org.ironrhino.core.metadata.Scope;
import org.ironrhino.core.security.jwt.Jwt;
import org.ironrhino.core.security.verfication.VerificationManager;
import org.ironrhino.core.struts.I18N;
import org.ironrhino.core.util.ExceptionUtils;
import org.ironrhino.security.oauth.server.component.OAuthHandler;
import org.ironrhino.security.oauth.server.domain.OAuthError;
import org.ironrhino.security.oauth.server.enums.GrantType;
import org.ironrhino.security.oauth.server.event.AuthorizeEvent;
import org.ironrhino.security.oauth.server.model.Authorization;
import org.ironrhino.security.oauth.server.model.Client;
import org.ironrhino.security.oauth.server.service.OAuthManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@Validated
@RequestMapping("/oauth2")
public class OAuth2Controller {

	@Autowired
	private EventPublisher eventPublisher;

	@Autowired
	private OAuthManager oauthManager;

	@Autowired
	private OAuthHandler oauthHandler;

	@Autowired
	private UserDetailsService userDetailsService;

	@Autowired
	private AuthenticationFailureHandler authenticationFailureHandler;

	@Autowired
	private AuthenticationSuccessHandler authenticationSuccessHandler;

	@Autowired
	private AuthenticationManager authenticationManager;

	@Autowired
	private WebAuthenticationDetailsSource authenticationDetailsSource;

	@Autowired(required = false)
	private VerificationManager verificationManager;

	@RequestMapping(value = "/token", method = { RequestMethod.GET, RequestMethod.POST })
	public Map<String, Object> token(HttpServletRequest request, HttpServletResponse response,
			@RequestParam GrantType grant_type, @RequestParam String client_id, @RequestParam String client_secret,
			@RequestParam(required = false) String username, @RequestParam(required = false) String password,
			@RequestParam(required = false) String device_id, @RequestParam(required = false) String device_name,
			@RequestParam(required = false) String code, @RequestParam(required = false) String redirect_uri,
			@RequestParam(required = false) String refresh_token) throws Exception {
		Client client;
		Authorization authorization;
		Map<String, Object> result = new LinkedHashMap<>();
		if (grant_type == GrantType.password
				|| grant_type == GrantType.jwt_bearer && oauthHandler != null && oauthHandler.isJwtEnabled()) {
			client = oauthManager.findClientById(client_id);
			if (client == null)
				throw new OAuthError(OAuthError.INVALID_CLIENT, OAuthError.ERROR_CLIENT_ID_NOT_EXISTS);
			if (!client.getSecret().equals(client_secret))
				throw new OAuthError(OAuthError.INVALID_CLIENT, OAuthError.ERROR_CLIENT_SECRET_MISMATCH);
			if (username == null)
				throw new MissingServletRequestParameterException("username", String.class.getSimpleName());
			if (password == null)
				throw new MissingServletRequestParameterException("password", String.class.getSimpleName());
			try {
				UsernamePasswordAuthenticationToken attempt = new UsernamePasswordAuthenticationToken(username,
						password);
				attempt.setDetails(authenticationDetailsSource.buildDetails(request));
				try {
					Authentication authResult = authenticationManager.authenticate(attempt);
					if (authResult != null)
						authenticationSuccessHandler.onAuthenticationSuccess(request, response, authResult);
				} catch (InternalAuthenticationServiceException failed) {
					throw new IllegalArgumentException(ExceptionUtils.getRootMessage(failed));
				} catch (AuthenticationException failed) {
					authenticationFailureHandler.onAuthenticationFailure(request, response, failed);
					throw new IllegalArgumentException(I18N.getText(failed.getClass().getName()), failed);
				}
				UserDetails ud = userDetailsService.loadUserByUsername(username);
				if (grant_type == GrantType.jwt_bearer) {
					int expiresIn = oauthHandler.getJwtExpiresIn();
					String jwt = Jwt.createWithSubject(ud.getUsername(), ud.getPassword(), expiresIn);
					result.put("access_token", jwt);
					if (expiresIn > 0)
						result.put("expires_in", expiresIn);
				} else {
					authorization = oauthManager.grant(client, ud.getUsername(), device_id, device_name);
					result.put("access_token", authorization.getAccessToken());
					result.put("refresh_token", authorization.getRefreshToken());
					result.put("expires_in", authorization.getExpiresIn());
				}
				eventPublisher.publish(new AuthorizeEvent(ud.getUsername(), request.getRemoteAddr(), client.getName(),
						grant_type.name()), Scope.LOCAL);
			} catch (Exception e) {
				if (e.getCause() instanceof AuthenticationException)
					log.error("Exchange token by password for \"{}\" failed with {}: {}", username,
							e.getClass().getName(), e.getLocalizedMessage());
				else
					log.error(e.getMessage(), e);
				throw new OAuthError(OAuthError.INVALID_REQUEST, e.getLocalizedMessage());
			}
		} else if (grant_type == GrantType.client_credentials) {
			client = new Client();
			client.setId(client_id);
			client.setSecret(client_secret);
			try {
				authorization = oauthManager.grant(client, device_id, device_name);
			} catch (Exception e) {
				log.error("Exchange token by client_credentials for \"{}\" failed with {}: {}", client_id,
						e.getClass().getName(), e.getLocalizedMessage());
				throw new OAuthError(OAuthError.INVALID_REQUEST, e.getLocalizedMessage());
			}
			result.put("access_token", authorization.getAccessToken());
			result.put("refresh_token", authorization.getRefreshToken());
			result.put("expires_in", authorization.getExpiresIn());
		} else if (grant_type == GrantType.refresh_token) {
			if (refresh_token == null)
				throw new MissingServletRequestParameterException("refresh_token", String.class.getSimpleName());
			client = new Client();
			client.setId(client_id);
			client.setSecret(client_secret);
			try {
				authorization = oauthManager.refresh(client, refresh_token);
				result.put("access_token", authorization.getAccessToken());
				result.put("expires_in", authorization.getExpiresIn());
				result.put("refresh_token", authorization.getRefreshToken());
			} catch (Exception e) {
				log.error("Refresh token \"{}\" failed with {}: {}", refresh_token, e.getClass().getName(),
						e.getLocalizedMessage());
				throw new OAuthError(OAuthError.INVALID_REQUEST, e.getLocalizedMessage());
			}
		} else if (grant_type == GrantType.authorization_code) {
			if (code == null)
				throw new MissingServletRequestParameterException("code", String.class.getSimpleName());
			client = new Client();
			client.setId(client_id);
			client.setSecret(client_secret);
			client.setRedirectUri(redirect_uri);
			try {
				authorization = oauthManager.authenticate(code, client);
				result.put("access_token", authorization.getAccessToken());
				result.put("expires_in", authorization.getExpiresIn());
				result.put("refresh_token", authorization.getRefreshToken());
				eventPublisher.publish(new AuthorizeEvent(authorization.getGrantor(), request.getRemoteAddr(),
						client.getName(), grant_type.name()), Scope.LOCAL);
			} catch (Exception e) {
				log.error("Exchange token by code for \"{}\" failed with {}: {}", code, e.getClass().getName(),
						e.getLocalizedMessage());
				throw new OAuthError(OAuthError.INVALID_REQUEST, e.getLocalizedMessage());
			}
		} else {
			throw new OAuthError(OAuthError.UNSUPPORTED_GRANT_TYPE);
		}
		return result;
	}

	@GetMapping(value = "/info")
	public Map<String, Object> info(@RequestParam String access_token) throws IOException {
		Map<String, Object> result = new LinkedHashMap<>();
		Authorization authorization = oauthManager.retrieve(access_token);
		if (authorization == null) {
			throw new OAuthError(OAuthError.INVALID_TOKEN);
		} else if (authorization.getExpiresIn() < 0) {
			throw new OAuthError(OAuthError.INVALID_TOKEN, OAuthError.ERROR_EXPIRED_TOKEN);
		} else {
			if (authorization.getClient() != null)
				result.put("client_id", authorization.getClient());
			if (authorization.getGrantor() != null)
				result.put("username", authorization.getGrantor());
			result.put("expires_in", authorization.getExpiresIn());
			if (authorization.getScope() != null)
				result.put("scope", authorization.getScope());
		}
		return result;
	}

	@RequestMapping(value = "/revoke", method = { RequestMethod.GET, RequestMethod.POST })
	public void revoke(@RequestParam String access_token) throws IOException {
		boolean revoked = oauthManager.revoke(access_token);
		if (!revoked) {
			throw new OAuthError(OAuthError.INVALID_REQUEST, OAuthError.ERROR_REVOKE_FAILED);
		}
	}

	@RequestMapping(value = "/sendVerificationCode", method = { RequestMethod.GET, RequestMethod.POST })
	public Map<String, Object> sendVerificationCode(@RequestParam String client_id, @RequestParam String client_secret,
			@RequestParam String username) throws IOException {
		Map<String, Object> result = null;
		if (verificationManager == null) {
			result = new LinkedHashMap<>();
			result.put("code", "2");
			result.put("status", "FORBIDDEN");
			return result;
		}
		try {
			Client client = oauthManager.findClientById(client_id);
			if (client == null)
				throw new OAuthError(OAuthError.INVALID_CLIENT, OAuthError.ERROR_CLIENT_ID_NOT_EXISTS);
			if (!client.getSecret().equals(client_secret))
				throw new OAuthError(OAuthError.INVALID_CLIENT, OAuthError.ERROR_CLIENT_SECRET_MISMATCH);
			if (StringUtils.isNotBlank(username)) {
				verificationManager.send(username);
				result = new LinkedHashMap<>();
				result.put("code", "0");
				result.put("status", "OK");
			}
		} catch (Exception e) {
			log.error("Send verification code to \"{}\" failed with {}: {}", username, e.getClass().getName(),
					e.getLocalizedMessage());
			throw new OAuthError(OAuthError.INVALID_REQUEST, e.getLocalizedMessage());
		}
		return result;
	}

}
