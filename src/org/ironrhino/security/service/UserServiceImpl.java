package org.ironrhino.security.service;

import java.util.List;

import org.ironrhino.core.spring.security.ConcreteUserDetailsService;
import org.ironrhino.core.util.BeanUtils;
import org.ironrhino.security.domain.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@Primary
public class UserServiceImpl implements UserService {

	@Autowired(required = false)
	private List<ConcreteUserDetailsService<? extends UserDetails>> userDetailsServices;

	@Override
	public User loadUserByUsername(String username) {
		if (username == null)
			throw new IllegalArgumentException("username shouldn't be null");
		if (userDetailsServices != null)
			for (ConcreteUserDetailsService<?> uds : userDetailsServices) {
				if (uds.accepts(username))
					try {
						User user = BeanUtils.forCopy(User.class).apply(uds.loadUserByUsername(username));
						if (user != null)
							return user;
					} catch (UsernameNotFoundException unfe) {
						continue;
					}
			}
		return null;
	}

}
