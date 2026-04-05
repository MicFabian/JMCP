package com.example.javamcp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.core.task.support.TaskExecutorAdapter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration(proxyBeanMethods = false)
public class AsyncConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService virtualThreadExecutorService() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    public ContextPropagatingTaskDecorator contextPropagatingTaskDecorator() {
        return new ContextPropagatingTaskDecorator();
    }

    @Bean
    public AsyncTaskExecutor asyncTaskExecutor(ExecutorService virtualThreadExecutorService,
                                               ContextPropagatingTaskDecorator taskDecorator) {
        TaskExecutorAdapter adapter = new TaskExecutorAdapter(virtualThreadExecutorService);
        adapter.setTaskDecorator(taskDecorator);
        return adapter;
    }
}
