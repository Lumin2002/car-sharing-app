package cn.ff26710.carsharingapp.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
public class TransactionUtil {
    private static final ThreadPoolExecutor ASYNC_EXECUTOR = new ThreadPoolExecutor(
            1, 1,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(2000),
            r -> new Thread(r, "mq-after-commit-thread"),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    public static void afterCommitAsync(Runnable callback) {
        afterCommit(() -> ASYNC_EXECUTOR.execute(() -> {
            try {
                callback.run();
            } catch (Exception e) {
                log.error("事务提交后异步任务执行失败", e);
            }
        }));
    }

    public static void afterCommit(Runnable callback) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            callback.run();
                        }
                    }
            );
        } else {
            callback.run();
        }
    }
}
