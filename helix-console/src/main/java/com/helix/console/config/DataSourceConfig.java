package com.helix.console.config;

import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

@Configuration
@MapperScan(basePackages = "com.helix.console.system.mapper",
        sqlSessionFactoryRef = "sysSqlSessionFactory")
@MapperScan(basePackages = {
        "com.helix.console.engine.mapper",
        "com.helix.console.knowledge.mapper",
        "com.helix.console.datamanage.mapper",
        "com.helix.console.result.mapper",
        "com.helix.console.batch.mapper"},
        sqlSessionFactoryRef = "engineSqlSessionFactory")
public class DataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("helix.datasource.sys")
    public DataSource sysDataSource() {
        return DruidDataSourceBuilder.create().build();
    }

    @Bean
    @ConfigurationProperties("helix.datasource.engine")
    public DataSource engineDataSource() {
        return DruidDataSourceBuilder.create().build();
    }

    @Bean
    @Primary
    public SqlSessionFactory sysSqlSessionFactory(@Qualifier("sysDataSource") DataSource dataSource,
                                                  MybatisPlusInterceptor interceptor) throws Exception {
        return buildFactory(dataSource, interceptor);
    }

    @Bean
    public SqlSessionFactory engineSqlSessionFactory(@Qualifier("engineDataSource") DataSource dataSource,
                                                     MybatisPlusInterceptor interceptor) throws Exception {
        return buildFactory(dataSource, interceptor);
    }

    @Bean
    @Primary
    public DataSourceTransactionManager sysTxManager(@Qualifier("sysDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    public DataSourceTransactionManager engineTxManager(@Qualifier("engineDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    private SqlSessionFactory buildFactory(DataSource dataSource,
                                           MybatisPlusInterceptor interceptor) throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPlugins(interceptor);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/*.xml"));
        return factory.getObject();
    }
}
