package org.ironrhino.core.spring.data.redis;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.ironrhino.core.metadata.Scope;
import org.ironrhino.core.model.NullObject;
import org.junit.Test;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

public abstract class RedisSerializerTestBase {

	protected abstract RedisSerializer<Object> getRedisSerializer();

	@Test
	public void testNullObject() {
		test(NullObject.get());
	}

	@Test
	public void testPrimitive() {
		test(true);
		test(1);
		test(120L);
		test(1.0);
		test(20.0f);
	}

	@Test
	public void testSimpleObject() {
		test(Scope.GLOBAL);
		test(new Date());
		test(new BigDecimal("100.00"));
		test(new AtomicInteger(100));
	}
	
	@Test
	public void testDateAndTime() {
		test(LocalDate.now());
		test(LocalDateTime.now());
	}

	@Test
	public void testCollectionObject() {
		ArrayList<GrantedAuthority> list = new ArrayList<>();
		list.add(new SimpleGrantedAuthority("test"));
		test(list);
		HashMap<String, GrantedAuthority> map = new HashMap<>();
		map.put("test", new SimpleGrantedAuthority("test"));
		test(map);
	}

	@Test
	public void testComplexObject() {
		User u = new User();
		u.setUsername("test");
		u.setPassword("test");
		u.setAge(100);
		u.setStatus(Status.DISABLED);
		u.setAuthorities(Collections.singletonList(new SimpleGrantedAuthority("test")));
		u.setCreated(LocalDateTime.now());
		test(u);
	}

	private void test(Object obj) {
		RedisSerializer<Object> serializer = getRedisSerializer();
		byte[] bytes = serializer.serialize(obj);
		Object obj2 = serializer.deserialize(bytes);
		if (obj instanceof NullObject || obj instanceof Enum)
			assertThat(obj == obj2, is(true));
		if (obj instanceof AtomicInteger) // AtomicInteger not override equals
			assertThat(
					obj.getClass() == obj2.getClass() && ((Number) obj).doubleValue() == ((Number) obj2).doubleValue(),
					is(true));
		else
			assertThat(obj2, is(obj));
	}

	static enum Status {
		ACTIVE, DISABLED;
	}

	@Data
	static class User implements UserDetails {
		private static final long serialVersionUID = 1L;
		private String username;
		private String password;
		private int age;
		private Status status;
		private List<GrantedAuthority> authorities;
		private LocalDateTime created;

		@Override
		@JsonIgnore
		public String getPassword() {
			return password;
		}

		@Override
		public boolean isAccountNonExpired() {
			return false;
		}

		@Override
		public boolean isAccountNonLocked() {
			return false;
		}

		@Override
		public boolean isCredentialsNonExpired() {
			return false;
		}

		@Override
		public boolean isEnabled() {
			return status == Status.ACTIVE;
		}

		public User getSelf() {
			return this;
		}

	}

}
