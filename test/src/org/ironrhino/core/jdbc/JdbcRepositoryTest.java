package org.ironrhino.core.jdbc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.ironrhino.common.model.Gender;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = JdbcConfiguration.class)
public class JdbcRepositoryTest {

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private DogRepository dogRepository;

	@Before
	public void setup() {
		personRepository.createTable();
	}

	@After
	public void cleanup() {
		personRepository.dropTable();
	}

	@Test
	public void testCrud() throws Exception {
		Person p = new Person();
		p.setName("test");
		p.setDob(LocalDate.now());
		p.setSince(YearMonth.now());
		p.setAge(11);
		p.setGender(Gender.FEMALE);
		p.setAmount(new BigDecimal("12.00"));
		p.setAttributes(new HashMap<>());
		p.getAttributes().put("key1", "value1");
		p.getAttributes().put("key2", "value2");
		p.setRoles(new LinkedHashSet<>());
		p.getRoles().add("test1");
		p.getRoles().add("test2");
		personRepository.save(p);
		Person p2 = personRepository.get(p.getName());
		assertEquals(p, p2);
		List<Person> all = personRepository.list();
		assertEquals(1, all.size());
		assertEquals(p, all.get(0));
		List<Person> females = personRepository.listByGender(Gender.FEMALE);
		assertEquals(1, females.size());
		assertEquals(p, females.get(0));
		List<Person> result = personRepository.search("te");
		assertEquals(1, result.size());
		assertEquals(p, result.get(0));
		assertEquals(1, personRepository.count());
		assertEquals(1, personRepository.countByNamePrefix("te"));
		assertEquals(1, personRepository.listNames().size());
		assertEquals(1, personRepository.listGenders().size());
		assertEquals(1, personRepository.listAges().size());
		assertFalse(personRepository.updateAmount("test", new BigDecimal("11.00"), new BigDecimal("120.00")));
		assertTrue(personRepository.updateAmount("test", new BigDecimal("12.00"), new BigDecimal("120.00")));
		Person p3 = personRepository.get("test");
		assertEquals(new BigDecimal("120.00"), p3.getAmount());
		int rows = personRepository.delete("test");
		assertEquals(1, rows);
		all = personRepository.list();
		assertTrue(all.isEmpty());
	}

	@Test
	public void testDefaultMethod() throws Exception {
		Person p = new Person();
		p.setName("test");
		p.setDob(LocalDate.now());
		p.setSince(YearMonth.now());
		p.setAge(11);
		p.setGender(Gender.FEMALE);
		p.setAmount(new BigDecimal("12.00"));
		personRepository.save(p);
		p = personRepository.getAndChangeAge(p.getName(), 0);
		assertEquals("test", p.getName());
		assertEquals(0, p.getAge());
		p = personRepository.getAndChangeGender(p.getName(), Gender.MALE);
		assertEquals("test", p.getName());
		assertEquals(Gender.MALE, p.getGender());
	}

	@Test
	public void testInCondition() throws Exception {
		Person p = new Person();
		p.setName("test1");
		p.setDob(LocalDate.now());
		p.setSince(YearMonth.now());
		p.setAge(11);
		p.setGender(Gender.FEMALE);
		p.setAmount(new BigDecimal("12.00"));
		personRepository.save(p);
		p.setName("test2");
		p.setGender(Gender.MALE);
		personRepository.save(p);
		p.setName("test3");
		personRepository.save(p);
		assertEquals(0, personRepository.getByNames(new String[] { "test" }).size());
		assertEquals(1, personRepository.getByNames(new String[] { "test1" }).size());
		assertEquals(2, personRepository.getByNames(new String[] { "test1", "test2" }).size());
		assertEquals(2, personRepository.getByNames(new String[] { "test1", "test2", "test" }).size());
		assertEquals(3, personRepository.getByNames(new String[] { "test1", "test2", "test3" }).size());
		assertEquals(1, personRepository.getByGenders(EnumSet.of(Gender.FEMALE)).size());
		assertEquals(2, personRepository.getByGenders(EnumSet.of(Gender.MALE)).size());
		assertEquals(3, personRepository.getByGenders(EnumSet.of(Gender.FEMALE, Gender.MALE)).size());
	}

	@Test
	public void testConditionalSql() throws Exception {
		Person p = new Person();
		p.setName("test1");
		p.setDob(LocalDate.now());
		p.setSince(YearMonth.now());
		p.setAge(11);
		p.setGender(Gender.FEMALE);
		p.setAmount(new BigDecimal("12.00"));
		personRepository.save(p);
		p.setName("test2");
		p.setGender(Gender.MALE);
		personRepository.save(p);
		p.setName("test3");
		personRepository.save(p);
		assertEquals(3, personRepository.searchByNameOrGender(null, null).size());
		assertEquals(1, personRepository.searchByNameOrGender("test1", null).size());
		assertEquals(1, personRepository.searchByNameOrGender("test1", Gender.FEMALE).size());
		assertEquals(0, personRepository.searchByNameOrGender("test1", Gender.MALE).size());
		assertEquals(2, personRepository.searchByNameOrGender(null, Gender.MALE).size());
	}

	@Test
	public void testNestedProperty() throws Exception {
		Person p = new Person();
		p.setName("test");
		p.setDob(LocalDate.now());
		p.setSince(YearMonth.now());
		p.setAge(11);
		p.setGender(Gender.FEMALE);
		p.setAmount(new BigDecimal("12.00"));
		personRepository.save(p);
		Person p2 = personRepository.getWithShadow(p.getName());
		assertNotNull(p2.getShadow());
		assertEquals(p2.getName(), p2.getShadow().getName());
		assertEquals(p2.getGender(), p2.getShadow().getGender());
		assertEquals(p2.getDob(), p2.getShadow().getDob());
		assertEquals(p2.getAge(), p2.getShadow().getAge());
		assertEquals(p2.getAmount(), p2.getShadow().getAmount());
	}

	@Test
	public void testLimiting() throws Exception {
		Person p = new Person();
		p.setName("test1");
		p.setDob(LocalDate.now());
		p.setSince(YearMonth.now());
		p.setAge(11);
		p.setGender(Gender.FEMALE);
		p.setAmount(new BigDecimal("12.00"));
		personRepository.save(p);
		p.setName("test2");
		p.setGender(Gender.MALE);
		personRepository.save(p);
		p.setName("test3");
		personRepository.save(p);
		assertEquals(3, personRepository.searchWithLimiting("test", Limiting.of(10)).size());
		assertEquals(3, personRepository.searchWithLimiting("test", Limiting.of(3)).size());
		assertEquals(2, personRepository.searchWithLimiting("test", Limiting.of(2)).size());
		assertEquals(1, personRepository.searchWithLimiting("test", Limiting.of(1)).size());
		assertEquals(0, personRepository.searchWithLimiting("test", Limiting.of(0)).size());
		List<Person> list = personRepository.searchWithLimiting("test", Limiting.of(1, 2));
		assertEquals(2, list.size());
		assertEquals("test2", list.get(0).getName());
		list = personRepository.searchWithLimiting("test", Limiting.of(2, 2));
		assertEquals(1, list.size());
		assertEquals("test3", list.get(0).getName());
	}

	@Test
	public void testGeneratedKey() throws Exception {
		dogRepository.createTable();
		int size = 10;
		for (int i = 0; i < size; i++) {
			Dog dog = new Dog();
			dog.setName("dog" + i);
			dogRepository.save(dog);
			assertEquals(Integer.valueOf(i + 1), dog.getId());
		}
		for (int i = 0; i < size; i++) {
			Dog dog = new Dog();
			dog.setName("dog" + i);
			dogRepository.insert(dog.getName(), id -> {
				dog.setId(id);
			});
			assertEquals(Integer.valueOf(size + i + 1), dog.getId());
		}
		for (int i = 0; i < size * 2; i++) {
			assertTrue(dogRepository.delete(i + 1));
		}
		dogRepository.dropTable();
	}

	@Test
	public void testOptional() throws Exception {
		Person p = new Person();
		p.setName("test");
		p.setDob(LocalDate.now());
		p.setSince(YearMonth.now());
		p.setAge(11);
		p.setGender(Gender.FEMALE);
		p.setAmount(new BigDecimal("12.00"));
		p.setAttributes(new HashMap<>());
		p.getAttributes().put("key1", "value1");
		p.getAttributes().put("key2", "value2");
		p.setRoles(new LinkedHashSet<>());
		p.getRoles().add("test1");
		p.getRoles().add("test2");
		personRepository.save(p);
		Optional<Person> optional = personRepository.getOptional(p.getName());
		assertTrue(optional.isPresent());
		assertEquals(p, optional.get());
		assertFalse(personRepository.getOptional("notexists").isPresent());
	}

	@Test
	public void testRowCallbackHanlder() throws Exception {
		Person p = new Person();
		p.setName("test1");
		p.setDob(LocalDate.now());
		p.setSince(YearMonth.now());
		p.setAge(11);
		p.setGender(Gender.FEMALE);
		p.setAmount(new BigDecimal("12.00"));
		personRepository.save(p);
		p.setName("test2");
		p.setGender(Gender.MALE);
		personRepository.save(p);
		p.setName("test3");
		personRepository.save(p);
		AtomicInteger count = new AtomicInteger();
		personRepository.searchWithLimiting("test", Limiting.of(2), new RowCallbackHandler() {
			@Override
			public void processRow(ResultSet rs) throws SQLException {
				count.incrementAndGet();
			}
		});
		assertEquals(2, count.get());
	}

}
