package org.apd.executor;

import org.apd.storage.EntryResult;
import org.apd.storage.SharedDatabase;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadPool {
    private static int numberOfTasks;
    public static BlockingQueue<StorageTask> tasks;
    private final int numberOfThreads;
    private final List<Thread> threads;
    private static LockType locktype;
    private static SharedDatabase sharedDatabase;
    private static boolean isStopped;
    // HashMap with semaphores for each index in the array
    // ReaderPreferred
    public static ConcurrentHashMap<Integer, Semaphore> semIndex;
    // WriterPreferred1
    public static ConcurrentHashMap<Integer, Semaphore> semWriteIndex;
    public static ConcurrentHashMap<Integer, Semaphore> semReadIndex;
    // Readers variable for ReaderPreferred
    public static ConcurrentHashMap<Integer, Integer> readersRP;
    // Variables for WriterPreferred1
    public static ConcurrentHashMap<Integer, Integer> writersWP1;
    public static ConcurrentHashMap<Integer, Integer> waitingWritersWP1;
    public static ConcurrentHashMap<Integer, Integer> readersWP1;
    public static ConcurrentHashMap<Integer, Integer> waitingReadersWP1;
    // Variables for WriterPreferred2
    public static ConcurrentHashMap<Integer, Object> readerLocks;
    public static ConcurrentHashMap<Integer, Object> writerLocks;
    public static AtomicInteger atomicSemaphore;
    // Results
    private static List<EntryResult> results;
    public static AtomicInteger wait;

    public ThreadPool(int numberOfThreads, int noOfTasks, LockType lockType, SharedDatabase sharedDB) {
        tasks = new ArrayBlockingQueue<>(noOfTasks);
        results = Collections.synchronizedList(new ArrayList<>());
        threads = new LinkedList<>();
        isStopped = false;
        sharedDatabase = sharedDB;
        locktype = lockType;
        numberOfTasks = noOfTasks;
        semIndex = new ConcurrentHashMap<>();
        semWriteIndex = new ConcurrentHashMap<>();
        semReadIndex = new ConcurrentHashMap<>();
        readersRP = new ConcurrentHashMap<>();
        writersWP1 = new ConcurrentHashMap<>();
        waitingWritersWP1 = new ConcurrentHashMap<>();
        readersWP1 = new ConcurrentHashMap<>();
        waitingReadersWP1 = new ConcurrentHashMap<>();
        readerLocks = new ConcurrentHashMap<>();
        writerLocks = new ConcurrentHashMap<>();
        atomicSemaphore = new AtomicInteger(1);
        this.numberOfThreads = numberOfThreads;
        wait = new AtomicInteger(numberOfTasks);
    }

    public void start() {
        // Create threads
        for (int i = 0; i < numberOfThreads; i++) {
            Thread thread = new Thread(new MyThread(locktype));
            threads.add(thread);
        }

        // Start them
        for (Thread thread : threads) {
            thread.start();
        }
    }

    public void introduceTask(StorageTask task) {
        // ReaderPreferred
        readersRP.put(task.index(), 0);
        // Semaphore for ReaderPreferred
        semIndex.put(task.index(), new Semaphore(1));

        // WriterPreferred1
        readersWP1.put(task.index(), 0);
        waitingReadersWP1.put(task.index(), 0);
        writersWP1.put(task.index(), 0);
        waitingWritersWP1.put(task.index(), 0);
        // Semaphores for WriterPreferred1
        semWriteIndex.put(task.index(), new Semaphore(0));
        semReadIndex.put(task.index(), new Semaphore(0));

        // Locks for WriterPreferred2
        readerLocks.put(task.index(), new Object());
        writerLocks.put(task.index(), new Object());

        tasks.add(task);
        wait.decrementAndGet();
    }

    public void stopTP() {
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    // Increment the number of waiting readers at a specific index
    public static void addWaitingReader(int index) {
        waitingReadersWP1.compute(index, (key, value) -> {
            if (value == null) {
                return 1;
            } else {
                return value + 1;
            }
        });
    }

    // Increment the number of readers at a specific index
    public static void addReader(int index) {
        readersRP.compute(index, (key, value) -> {
            if (value == null) {
                return 1;
            } else {
                return value + 1;
            }
        });
    }

    // Increment the number of waiting writers at a specific index
    public static void addWaitingWriter(int index) {
        waitingWritersWP1.compute(index, (key, value) -> {
            if (value == null) {
                return 1;
            } else {
                return value + 1;
            }
        });
    }

    // Increment the number of writers at a specific index
    public static void addWriter(int index) {
        writersWP1.compute(index, (key, value) -> {
            if (value == null) {
                return 1;
            } else {
                return value + 1;
            }
        });
    }

    // Decrement the number of waiting readers at a specific index
    public static void removeWaitingReader(int index) {
        waitingReadersWP1.compute(index, (key, value) -> {
            if (value == null) {
                return 0;
            } else {
                return value - 1;
            }
        });
    }

    // Decrement the number of readers at a specific index
    public static void removeReader(int index) {
        readersRP.compute(index, (key, value) -> {
            if (value == null) {
                return 0;
            } else {
                return value - 1;
            }
        });
    }

    // Decrement the number of writers at a specific index
    public static void removeWaitingWriter(int index) {
        waitingWritersWP1.compute(index, (key, value) -> {
            if (value == null) {
                return 0;
            } else {
                return value - 1;
            }
        });
    }

    // Decrement the number of writers at a specific index
    public static void removeWriter(int index) {
        writersWP1.compute(index, (key, value) -> {
            if (value == null) {
                return 0;
            } else {
                return value - 1;
            }
        });
    }

    // Returns the number of waiting readers at a certain index
    public static int getWaitingReaderCount(int index) {
        return waitingReadersWP1.get(index);
    }

    // Returns the number of readers at a certain index
    public static int getReaderCount(int index) {
        return readersRP.get(index);
    }

    // Returns the number of waiting writers at a certain index
    public static int getWaitingWriterCount(int index) {
        return waitingWritersWP1.get(index);
    }

    // Returns the number of writers at a certain index
    public static int getWriterCount(int index) {
        return writersWP1.get(index);
    }

    public static BlockingQueue<StorageTask> getTasks() {
        return tasks;
    }

    public static List<EntryResult> getResults() {
        return results;
    }

    public static boolean getStopped() {
        return isStopped;
    }

    public static SharedDatabase getSharedDatabase() {
        return sharedDatabase;
    }

    public static void setStopped(boolean status) {
        isStopped = status;
    }
}
