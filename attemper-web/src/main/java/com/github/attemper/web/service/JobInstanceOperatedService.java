package com.github.attemper.web.service;

import com.github.attemper.common.enums.JobInstanceStatus;
import com.github.attemper.common.exception.RTException;
import com.github.attemper.common.param.dispatch.instance.JobInstanceGetParam;
import com.github.attemper.common.param.dispatch.instance.JobInstanceIdParam;
import com.github.attemper.common.property.StatusProperty;
import com.github.attemper.common.result.dispatch.instance.JobInstance;
import com.github.attemper.core.service.instance.JobInstanceService;
import com.github.attemper.invoker.service.JobCallingService;
import com.github.attemper.web.ext.app.ExecutorHandler;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.impl.cfg.IdGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
public class JobInstanceOperatedService {

    @Autowired
    private JobInstanceService jobInstanceService;

    @Autowired
    private JobCallingService jobCallingService;

    @Autowired
    private ExecutorHandler executorHandler;

    @Autowired
    private IdGenerator idGenerator;

    public Void retry(JobInstanceIdParam param) {
        JobInstance jobInstance = getJobInstance(param);
        if(!isDone(jobInstance.getStatus())) {
            throw new RTException(6202, jobInstance.getStatus());
        }
        String parentId = jobInstance.getId();
        if (jobInstance.getParentId() == null) {
            jobInstance.setParentId(jobInstance.getId());
            jobInstanceService.updateDone(jobInstance);
        }
        jobCallingService.retry(idGenerator.getNextId(), jobInstance.getJobName(), jobInstance.getTenantId(), parentId, null);
        return null;
    }

    public Void terminate(JobInstanceIdParam param) {
        JobInstance jobInstance = getJobInstance(param);
        if (!isDoing(jobInstance.getStatus())) {
            throw new RTException(6202, jobInstance.getStatus());
        }
        executorHandler.terminate(jobInstance.getExecutorUri(), jobInstance);
        int code = 901;
        jobInstance.setCode(code);
        jobInstance.setMsg(StatusProperty.getValue(code));
        updateJobInstanceStatus(jobInstance, JobInstanceStatus.TERMINATED.getStatus());
        return null;
    }

    private void updateJobInstanceStatus(JobInstance jobInstance, int status) {
        jobInstance.setStatus(status);
        jobInstanceService.updateDone(jobInstance);
    }

    private boolean isDoing(int status) {
        return status == JobInstanceStatus.RUNNING.getStatus();
    }

    private boolean isDone(int status) {
        return status == JobInstanceStatus.SUCCESS.getStatus() ||
                status == JobInstanceStatus.FAILURE.getStatus() ||
                status == JobInstanceStatus.TERMINATED.getStatus();
    }

    private JobInstance getJobInstance(JobInstanceIdParam param) {
        JobInstance jobInstance = jobInstanceService.get(new JobInstanceGetParam().setId(param.getId()));
        if (jobInstance == null) {
            throw new RTException(6201);
        }
        return jobInstance;
    }
}
