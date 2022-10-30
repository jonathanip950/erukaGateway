package com.jebsen.gateway;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.annotation.EnableTransactionManagement;


@EnableTransactionManagement
@EnableJpaRepositories(entityManagerFactoryRef = "brokerEntityManagerFactory",
	basePackages = { "com.jebsen.repository", "com.jebsen.broker.repository" })
@Configuration
public class GatewayConfiguration {

	@Primary
	@Bean(name = "brokerDaoDataSource")
	@Qualifier("brokerDaoDataSource")
	@ConfigurationProperties(prefix="spring.datasource.brokerdao")
	public DataSource brokerDaoDataSource(){
		return DataSourceBuilder.create().build();
	}
	
	@Primary
	@Bean(name = "brokerEntityManagerFactory")
	public LocalContainerEntityManagerFactoryBean brokerEntityManagerFactory (
			EntityManagerFactoryBuilder builder,
			@Qualifier("brokerDaoDataSource") DataSource dataSource) {
		return builder.dataSource(dataSource)
				.packages("com.jebsen")
				.persistenceUnit("broker").build();
	}
	
}
