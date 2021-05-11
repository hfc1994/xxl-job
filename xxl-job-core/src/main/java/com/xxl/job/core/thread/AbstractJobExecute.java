package com.xxl.job.core.thread;

import com.xxl.job.core.biz.model.HandleCallbackParam;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.biz.model.TriggerParam;
import com.xxl.job.core.context.XxlJobContext;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.executor.XxlJobExecutor;
import com.xxl.job.core.handler.IJobHandler;
import com.xxl.job.core.log.XxlJobFileAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by hfc on 2021/4/15.
 */
public abstract class AbstractJobExecute {

    private static Logger logger = LoggerFactory.getLogger(AbstractJobExecute.class);

    private static AtomicInteger accumulator = new AtomicInteger();

    protected int jobId;
    protected IJobHandler handler;
    protected LinkedBlockingQueue<TriggerParam> triggerQueue;
    protected Set<Long> triggerLogIdSet;		// avoid repeat trigger for the same TRIGGER_LOG_ID

    protected volatile boolean toStop = false;
    protected String stopReason;

    protected boolean running = false;    // if running job
    protected int idleTimes = 0;			// idel times

    // 用于启动的线程，保持接口可以立即返回
    protected Thread t;

    public AbstractJobExecute(int jobId, IJobHandler handler) {
        this.jobId = jobId;
        this.handler = handler;
        this.triggerQueue = new LinkedBlockingQueue<TriggerParam>();
        this.triggerLogIdSet = new CopyOnWriteArraySet<>();
    }

    public IJobHandler getHandler() {
        return handler;
    }

    /**
     * new trigger to queue
     *
     * @param triggerParam
     * @return
     */
    public ReturnT<String> pushTriggerQueue(TriggerParam triggerParam) {
        // avoid repeat
        if (triggerLogIdSet.contains(triggerParam.getLogId())) {
            logger.info(">>>>>>>>>>> repeate trigger job, logId:{}", triggerParam.getLogId());
            return new ReturnT<String>(ReturnT.FAIL_CODE, "repeate trigger job, logId:" + triggerParam.getLogId());
        }

        triggerLogIdSet.add(triggerParam.getLogId());
        triggerQueue.add(triggerParam);
        return ReturnT.SUCCESS;
    }

    /**
     * kill job thread
     *
     * @param stopReason
     */
    public void toStop(String stopReason) {
        /**
         * Thread.interrupt只支持终止线程的阻塞状态(wait、join、sleep)，
         * 在阻塞出抛出InterruptedException异常,但是并不会终止运行的线程本身；
         * 所以需要注意，此处彻底销毁本线程，需要通过共享变量方式；
         */
        this.toStop = true;
        this.stopReason = stopReason;
    }

    /**
     * is running job
     * @return
     */
    public boolean isRunningOrHasQueue() {
        return running || triggerQueue.size()>0;
    }

    public void start() {
        t = new Thread(() -> {
            // init
            try {
                handler.init();
            } catch (Throwable e) {
                logger.error(e.getMessage(), e);
            }

            // execute
            while(!toStop){
                running = false;
                idleTimes++;

                TriggerParam triggerParam = null;
                try {
                    // to check toStop signal, we need cycle, so wo cannot use queue.take(), instand of poll(timeout)
                    triggerParam = triggerQueue.poll(3L, TimeUnit.SECONDS);
                    if (triggerParam != null) {
                        running = true;
                        idleTimes = 0;

                        // 子类来实现
                        doJob(triggerParam);
                    } else {
                        // 没任务时，每次循环会在triggerQueue获取任务上阻塞3秒
                        if (idleTimes > 30) {
                            if(triggerQueue.size() == 0) {	// avoid concurrent trigger causes jobId-lost
                                XxlJobExecutor.removeJobThread(jobId, "excutor idel times over limit.");
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }
            }

            // callback trigger request in queue
            while(triggerQueue !=null && triggerQueue.size()>0){
                TriggerParam triggerParam = triggerQueue.poll();
                if (triggerParam!=null) {
                    // is killed
                    TriggerCallbackThread.pushCallBack(new HandleCallbackParam(
                            triggerParam.getLogId(),
                            triggerParam.getLogDateTime(),
                            XxlJobContext.HANDLE_COCE_FAIL,
                            stopReason + " [job not executed, in the job queue, killed.]")
                    );
                }
            }

            // destroy
            try {
                handler.destroy();
            } catch (Throwable e) {
                logger.error(e.getMessage(), e);
            }

            logger.info(">>>>>>>>>>> xxl-job JobExecute stoped, hashCode:{}", Thread.currentThread());

        }, "xxi-job-thread-" + accumulator.incrementAndGet());
        t.start();
    }

    // 给子类提供的默认实现方式
    public void doJobDefault(TriggerParam triggerParam) {
        triggerLogIdSet.remove(triggerParam.getLogId());

        // log filename, like "logPath/yyyy-MM-dd/9999.log"
        String logFileName = XxlJobFileAppender.makeLogFileName(new Date(triggerParam.getLogDateTime()), triggerParam.getLogId());
        XxlJobContext xxlJobContext = new XxlJobContext(
                triggerParam.getJobId(),
                triggerParam.getExecutorParams(),
                logFileName,
                triggerParam.getBroadcastIndex(),
                triggerParam.getBroadcastTotal());

        // 被存放在ThreadLocal对象中
        // init job context
        XxlJobContext.setXxlJobContext(xxlJobContext);

        // execute
        XxlJobHelper.log("<br>----------- xxl-job job execute start -----------<br>----------- Param:" + xxlJobContext.getJobParam());

        try {
            if (triggerParam.getExecutorTimeout() > 0) {
                // limit timeout
                Thread futureThread = null;
                try {
                    FutureTask<Boolean> futureTask = new FutureTask<Boolean>(new Callable<Boolean>() {
                        @Override
                        public Boolean call() throws Exception {

                            // init job context
                            XxlJobContext.setXxlJobContext(xxlJobContext);

                            handler.execute();
                            return true;
                        }
                    });
                    futureThread = new Thread(futureTask);
                    futureThread.start();

                    // 实现超时停止任务的功能，当然前提是线程能被interrupt中止的那种
                    Boolean tempResult = futureTask.get(triggerParam.getExecutorTimeout(), TimeUnit.SECONDS);
                } catch (TimeoutException e) {

                    XxlJobHelper.log("<br>----------- xxl-job job execute timeout");
                    XxlJobHelper.log(e);

                    // handle result
                    XxlJobHelper.handleTimeout("job execute timeout ");
                } finally {
                    if (futureThread != null) {
                        futureThread.interrupt();
                    }
                }
            } else {
                // just execute
                handler.execute();
            }

            // valid execute handle data
            if (XxlJobContext.getXxlJobContext().getHandleCode() <= 0) {
                XxlJobHelper.handleFail("job handle result lost.");
            } else {
                String tempHandleMsg = XxlJobContext.getXxlJobContext().getHandleMsg();
                tempHandleMsg = (tempHandleMsg!=null&&tempHandleMsg.length()>50000)
                        ?tempHandleMsg.substring(0, 50000).concat("...")
                        :tempHandleMsg;
                XxlJobContext.getXxlJobContext().setHandleMsg(tempHandleMsg);
            }
            XxlJobHelper.log("<br>----------- xxl-job job execute end(finish) -----------<br>----------- Result: handleCode="
                    + XxlJobContext.getXxlJobContext().getHandleCode()
                    + ", handleMsg = "
                    + XxlJobContext.getXxlJobContext().getHandleMsg()
            );
        }  catch (Throwable e) {
            if (toStop) {
                XxlJobHelper.log("<br>----------- JobThread toStop, stopReason:" + stopReason);
            }

            // handle result
            StringWriter stringWriter = new StringWriter();
            e.printStackTrace(new PrintWriter(stringWriter));
            String errorMsg = stringWriter.toString();

            XxlJobHelper.handleFail(errorMsg);

            XxlJobHelper.log("<br>----------- JobThread Exception:" + errorMsg + "<br>----------- xxl-job job execute end(error) -----------");
        } finally {
            // callback handler info
            if (!toStop) {
                // commonm
                TriggerCallbackThread.pushCallBack(new HandleCallbackParam(
                        triggerParam.getLogId(),
                        triggerParam.getLogDateTime(),
                        XxlJobContext.getXxlJobContext().getHandleCode(),
                        XxlJobContext.getXxlJobContext().getHandleMsg() )
                );
            } else {
                // is killed
                TriggerCallbackThread.pushCallBack(new HandleCallbackParam(
                        triggerParam.getLogId(),
                        triggerParam.getLogDateTime(),
                        XxlJobContext.HANDLE_COCE_FAIL,
                        stopReason + " [job running, killed]" )
                );
            }
        }
    }

    public abstract void doJob(TriggerParam triggerParam);

    public abstract void interrupt();

    public abstract void join() throws InterruptedException;

}
