package org.ironrhino.core.jdbc;

import javax.sql.DataSource;

import org.ironrhino.core.configuration.DataSourceConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement(proxyTargetClass = true)
@Import(DataSourceConfiguration.class)
public class JdbcConfiguration {

	@Bean
	public PlatformTransactionManager transactionManager(DataSource dataSource) {
		return new DataSourceTransactionManager(dataSource);
	}

	@Bean
	public static JdbcRepositoryRegistryPostProcessor jdbcRepositoryRegistryPostProcessor() {
		JdbcRepositoryRegistryPostProcessor obj = new JdbcRepositoryRegistryPostProcessor();
		obj.setPackagesToScan(new String[] { JdbcConfiguration.class.getPackage().getName() });
		return obj;
	}

	@Bean
	public CustomerPartitioner customerPartitioner() {
		return new CustomerPartitioner();
	}

}
