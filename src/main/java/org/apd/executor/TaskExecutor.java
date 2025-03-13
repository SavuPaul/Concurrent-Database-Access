package org.apd.executor;

import org.apd.storage.EntryResult;
import org.apd.storage.SharedDatabase;
import java.util.List;

/* DO NOT MODIFY THE METHODS SIGNATURES */
public class TaskExecutor {
    public final SharedDatabase sharedDatabase;

    public TaskExecutor(int storageSize, int blockSize, long readDuration, long writeDuration) {
        sharedDatabase = new SharedDatabase(storageSize, blockSize, readDuration, writeDuration);
    }

    public List<EntryResult> ExecuteWork(int numberOfThreads, List<StorageTask> tasks, LockType lockType) {
        /* IMPLEMENT HERE THE THREAD POOL, ASSIGN THE TASKS AND RETURN THE RESULTS */
        // Instantiate the threadpool
        ThreadPool threadpool = new ThreadPool(numberOfThreads, tasks.size(), lockType, sharedDatabase);

        // Start the threads
        threadpool.start();

        // Load task queue
        for (StorageTask task : tasks) {
            threadpool.introduceTask(task);
        }

        // Stop the threadpool
        threadpool.stopTP();

        return ThreadPool.getResults();
    }

    public List<EntryResult> ExecuteWorkSerial(List<StorageTask> tasks) {
        var results = tasks.stream().map(task -> {
            try {
                if (task.isWrite()) {
                    return sharedDatabase.addData(task.index(), task.data());
                } else {
                    return sharedDatabase.getData(task.index());
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).toList();

        return results.stream().toList();
    }
}
