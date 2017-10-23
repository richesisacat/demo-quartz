package com.example.demo.service;

import com.example.demo.task.TaskInfo;

import lombok.extern.slf4j.Slf4j;

import org.hibernate.service.spi.ServiceException;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


@Service
@Slf4j
public class TaskService {

  @Autowired
  private Scheduler scheduler;

  /**
   * 任务列表.
   */
  public List<TaskInfo> list() {

    final List<TaskInfo> list = new ArrayList<>();
    try {
      for (final String groupJob : scheduler.getJobGroupNames()) {
        for (final JobKey jobKey : scheduler.getJobKeys(GroupMatcher.groupEquals(groupJob))) {

          final List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);
          for (final Trigger trigger : triggers) {
            final Trigger.TriggerState triggerState = scheduler.getTriggerState(trigger.getKey());
            final JobDetail jobDetail = scheduler.getJobDetail(jobKey);

            String cronExpression = "";
            String createTime = "";

            if (trigger instanceof CronTrigger) {
              final CronTrigger cronTrigger = (CronTrigger) trigger;
              cronExpression = cronTrigger.getCronExpression();
              createTime = cronTrigger.getDescription();
            }

            final TaskInfo info = new TaskInfo();
            info.setJobName(jobKey.getName());
            info.setJobGroup(jobKey.getGroup());
            info.setJobDescription(jobDetail.getDescription());
            info.setJobStatus(triggerState.name());
            info.setCronExpression(cronExpression);
            info.setCreateTime(createTime);
            info.setJobDataMap(jobDetail.getJobDataMap());
            list.add(info);
          }
        }
      }
    } catch (SchedulerException e) {
      log.error("获取任务列表异常", e);
    }

    return list;
  }

  /**
   * 保存定时任务.
   */
  public void addJob(final TaskInfo info) {

    final String jobName = info.getJobName();
    final String jobGroup = info.getJobGroup();
    final String cronExpression = info.getCronExpression();
    final String jobDescription = info.getJobDescription();
    final JobDataMap jobDataMap = info.getJobDataMap() == null ? new JobDataMap() : info.getJobDataMap();
    final String createTime = new DateTime().toString(DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss"));

    try {

      if (checkExists(jobName, jobGroup)) {
        log.info("===> AddJob fail, job already exist, jobGroup:{}, jobName:{}", jobGroup, jobName);
        throw new ServiceException(String.format("Job已经存在, jobName:{%s},jobGroup:{%s}", jobName, jobGroup));
      }

      final TriggerKey triggerKey = TriggerKey.triggerKey(jobName, jobGroup);
      final CronScheduleBuilder cronScheduleBuilder = CronScheduleBuilder.cronSchedule(cronExpression)
          .withMisfireHandlingInstructionDoNothing();
      final CronTrigger cronTrigger = TriggerBuilder.newTrigger().withIdentity(triggerKey)
          .withDescription(createTime).withSchedule(cronScheduleBuilder).build();

      final Class<? extends Job> clazz = Class.forName(jobName).asSubclass(Job.class);

      final JobKey jobKey = JobKey.jobKey(jobName, jobGroup);
      final JobDetail jobDetail = JobBuilder.newJob(clazz).withIdentity(jobKey)
          .withDescription(jobDescription)
          .usingJobData(jobDataMap)
          .build();

      scheduler.scheduleJob(jobDetail, cronTrigger);
    } catch (SchedulerException | ClassNotFoundException e) {
      throw new ServiceException("类名不存在或执行表达式错误");
    }
  }

  /**
   * 修改定时任务.
   */
  public void edit(final TaskInfo info) {
    final String jobName = info.getJobName();
    final String jobGroup = info.getJobGroup();
    final String cronExpression = info.getCronExpression();
    final String jobDescription = info.getJobDescription();
    final JobDataMap jobDataMap = info.getJobDataMap() == null ? new JobDataMap() : info.getJobDataMap();
    final String createTime = new DateTime().toString(DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss"));

    try {
      if (!checkExists(jobName, jobGroup)) {
        log.info("===> EditJob fail, job already exist, jobGroup:{}, jobName:{}", jobGroup, jobName);
        throw new ServiceException(String.format("Job不存在, jobName:{%s},jobGroup:{%s}", jobName, jobGroup));
      }

      final TriggerKey triggerKey = TriggerKey.triggerKey(jobName, jobGroup);
      final CronScheduleBuilder cronScheduleBuilder = CronScheduleBuilder.cronSchedule(cronExpression)
          .withMisfireHandlingInstructionDoNothing();
      final CronTrigger cronTrigger = TriggerBuilder.newTrigger().withIdentity(triggerKey)
          .withDescription(createTime).withSchedule(cronScheduleBuilder).build();

      final JobKey jobKey = new JobKey(jobName, jobGroup);
      final JobBuilder jobBuilder = scheduler.getJobDetail(jobKey).getJobBuilder();

      final JobDetail jobDetail = jobBuilder.usingJobData(jobDataMap).withDescription(jobDescription).build();

      final Set<Trigger> triggerSet = new HashSet<>();
      triggerSet.add(cronTrigger);

      scheduler.scheduleJob(jobDetail, triggerSet, true);
    } catch (SchedulerException e) {
      throw new ServiceException("类名不存在或执行表达式错误");
    }
  }

  /**
   * 删除定时任务.
   */
  public void delete(final String jobName, final String jobGroup) {
    final TriggerKey triggerKey = TriggerKey.triggerKey(jobName, jobGroup);
    try {
      if (checkExists(jobName, jobGroup)) {
        scheduler.pauseTrigger(triggerKey);
        scheduler.unscheduleJob(triggerKey);
        log.info("===> delete, triggerKey:{}", triggerKey);
      }
    } catch (SchedulerException e) {
      throw new ServiceException(e.getMessage());
    }
  }

  /**
   * 暂停定时任务.
   */
  public void pause(final String jobName, final String jobGroup) {
    final TriggerKey triggerKey = TriggerKey.triggerKey(jobName, jobGroup);
    try {
      if (checkExists(jobName, jobGroup)) {
        scheduler.pauseTrigger(triggerKey);
        log.info("===> Pause success, triggerKey:{}", triggerKey);
      }
    } catch (SchedulerException e) {
      throw new ServiceException(e.getMessage());
    }
  }

  /**
   * 重新开始任务.
   */
  public void resume(final String jobName, final String jobGroup) {
    final TriggerKey triggerKey = TriggerKey.triggerKey(jobName, jobGroup);
    try {
      if (checkExists(jobName, jobGroup)) {
        scheduler.resumeTrigger(triggerKey);
        log.info("===> Resume success, triggerKey:{}", triggerKey);
      }
    } catch (SchedulerException e) {
      log.error("恢复任务时出现异常", e);
    }
  }

  /**
   * 验证是否存在.
   */
  private boolean checkExists(String jobName, String jobGroup) throws SchedulerException {
    TriggerKey triggerKey = TriggerKey.triggerKey(jobName, jobGroup);
    return scheduler.checkExists(triggerKey);
  }
}
