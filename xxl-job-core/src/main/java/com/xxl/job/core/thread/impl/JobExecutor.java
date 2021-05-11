package com.xxl.job.core.thread.impl;

import com.xxl.job.core.biz.model.TriggerParam;
import com.xxl.job.core.handler.IJobHandler;
import com.xxl.job.core.thread.AbstractJobExecute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by hfc on 2021/4/15.
 */
public class JobExecutor extends AbstractJobExecute {

    private static Logger logger = LoggerFactory.getLogger(JobExecutor.class);

    private static AtomicInteger accumulator = new AtomicInteger();

    // 用于执行任务的线程池，使该实现可以并行执行
    private ExecutorService executor;

    public JobExecutor(int jobId, IJobHandler handler) {
        super(jobId, handler);
    }

    @Override
    public void start() {
        int coreNum = Runtime.getRuntime().availableProcessors();
        executor = new ThreadPoolExecutor(coreNum * 2,
                coreNum * 2 * 20,
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(500),
                r -> new Thread(r, "xxl-job-executor-thread-" + accumulator.incrementAndGet()),
                (r, e) -> {
                    r.run();
                    logger.warn("xxl-job-executor-thread is EXHAUSTED!");
                });
        super.start();
    }

    @Override
    public void doJob(TriggerParam triggerParam) {
        executor.execute(() -> doJobDefault(triggerParam));
    }

    @Override
    public void interrupt() {
        executor.shutdown();
        t.interrupt();
    }

    @Override
    public void join() throws InterruptedException {
        t.join();
    }
}
