package com.example.demo.task;

import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TaskTest {

  @Scheduled(cron = "0 0/1 * * * ?")
  public void abc() {
    log.info("JobName: { abc }");
  }
}
