package com.company.taskmanagementplatform.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.company.taskmanagementplatform.tasks.dto.CreateTaskRequest;
import com.company.taskmanagementplatform.tasks.dto.TaskResponse;

/**
 * Task numbering under concurrent creation, which is the one race this phase had to design around.
 *
 * <p>Modelled on {@code RefreshTokenConcurrencyIT}, and for the same reason: a rule that only holds
 * when requests arrive one at a time is not a rule, and nothing in a single-threaded test would ever
 * notice. {@code SELECT max(task_number) + 1} passes every test in this file's absence and fails the
 * first time two people press the button together.
 */
class TaskNumberingConcurrencyIT extends TaskApiTestBase {

    private static final int CONCURRENT_CREATORS = 12;

    @Autowired
    private TaskService tasks;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void concurrentCreationProducesEveryNumberOnceWithNoGaps() throws Exception {
        Scenario scenario = scenario();

        List<Integer> allocated = inParallel(scenario, CONCURRENT_CREATORS);

        assertThat(allocated)
                .as("numbers handed out to %d concurrent creators", CONCURRENT_CREATORS)
                .containsExactlyInAnyOrderElementsOf(
                        IntStream.rangeClosed(1, CONCURRENT_CREATORS).boxed().toList());
    }

    @Test
    void everyTaskRowSurvivesRatherThanOneWinningTheUniqueKey() throws Exception {
        // The failure mode a naive implementation shows: several requests read the
        // same maximum, all but one violate the unique key, and the rest are lost.
        Scenario scenario = scenario();

        inParallel(scenario, CONCURRENT_CREATORS);

        Integer stored = jdbc.queryForObject(
                "SELECT count(*) FROM tasks WHERE project_id = ?", Integer.class, scenario.projectId());

        assertThat(stored).isEqualTo(CONCURRENT_CREATORS);
    }

    @Test
    void twoProjectsNumberIndependentlyEvenWhenWrittenTogether() throws Exception {
        // The counter is keyed per project, so contention in one must not serialise
        // or interfere with another.
        Scenario first = scenario();
        Scenario second = scenario();

        List<Integer> mine = inParallel(first, 6);
        List<Integer> theirs = inParallel(second, 6);

        assertThat(mine).containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(1, 6).boxed().toList());
        assertThat(theirs).containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(1, 6).boxed().toList());
    }

    /**
     * Creates {@code count} tasks from {@code count} threads released at the same instant.
     *
     * <p>The latch matters. Without it the pool would start the first task while the last one was
     * still being submitted, and the threads would never actually overlap.
     */
    private List<Integer> inParallel(Scenario scenario, int count) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<Integer>> futures = IntStream.range(0, count)
                    .mapToObj(index -> pool.submit((Callable<Integer>) () -> {
                        start.await();
                        TaskResponse created = tasks.create(
                                scenario.workspaceId(),
                                taskFixtures.context(scenario.workspaceId(), scenario.projectId(), scenario.adminId()),
                                new CreateTaskRequest(
                                        "Concurrent " + index, null, null, null, null, null, null, null, null, null),
                                scenario.adminId());
                        return created.taskNumber();
                    }))
                    .toList();

            start.countDown();

            List<Integer> numbers = new java.util.ArrayList<>();
            for (Future<Integer> future : futures) {
                numbers.add(future.get(30, TimeUnit.SECONDS));
            }
            return numbers;
        } finally {
            pool.shutdownNow();
        }
    }
}
