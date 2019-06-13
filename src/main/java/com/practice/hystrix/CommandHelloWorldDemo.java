package com.practice.hystrix;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixThreadPoolKey;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import rx.Observable;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * @author Luo Bao Ding
 * @since 2018/12/7
 */
@SpringBootApplication
public class CommandHelloWorldDemo {

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(CommandHelloWorldDemo.class).web(WebApplicationType.NONE).build().run(args);
        CommandHelloWorldDemo demo = context.getBean(CommandHelloWorldDemo.class);
        demo.demo();
    }


    private final ThreadPoolTaskExecutor threadPoolTaskExecutor;

    public CommandHelloWorldDemo(ThreadPoolTaskExecutor threadPoolTaskExecutor) {
        this.threadPoolTaskExecutor = threadPoolTaskExecutor;
    }

    public void demo() throws InterruptedException, ExecutionException {
        CommandService commandService = new CommandService();
        commandService.executeCommand();
        commandService.queueCommand();
        commandService.observeCommand();

        for (int i = 0; i < 1000; i++) {
            Thread.sleep(100);
            threadPoolTaskExecutor.execute(commandService::observeCommand);
            threadPoolTaskExecutor.execute(() -> {
                try {
                    commandService.queueCommand();
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private static class CommandService {
        private void observeCommand() {
            for (int i = 0; i < 10; i++) {
                Observable<String> observe = new HelloCommand("Bob").observe();
                observe.subscribe(item -> {
                    System.out.println("item = " + item);
                });
            }
        }

        private void queueCommand() throws InterruptedException, ExecutionException {
            for (int i = 0; i < 10; i++) {
                Future<String> future = new HelloCommand("Bob").queue();
                System.out.println("future.get() = " + future.get());
            }
        }

        private void executeCommand() {
            String greeting = new HelloCommand("Bob").execute();
            System.out.println("greeting = " + greeting);
        }
    }

    private static class HelloCommand extends HystrixCommand<String> {
        private final String username;

        public HelloCommand(String username) {
            super(setter());
            this.username = username;
        }

        private static Setter setter() {
            HystrixCommandProperties.Setter cmdSetter = HystrixCommandProperties.Setter().withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.SEMAPHORE)
                    .withExecutionIsolationSemaphoreMaxConcurrentRequests(10)
                    .withFallbackIsolationSemaphoreMaxConcurrentRequests(10);
            return Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("MyExample-HystrixCommandGroup"))
                    .andCommandPropertiesDefaults(cmdSetter)
                    .andThreadPoolKey(HystrixThreadPoolKey.Factory.asKey("MyExample-HystrixThreadPool"));
        }

        @Override
        protected String run() throws Exception {
            Thread thread = Thread.currentThread();
            String threadGroupName = thread.getThreadGroup().getName();
            String threadName = thread.getName();
            System.out.println("thread group: " + threadGroupName + ",thread: " + threadName);
            Thread.sleep(30);
            return "hello, " + username;
        }

    }


}
