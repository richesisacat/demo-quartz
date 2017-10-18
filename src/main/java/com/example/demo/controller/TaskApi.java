package com.example.demo.controller;

import com.example.demo.service.TaskService;
import com.example.demo.task.TaskInfo;

import io.swagger.annotations.ApiOperation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tasks")
public class TaskApi {

  @Autowired
  private TaskService taskService;

  @GetMapping
  @ApiOperation("任务列表")
  public List<TaskInfo> list() {
    return taskService.list();
  }

  @PostMapping
  @ApiOperation("任务新建")
  public void add(@RequestBody final TaskInfo taskInfo) {
    taskService.addJob(taskInfo);
  }

  @PutMapping
  @ApiOperation("任务编辑")
  public void edit(@RequestBody final TaskInfo taskInfo) {
    taskService.edit(taskInfo);
  }

  @DeleteMapping
  @ApiOperation("任务删除")
  public void delete(@RequestParam final String jobName, @RequestParam final String jobGroup) {
    taskService.delete(jobName, jobGroup);
  }

  @PutMapping("/pause")
  @ApiOperation("任务暂停")
  public void pause(@RequestParam final String jobName, @RequestParam final String jobGroup) {
    taskService.pause(jobName, jobGroup);
  }

  @PutMapping("/resume")
  @ApiOperation("任务恢复")
  public void resume(@RequestParam final String jobName, @RequestParam final String jobGroup) {
    taskService.resume(jobName, jobGroup);
  }
}
