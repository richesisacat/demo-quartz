### 一、基于内存管理定时任务
#### 1、添加Maven依赖

```
<dependency>
    <groupId>org.quartz-scheduler</groupId>
    <artifactId>quartz</artifactId>
    <version>2.3.0</version>
</dependency>
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-context-support</artifactId>
</dependency>
```
#### 2、Spring Boot整合

```
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
    //schedulerFactoryBean.setQuartzProperties(quartzProperties());
    //schedulerFactoryBean.setDataSource(dataSource);
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
```

这里注入了一个 自定义的JobFactory ，然后 把其设置为SchedulerFactoryBean 的 JobFactory。其目的是因为我在具体的Job 中 需要Spring 注入一些Service。
所以我们要自定义一个jobfactory， 让其在具体job 类实例化时 使用Spring 的API 来进行依赖注入。

SpringJobFactory 具体实现：

```
@Component
public class SpringJobFactory extends AdaptableJobFactory {

  @Autowired
  private AutowireCapableBeanFactory capableBeanFactory;

  @Override
  protected Object createJobInstance(final TriggerFiredBundle bundle) throws Exception {
    Object jobInstance = super.createJobInstance(bundle);
    capableBeanFactory.autowireBean(jobInstance);
    return jobInstance;
  }
}
```
#### 3、定义Service方法管理任务

```
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

    final String jobName = info.jobClass();
    final String jobGroup = info.getJobGroup();
    final String cronExpression = info.getCronExpression();
    final String jobDescription = info.getJobDescription();
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
          .usingJobData(info.getJobDataMap())
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
    final String jobName = info.jobClass();
    final String jobGroup = info.getJobGroup();
    final String cronExpression = info.getCronExpression();
    final String jobDescription = info.getJobDescription();
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

      final JobDetail jobDetail = jobBuilder.usingJobData(info.getJobDataMap()).withDescription(jobDescription).build();

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
```
#### 4、任务实体类

```
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TaskInfo {

  private JobDataMap jobDataMap;

  // 任务名称
  @ApiModelProperty("任务类全名")
  private String jobName;

  //任务分组
  @ApiModelProperty("任务分组")
  private String jobGroup;

  //任务描述
  @ApiModelProperty("任务描述")
  private String jobDescription;

  //任务状态
  @ApiModelProperty("任务状态 新建编辑时忽略此项")
  private String jobStatus;

  //任务表达式
  @ApiModelProperty("cron表达式")
  private String cronExpression;

  @ApiModelProperty("创建时间 新建编辑时忽略此项")
  private String createTime;

  public String jobClass() {
    return jobName == null ? null : jobName.split("#")[0];
  }
}
```
#### 5、一个具体任务的实例

```
@Slf4j
public class ScheduledTest implements Job {

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    log.info("JobName: { " + context.getJobDetail().getKey().getName() + "}");
  }
}
```
注意：JobName传的是具体任务的包名+类名，service中反射取到具体类
### 二、基于数据库的集群模式
#### 1、修改Config
放开前一部的两行注释
#### 2、增加配置文件quartz.properties

```
#============================================================================
# Configure Main Scheduler Properties
#============================================================================
org.quartz.scheduler.instanceName:DictScheduler
org.quartz.scheduler.instanceId:AUTO
org.quartz.scheduler.skipUpdateCheck:true
#============================================================================
# Configure ThreadPool
#============================================================================
org.quartz.threadPool.class:org.quartz.simpl.SimpleThreadPool
org.quartz.threadPool.threadCount:5
org.quartz.threadPool.threadPriority:5
#============================================================================
# Configure JobStore
#============================================================================
org.quartz.jobStore.misfireThreshold:60000
org.quartz.jobStore.class=org.quartz.impl.jdbcjobstore.JobStoreTX
org.quartz.jobStore.driverDelegateClass=org.quartz.impl.jdbcjobstore.StdJDBCDelegate
#org.quartz.jobStore.useProperties=true
org.quartz.jobStore.tablePrefix=QRTZ_
org.quartz.jobStore.isClustered=false
```
#### 3、数据库需要建11张表
建表语句在quartz的jar包中：\docs\dbTables目录下
Jar包下载：http://www.quartz-scheduler.org/
#### 4、数据源配置application.yml
spring:
  datasource:
      driverClass: com.mysql.jdbc.Driver
      url: jdbc:mysql://127.0.0.1:3306/quartz?useUnicode=true&characterEncoding=utf-8&useSSL=false
      username: admin
      password: 123456
