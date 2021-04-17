package com.xxl.job.core.thread.impl;

import com.xxl.job.core.biz.model.TriggerParam;
import com.xxl.job.core.handler.IJobHandler;
import com.xxl.job.core.thread.AbstractJobExecute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by hfc on 2021/4/15.
 */
public class JobSingleThread extends AbstractJobExecute {

    private static Logger logger = LoggerFactory.getLogger(JobSingleThread.class);

    public JobSingleThread(int jobId, IJobHandler handler) {
        super(jobId, handler);
    }

    @Override
    public void doJob(TriggerParam triggerParam) {
        doJobDefault(triggerParam);
    }

    @Override
    public void interrupt() {
        t.interrupt();
    }

    @Override
    public void join() throws InterruptedException {
        t.join();
    }
}
