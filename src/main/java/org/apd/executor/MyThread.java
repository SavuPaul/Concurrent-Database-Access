package org.apd.executor;

import java.util.concurrent.Semaphore;

public class MyThread implements Runnable {
    private final LockType locktype;

    // Semaphore for ReadersPreferred - changing number of readers
    private static final Semaphore semNumberOfReaders = new Semaphore(1);

    // Semaphores for WriterPreferred1
    private static final Semaphore enter = new Semaphore(1);

    public MyThread(LockType locktype) {
        this.locktype = locktype;
    }

    @Override
    public void run() {
        while (true) {
            if (ThreadPool.wait.get() == 0) {
                break;
            }
        }
        while (!ThreadPool.getStopped()) {
            StorageTask task;
            // Comment the next line for no output (this is used to track the size of the tasks queue)
            System.out.println(ThreadPool.tasks.size());
            try {
                task = ThreadPool.getTasks().take();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            if (ThreadPool.getTasks().isEmpty()) {
                ThreadPool.setStopped(true);
            }

            if (this.locktype.equals(LockType.ReaderPreferred)) {
                solveReaderPreferred(task);
            } else if (this.locktype.equals(LockType.WriterPreferred1)) {
                solveWriterPreferred1(task);
            } else if (this.locktype.equals(LockType.WriterPreferred2)) {
                solveWriterPreferred2(task);
            }
        }
    }

    // TASK 1
    private void solveReaderPreferred(StorageTask task) {
        try {
            if (!task.isWrite()) {
                semNumberOfReaders.acquire();

                // Add a reader at the task index
                ThreadPool.addReader(task.index());

                // If there is a reader at this index, "lock" the index so writers can't get in
                if (ThreadPool.getReaderCount(task.index()) == 1) {
                    ThreadPool.semIndex.get(task.index()).acquire();
                }

                semNumberOfReaders.release();

                ThreadPool.getResults().add(ThreadPool.getSharedDatabase().getData(task.index()));

                semNumberOfReaders.acquire();

                // Subtract a reader from the task index
                ThreadPool.removeReader(task.index());

                // If there are no more readers at this index, "unlock" the index so writers can get in
                if (ThreadPool.getReaderCount(task.index()) == 0) {
                    ThreadPool.semIndex.get(task.index()).release();
                }

                semNumberOfReaders.release();
            } else {
                // Acquire and release the index semaphore
                ThreadPool.semIndex.get(task.index()).acquire();
                ThreadPool.getResults().add(ThreadPool.getSharedDatabase().addData(task.index(), task.data()));
                ThreadPool.semIndex.get(task.index()).release();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // TASK 2
    private void solveWriterPreferred1(StorageTask task) {
        try {
            if (!task.isWrite()) {
                enter.acquire();

                if (ThreadPool.getWriterCount(task.index()) > 0 || ThreadPool.getWaitingWriterCount(task.index()) > 0) {
                    ThreadPool.addWaitingReader(task.index());
                    enter.release();
                    ThreadPool.semReadIndex.get(task.index()).acquire();
                }

                ThreadPool.addReader(task.index());

                if (ThreadPool.getWaitingReaderCount(task.index()) > 0) {
                    ThreadPool.removeWaitingReader(task.index());
                    ThreadPool.semReadIndex.get(task.index()).release();
                } else if (ThreadPool.getWaitingReaderCount(task.index()) == 0) {
                    enter.release();
                }

                ThreadPool.getResults().add(ThreadPool.getSharedDatabase().getData(task.index()));
                enter.acquire();
                ThreadPool.removeReader(task.index());

                if (ThreadPool.getReaderCount(task.index()) == 0 && ThreadPool.getWaitingWriterCount(task.index()) > 0) {
                    ThreadPool.removeWaitingWriter(task.index());
                    ThreadPool.semWriteIndex.get(task.index()).release();
                } else if (ThreadPool.getReaderCount(task.index()) > 0 || ThreadPool.getWaitingWriterCount(task.index()) == 0) {
                    enter.release();
                }
            } else {
                enter.acquire();

                // || ThreadPool.semIndex.get(task.index()).availablePermits() == 0
                if (ThreadPool.getReaderCount(task.index()) > 0 || ThreadPool.getWriterCount(task.index()) > 0) {
                    ThreadPool.addWaitingWriter(task.index());
                    enter.release();
                    ThreadPool.semWriteIndex.get(task.index()).acquire();
                }

                ThreadPool.addWriter(task.index());

                enter.release();

                ThreadPool.getResults().add(ThreadPool.getSharedDatabase().addData(task.index(), task.data()));

                enter.acquire();

                ThreadPool.removeWriter(task.index());

                if (ThreadPool.getWaitingReaderCount(task.index()) > 0 && ThreadPool.getWaitingWriterCount(task.index()) == 0) {
                    ThreadPool.removeWaitingReader(task.index());
                    ThreadPool.semReadIndex.get(task.index()).release();
                } else if (ThreadPool.getWaitingWriterCount(task.index()) > 0) {
                    ThreadPool.removeWaitingWriter(task.index());
                    ThreadPool.semWriteIndex.get(task.index()).release();
                } else if (ThreadPool.getWaitingReaderCount(task.index()) == 0 && ThreadPool.getWaitingWriterCount(task.index()) == 0) {
                    enter.release();
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // TASK 3
    private void solveWriterPreferred2(StorageTask task) {
        try {
            if (!task.isWrite()) {
                enter.acquire();

                if (ThreadPool.getWriterCount(task.index()) > 0 || ThreadPool.getWaitingWriterCount(task.index()) > 0) {
                    ThreadPool.addWaitingReader(task.index());
                    enter.release();
                    synchronized(ThreadPool.readerLocks.get(task.index())) {
                        ThreadPool.readerLocks.get(task.index()).wait();
                    }
                }

                ThreadPool.addReader(task.index());

                if (ThreadPool.getWaitingReaderCount(task.index()) > 0) {
                    ThreadPool.removeWaitingReader(task.index());
                    synchronized(ThreadPool.readerLocks.get(task.index())) {
                        ThreadPool.readerLocks.get(task.index()).notify();
                    }
                } else if (ThreadPool.getWaitingReaderCount(task.index()) == 0) {
                    enter.release();
                }

                ThreadPool.getResults().add(ThreadPool.getSharedDatabase().getData(task.index()));
                enter.acquire();
                ThreadPool.removeReader(task.index());

                if (ThreadPool.getReaderCount(task.index()) == 0 && ThreadPool.getWaitingWriterCount(task.index()) > 0) {
                    ThreadPool.removeWaitingWriter(task.index());
                    synchronized(ThreadPool.writerLocks.get(task.index())) {
                        ThreadPool.writerLocks.get(task.index()).notify();
                    }
                } else if (ThreadPool.getReaderCount(task.index()) > 0 || ThreadPool.getWaitingWriterCount(task.index()) == 0) {
                    enter.release();
                }
            } else {
                enter.acquire();

                // || ThreadPool.semIndex.get(task.index()).availablePermits() == 0
                if (ThreadPool.getReaderCount(task.index()) > 0 || ThreadPool.getWriterCount(task.index()) > 0) {
                    ThreadPool.addWaitingWriter(task.index());
                    enter.release();
                    synchronized(ThreadPool.writerLocks.get(task.index())) {
                        ThreadPool.writerLocks.get(task.index()).wait();
                    }
                }

                ThreadPool.addWriter(task.index());

                enter.release();

                ThreadPool.getResults().add(ThreadPool.getSharedDatabase().addData(task.index(), task.data()));

                enter.acquire();

                ThreadPool.removeWriter(task.index());

                if (ThreadPool.getWaitingReaderCount(task.index()) > 0 && ThreadPool.getWaitingWriterCount(task.index()) == 0) {
                    ThreadPool.removeWaitingReader(task.index());
                    synchronized(ThreadPool.readerLocks.get(task.index())) {
                        ThreadPool.readerLocks.get(task.index()).notify();
                    }
                } else if (ThreadPool.getWaitingWriterCount(task.index()) > 0) {
                    ThreadPool.removeWaitingWriter(task.index());
                    synchronized(ThreadPool.writerLocks.get(task.index())) {
                        ThreadPool.writerLocks.get(task.index()).notify();
                    }
                } else if (ThreadPool.getWaitingReaderCount(task.index()) == 0 && ThreadPool.getWaitingWriterCount(task.index()) == 0) {
                    enter.release();
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}