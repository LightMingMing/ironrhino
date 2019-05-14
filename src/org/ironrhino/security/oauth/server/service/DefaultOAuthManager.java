package org.ironrhino.security.oauth.server.service;

import static org.ironrhino.core.metadata.Profiles.CLOUD;
import static org.ironrhino.core.metadata.Profiles.DEFAULT;
import static org.ironrhino.core.metadata.Profiles.DUAL;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.ironrhino.core.metadata.Trigger;
import org.ironrhino.core.servlet.RequestContext;
import org.ironrhino.core.spring.configuration.ServiceImplementationConditional;
import org.ironrhino.core.throttle.Mutex;
import org.ironrhino.core.util.CodecUtils;
import org.ironrhino.security.oauth.server.domain.OAuthError;
import org.ironrhino.security.oauth.server.enums.GrantType;
import org.ironrhino.security.oauth.server.enums.ResponseType;
import org.ironrhino.security.oauth.server.model.Authorization;
import org.ironrhino.security.oauth.server.model.Client;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component("oauthManager")
@ServiceImplementationConditional(profiles = { DEFAULT, DUAL, CLOUD })
public class DefaultOAuthManager extends AbstractOAuthManager {

	@Autowired
	private ClientManager clientManager;

	@Autowired
	private AuthorizationManager authorizationManager;

	@Override
	public Authorization grant(Client client, String deviceId, String deviceName) {
		Client orig = findClientById(client.getClientId());
		if (orig == null)
			throw new OAuthError(OAuthError.UNAUTHORIZED_CLIENT, "client_id_not_exists");
		if (!orig.getSecret().equals(client.getSecret()))
			throw new OAuthError(OAuthError.UNAUTHORIZED_CLIENT, "client_secret_mismatch");
		Authorization auth = new Authorization();
		if (authorizationLifetime > 0)
			auth.setLifetime(authorizationLifetime);
		auth.setClient(client.getId());
		auth.setResponseType(ResponseType.token);
		auth.setGrantType(GrantType.client_credentials);
		if (StringUtils.isNotBlank(deviceId)) {
			auth.setDeviceId(deviceId);
			auth.setDeviceName(deviceName);
		}
		try {
			auth.setAddress(RequestContext.getRequest().getRemoteAddr());
		} catch (NullPointerException npe) {
		}
		auth.setRefreshToken(CodecUtils.nextId(32));
		authorizationManager.save(auth);
		return auth;
	}

	@Override
	protected Authorization doGrant(Client client, String grantor, String deviceId, String deviceName) {
		if (StringUtils.isNotBlank(deviceId)) {
			DetachedCriteria dc = authorizationManager.detachedCriteria();
			dc.add(Restrictions.eq("client", client.getClientId()));
			dc.add(Restrictions.eq("grantor", grantor));
			dc.add(Restrictions.eq("deviceId", deviceId));
			Authorization auth = authorizationManager.findByCriteria(dc);
			if (auth != null) {
				auth.setAccessToken(CodecUtils.nextId(32));
				auth.setRefreshToken(CodecUtils.nextId(32));
				auth.setModifyDate(new Date());
				authorizationManager.save(auth);
				return auth;
			} else {
				dc = authorizationManager.detachedCriteria();
				dc.add(Restrictions.eq("client", client.getClientId()));
				dc.add(Restrictions.eq("grantor", grantor));
				dc.add(Restrictions.isNotNull("deviceId"));
				if (maximumDevices > 0 && authorizationManager.countByCriteria(dc) >= maximumDevices)
					throw new IllegalArgumentException("maximum_devices_reached");
			}
		}
		Authorization auth = new Authorization();
		if (authorizationLifetime > 0)
			auth.setLifetime(authorizationLifetime);
		auth.setClient(client.getId());
		auth.setGrantor(grantor);
		auth.setRefreshToken(CodecUtils.nextId());
		auth.setResponseType(ResponseType.token);
		auth.setGrantType(GrantType.password);
		if (StringUtils.isNotBlank(deviceId)) {
			auth.setDeviceId(deviceId);
			auth.setDeviceName(deviceName);
		}
		try {
			auth.setAddress(RequestContext.getRequest().getRemoteAddr());
		} catch (NullPointerException npe) {
		}
		authorizationManager.save(auth);
		return auth;
	}

	@Override
	public Authorization generate(Client client, String redirectUri, String scope, ResponseType responseType) {
		if (!client.supportsRedirectUri(redirectUri))
			throw new OAuthError(OAuthError.INVALID_GRANT, "redirect_uri_mismatch");
		Authorization auth = new Authorization();
		if (authorizationLifetime > 0)
			auth.setLifetime(authorizationLifetime);
		auth.setClient(client.getId());
		if (StringUtils.isNotBlank(scope))
			auth.setScope(scope);
		if (responseType != null)
			auth.setResponseType(responseType);
		authorizationManager.save(auth);
		return auth;
	}

	@Override
	public Authorization reuse(Authorization auth) {
		auth.setCode(CodecUtils.nextId(32));
		auth.setModifyDate(new Date());
		auth.setLifetime(Authorization.DEFAULT_LIFETIME);
		authorizationManager.save(auth);
		return auth;
	}

	@Override
	public Authorization grant(String authorizationId, String grantor) {
		Authorization auth = authorizationManager.get(authorizationId);
		if (auth == null)
			throw new OAuthError(OAuthError.INVALID_GRANT, "bad_auth");
		auth.setGrantor(grantor);
		try {
			auth.setAddress(RequestContext.getRequest().getRemoteAddr());
		} catch (NullPointerException npe) {
		}
		auth.setModifyDate(new Date());
		if (!auth.isClientSide())
			auth.setCode(CodecUtils.nextId(32));
		authorizationManager.save(auth);
		return auth;
	}

	@Override
	public void deny(String authorizationId) {
		Authorization auth = authorizationManager.get(authorizationId);
		if (auth != null)
			authorizationManager.delete(auth);
	}

	@Override
	public Authorization authenticate(String code, Client client) {
		Authorization auth = authorizationManager.findOne("code", code);
		if (auth == null)
			throw new OAuthError(OAuthError.INVALID_GRANT, "code_invalid");
		if (auth.isClientSide())
			throw new OAuthError(OAuthError.INVALID_GRANT, "not_server_side");
		if (auth.getGrantor() == null)
			throw new OAuthError(OAuthError.INVALID_GRANT, "user_not_granted");
		Client orig = findClientById(auth.getClient());
		if (!orig.getId().equals(client.getId()))
			throw new OAuthError(OAuthError.UNAUTHORIZED_CLIENT, "client_id_mismatch");
		if (!orig.getSecret().equals(client.getSecret()))
			throw new OAuthError(OAuthError.UNAUTHORIZED_CLIENT, "client_secret_mismatch");
		if (!orig.supportsRedirectUri(client.getRedirectUri()))
			throw new OAuthError(OAuthError.INVALID_GRANT, "redirect_uri_mismatch");
		if (exclusive)
			deleteAuthorizationsByGrantor(auth.getGrantor(), client.getId(), GrantType.authorization_code);
		auth.setCode(null);
		auth.setRefreshToken(CodecUtils.nextId(32));
		auth.setGrantType(GrantType.authorization_code);
		auth.setModifyDate(new Date());
		authorizationManager.save(auth);
		return auth;
	}

	@Override
	public Authorization retrieve(String accessToken) {
		Authorization auth = authorizationManager.findByAccessToken(accessToken);
		return auth;
	}

	@Override
	public Authorization refresh(Client client, String refreshToken) {
		Client orig = findClientById(client.getClientId());
		if (orig == null)
			throw new OAuthError(OAuthError.UNAUTHORIZED_CLIENT, "client_id_not_exists");
		if (!orig.getSecret().equals(client.getSecret()))
			throw new OAuthError(OAuthError.UNAUTHORIZED_CLIENT, "client_secret_mismatch");
		Authorization auth = authorizationManager.findOne("refreshToken", refreshToken);
		if (auth == null)
			throw new OAuthError(OAuthError.INVALID_GRANT);
		auth.setAccessToken(CodecUtils.nextId(32));
		auth.setRefreshToken(CodecUtils.nextId(32));
		auth.setModifyDate(new Date());
		authorizationManager.save(auth);
		return auth;
	}

	@Override
	public boolean revoke(String accessToken) {
		Authorization auth = authorizationManager.findByNaturalId(accessToken);
		if (auth != null) {
			authorizationManager.delete(auth);
			return true;
		}
		return false;
	}

	@Override
	public void create(Authorization authorization) {
		authorizationManager.save(authorization);
	}

	@Override
	public List<Authorization> findAuthorizationsByGrantor(String grantor) {
		DetachedCriteria dc = authorizationManager.detachedCriteria();
		dc.add(Restrictions.eq("grantor", grantor));
		dc.addOrder(Order.desc("modifyDate"));
		return authorizationManager.findListByCriteria(dc);
	}

	@Override
	public void deleteAuthorizationsByGrantor(String grantor, String client, GrantType grantType) {
		DetachedCriteria dc = authorizationManager.detachedCriteria();
		dc.add(Restrictions.eq("grantor", grantor));
		if (client != null)
			dc.add(Restrictions.eq("client", client));
		if (grantType != null)
			dc.add(Restrictions.eq("grantType", grantType));
		List<Authorization> list = authorizationManager.findListByCriteria(dc);
		for (Authorization authorization : list)
			authorizationManager.delete(authorization);
	}

	@Trigger
	@Scheduled(cron = "0 30 23 * * ?")
	@Mutex
	public void removeExpired() {
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.SECOND, (int) (-expireTime));
		authorizationManager.executeUpdate("delete from Authorization a where lifetime >0 and a.modifyDate < ?1",
				cal.getTime());
	}

	@Override
	public Client findClientById(String clientId) {
		if (StringUtils.isBlank(clientId))
			return null;
		Client c = clientManager.get(clientId);
		return c != null && c.isEnabled() ? c : null;
	}

	@Override
	public List<Client> findClientByOwner(UserDetails owner) {
		DetachedCriteria dc = clientManager.detachedCriteria();
		dc.add(Restrictions.eq("owner", owner));
		dc.addOrder(Order.asc("createDate"));
		return clientManager.findListByCriteria(dc);
	}
}
