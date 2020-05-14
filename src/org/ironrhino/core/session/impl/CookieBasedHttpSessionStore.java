package org.ironrhino.core.session.impl;

import java.math.BigInteger;
import java.net.URLDecoder;
import java.util.Map;
import java.util.TreeMap;

import javax.servlet.http.Cookie;

import org.apache.commons.lang3.StringUtils;
import org.ironrhino.core.security.SecurityConfig;
import org.ironrhino.core.security.util.Blowfish;
import org.ironrhino.core.session.HttpSessionManager;
import org.ironrhino.core.session.HttpSessionStore;
import org.ironrhino.core.session.SessionCompressorManager;
import org.ironrhino.core.session.WrappedHttpSession;
import org.ironrhino.core.util.NumberUtils;
import org.ironrhino.core.util.RequestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component("cookieBased")
@Slf4j
public class CookieBasedHttpSessionStore implements HttpSessionStore {

	public static final int SINGLE_COOKIE_SIZE = 2 * 1024;

	@Value("${httpSessionManager.sessionCookieName:" + HttpSessionManager.DEFAULT_SESSION_COOKIE_NAME + "}")
	private String sessionCookieName = HttpSessionManager.DEFAULT_SESSION_COOKIE_NAME;

	@Autowired(required = false)
	private SecurityConfig securityConfig;

	@Autowired
	private SessionCompressorManager sessionCompressorManager;

	@Override
	public void initialize(WrappedHttpSession session) {
		session.setCacheBased(false);
		String cookie = getCookie(session);
		if (StringUtils.isNotBlank(cookie)) {
			cookie = decrypt(cookie, session.getId());
			String creationTime = NumberUtils.decimalToX(62, BigInteger.valueOf(session.getCreationTime()));
			if (cookie.startsWith("{") || cookie.startsWith(creationTime)) {
				cookie = cookie.substring(creationTime.length());
				sessionCompressorManager.uncompress(session, cookie);
			} else {
				invalidate(session);
			}
		}
	}

	@Override
	public void save(WrappedHttpSession session) {
		if (!session.isDirty())
			return;
		String sessionString = sessionCompressorManager.compress(session);
		if (StringUtils.isNotBlank(sessionString)) {
			String creationTime = NumberUtils.decimalToX(62, BigInteger.valueOf(session.getCreationTime()));
			String cookie = creationTime + sessionString;
			save(session, encrypt(cookie, session.getId()));
		} else
			clear(session);
	}

	@Override
	public void invalidate(WrappedHttpSession session) {
		clear(session);
	}

	protected String encrypt(String value, String salt) {
		return Blowfish.encryptWithSalt(value, salt);
	}

	protected String decrypt(String value, String salt) {
		return Blowfish.decryptWithSalt(value, salt);
	}

	private String getCookie(WrappedHttpSession session) {
		Map<String, String> cookieMap = new TreeMap<>();
		Cookie[] cookies = session.getRequest().getCookies();
		if (cookies != null) {
			for (Cookie cookie : cookies)
				if (isSessionCookie(cookie.getName()))
					try {
						cookieMap.put(cookie.getName(), URLDecoder.decode(cookie.getValue(), "UTF-8"));
					} catch (Exception e) {
						log.error(e.getMessage(), e);
					}
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < cookieMap.size(); i++) {
			String s = i == 0 ? cookieMap.get(sessionCookieName) : cookieMap.get(sessionCookieName + (i - 1));
			if (s == null) {
				clear(session);
				return null;
			}
			sb.append(s);
		}
		return sb.toString();
	}

	private void save(WrappedHttpSession session, String value) {
		clear(session);
		if (StringUtils.isNotBlank(value)) {
			int pieces = value.length() / SINGLE_COOKIE_SIZE;
			if (value.length() % SINGLE_COOKIE_SIZE != 0)
				pieces++;
			for (int i = 0; i < pieces; i++)
				RequestUtils.saveCookie(session.getRequest(), session.getResponse(),
						i == 0 ? sessionCookieName : sessionCookieName + (i - 1),
						value.substring(i * SINGLE_COOKIE_SIZE,
								i == pieces - 1 ? value.length() : (i + 1) * SINGLE_COOKIE_SIZE),
						securityConfig != null ? securityConfig.isGlobalCookie() : false, true);
		}
	}

	private void clear(WrappedHttpSession session) {
		Cookie[] cookies = session.getRequest().getCookies();
		if (cookies != null)
			for (Cookie cookie : cookies)
				if (isSessionCookie(cookie.getName()))
					RequestUtils.deleteCookie(session.getRequest(), session.getResponse(), cookie.getName(), true);
	}

	private boolean isSessionCookie(String name) {
		return name.equals(sessionCookieName)
				|| name.startsWith(sessionCookieName) && name.matches(sessionCookieName + "\\d*");
	}

}
