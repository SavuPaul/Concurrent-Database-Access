# PARALLEL AND DISTRIBUTED ALGORITHMS

## README - Concurrent Database Access

### Conclusions regarding the performance
The parallel implementation for the Readers-Writers problem is more efficient than the serialized one thanks to the threads who are able to execute multiple read/write operations at the same moment of time (but on different indexes). There would be only one scenario where the parallel implementation would be equivalent to the serialized one when it comes down to performance: when all tasks are of type write, and they are all performed on the same index. This would make each writer wait for the one in front, leading to a serialized-like performance. However, even by randomly generating reader-to-writer ratios, the likelihood of this occurring is extremely low, since all tasks would have to modify the same index as well, besides being all writing tasks. By implementing a threadpool, the solution to this problem is modeled more like a real-life scenario, where multiple threads can read data at the same time (even from the same index) or write data (but on different indexes). The threadpool-based solution not only distributes work as equally as possible across all threads, but it is a type of design that can be easily changed to add functionalities such as prioritization (just like ReaderPreferred, WriterPreferred).

### Observations regarding tests
The tests consist of different ratios between readers and writers, with ratios closer to zero meaning more writers than readers. They each have a timeout, established based on the number of tasks in the queue, the `blockSize`, and the time required to execute a read/write operation. The execution time can differ when running the same test because of the function `generateTasks`, which assigns randomized data to the task, including indexes and the order of read/write tasks.

There can be two scenarios that would slow down the execution due to the randomized data when creating tests:
- Multiple and consecutive write tasks on the same index (one thread hasn't finished writing on index x and another one enters a wait state until the other entirely finishes).
- A read task on index x is extracted from the queue before the thread writing on index x has finished.

The wider the interval for task indexes, the lower the chances are for these scenarios to happen. So, therefore, for a large database, these problems would occur less frequently than for a smaller database.

The analysis of the following tests would help in demonstrating the statements mentioned above:

```java
@Test
void executeWorkWriterPreferred12() {
    executeWork(12, 200, 0.7, testValues1, testValues1.length / 2, 255, 100, 250, LockType.WriterPreferred1, 15);
}

@Test
void executeWorkWriterPreferred13() {
    executeWork(8, 200, 0.7, testValues1, testValues1.length * 2, 255, 100, 250, LockType.WriterPreferred1, 15);
}
```

### Execution times
1. **executeWorkWriterPreferred12** - 12.714 seconds
2. **executeWorkWriterPreferred13** - 8.598 seconds

The differences between these tests are that the first one works with 12 threads, while the second one works with 8. It would make more sense for the 12-threaded test to finish faster, but the index ranges are the key factor influencing the execution times. The first test has an index range of `[0,2]`, while the second has `[0,11]`. It can be clearly noticed that the statements mentioned above have a great impact on the execution of the program.

