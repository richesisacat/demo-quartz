package com.example.demo.config;

import lombok.extern.slf4j.Slf4j;

import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.PropertiesFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import java.io.IOException;
import java.util.Properties;

import javax.sql.DataSource;

@Configuration
@Slf4j
public class QuartzConfig {

  @Autowired
  private SpringJobFactory springJobFactory;
  @Autowired
  private DataSource dataSource;

  @Bean
  public SchedulerFactoryBean schedulerFactoryBean() throws IOException {
    final SchedulerFactoryBean schedulerFactoryBean = new SchedulerFactoryBean();
    schedulerFactoryBean.setJobFactory(springJobFactory);
    schedulerFactoryBean.setQuartzProperties(quartzProperties());
    schedulerFactoryBean.setDataSource(dataSource);
    return schedulerFactoryBean;
  }

  @Bean
  public Scheduler scheduler() throws IOException {
    return schedulerFactoryBean().getScheduler();
  }

  private Properties quartzProperties() throws IOException {
    final PropertiesFactoryBean propertiesFactoryBean = new PropertiesFactoryBean();
    propertiesFactoryBean.setLocation(new ClassPathResource("quartz.properties"));
    Properties properties = null;

    try {
      propertiesFactoryBean.afterPropertiesSet();
      properties = propertiesFactoryBean.getObject();
    } catch (IOException e) {
      log.error("读取quartz.properties失败", e);
    }
    return properties;
  }

}