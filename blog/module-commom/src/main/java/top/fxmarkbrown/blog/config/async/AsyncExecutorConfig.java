package top.fxmarkbrown.blog.config.async;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;

@Slf4j
@Configuration
@EnableAsync
public class AsyncExecutorConfig implements AsyncConfigurer {

    @Bean("blogAsyncExecutor")
    public Executor blogAsyncExecutor() {
        return createVirtualExecutor("blog-async-");
    }

    @Bean("ragTaskExecutor")
    public Executor ragTaskExecutor() {
        return createVirtualExecutor("rag-async-");
    }

    @Override
    public Executor getAsyncExecutor() {
        return blogAsyncExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new LoggingAsyncUncaughtExceptionHandler();
    }

    private Executor createVirtualExecutor(String threadNamePrefix) {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor(threadNamePrefix);
        executor.setVirtualThreads(true);
        executor.setTaskTerminationTimeout(10_000L);
        executor.setCancelRemainingTasksOnClose(false);
        return executor;
    }

    private static final class LoggingAsyncUncaughtExceptionHandler implements AsyncUncaughtExceptionHandler {

        @Override
        public void handleUncaughtException(@NonNull Throwable ex, @NonNull Method method, Object @Nullable ... params) {
            log.error("异步任务执行失败, method={}", method.getName(), ex);
        }
    }
}
