package com.xxl.job.executor;

import com.xxl.job.core.biz.ExecutorBiz;
import com.xxl.job.core.biz.impl.ExecutorBizImpl;
import com.xxl.job.core.biz.model.TriggerParam;
import com.xxl.job.core.enums.ExecutorBlockStrategyEnum;
import com.xxl.job.core.glue.GlueTypeEnum;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

/**
 * @author xuxueli 2018-10-28 00:38:13
 */
@SpringBootApplication
public class XxlJobExecutorApplication {

	public static void main(String[] args) {
        SpringApplication.run(XxlJobExecutorApplication.class, args);
	}

	// 方便的测试，测试单机串行和单机并行的任务
	 @Component
	public class test implements ApplicationRunner {

		@Override
		public void run(ApplicationArguments args) {
			ExecutorBiz executorBiz = new ExecutorBizImpl();
			int count = 10;
			Thread[] ts = new Thread[count];
			for (int i=0; i<count; i++) {
				final int j = i;
				Thread t = new Thread(() -> {
					TriggerParam triggerParam = new TriggerParam();
					triggerParam.setJobId(j>=5 ? 8 : 2);
					triggerParam.setExecutorHandler("demoJobHandler");
					triggerParam.setExecutorParams(null);
					triggerParam.setExecutorBlockStrategy(j >= 5 ? ExecutorBlockStrategyEnum.CONCURRENT_EXECUTION.getTitle()
							: ExecutorBlockStrategyEnum.SERIAL_EXECUTION.getTitle());
					triggerParam.setExecutorTimeout(0);
					triggerParam.setLogId(55+j);
					triggerParam.setLogDateTime(1618390300000L);
					triggerParam.setGlueType(GlueTypeEnum.BEAN.name());
					triggerParam.setGlueSource(null);
					triggerParam.setGlueUpdatetime(1617706690000L);
					triggerParam.setBroadcastIndex(0);
					triggerParam.setBroadcastTotal(0);

					executorBiz.run(triggerParam);
				});
				ts[i] = t;
			}

			for (Thread t : ts) {
				t.start();
			}
		}
	}

}
