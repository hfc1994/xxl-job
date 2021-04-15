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

//	@Component
	public class test implements ApplicationRunner {

		@Override
		public void run(ApplicationArguments args) throws Exception {
			ExecutorBiz executorBiz = new ExecutorBizImpl();
			Thread[] ts = new Thread[3];
			for (int i=0; i<3; i++) {
				final int j = i;
				Thread t = new Thread(() -> {
					TriggerParam triggerParam = new TriggerParam();
					triggerParam.setJobId(2);
					triggerParam.setExecutorHandler("demoJobHandler");
					triggerParam.setExecutorParams(null);
					triggerParam.setExecutorBlockStrategy(ExecutorBlockStrategyEnum.SERIAL_EXECUTION.getTitle());
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
