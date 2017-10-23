package com.example.demo.task;

import io.swagger.annotations.ApiModelProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.quartz.JobDataMap;

@Data
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
}
