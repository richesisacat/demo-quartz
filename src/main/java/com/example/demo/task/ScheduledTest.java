package com.example.demo.task;

import lombok.extern.slf4j.Slf4j;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

@Slf4j
public class ScheduledTest implements Job {

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    log.info("JobName: { " + context.getJobDetail().getKey().getName() + "}");
  }
}
