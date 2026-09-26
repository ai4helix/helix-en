package com.helix.engine.config;

import com.alibaba.druid.pool.DruidDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Datasource configuration.
 *
 * <p>Only declares the Druid datasource; the SqlSessionFactory is left to MyBatis-Plus auto-configuration
 * (manual wiring conflicts with auto-configuration and would leave Mapper interfaces unscanned).</p>
 */
@Configuration
public class DataSourceConfig {

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource")
    @ConditionalOnMissingBean(DataSource.class)
    public DataSource dataSource() {
        return new DruidDataSource();
    }
}
