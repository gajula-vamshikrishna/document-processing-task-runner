# Design

## 1. Problem

The assignment is to build a reliable task runner that runs simulated document-processing jobs while respecting task dependencies, concurrency limits, retries, failure propagation, cancellation, persistence, and restart recovery.

## 2. Real-World Scenario

A document arrives as an employee expense receipt or invoice. It goes through validation, data extraction, classification, validation of extracted data, JSON generation, and final persistence. The orchestration engine is the key focus; the actual business work is simulated.

## 3. Workflow

```text
Validate
  |
  +--> Extract
  |
  +--> Classify
          |
          v
    Validate Data
          |
          v
    Generate JSON
          |
          v
      Store Result
```

## 4. Architecture

The system is intentionally simple:

- Controllers expose HTTP endpoints for workflow submission, status, cancellation, and statistics.
- Services validate workflows, manage task lifecycle, evaluate dependencies, and enforce cancellation/recovery rules.
- A scheduler polls for ready tasks and admits only tasks that are eligible and within the concurrency budget.
- A fixed-size thread pool executes tasks.
- The persistence layer stores tasks, dependencies, and attempts in the database.
- Dependency graphs enforce ordering and cycle prevention.
- Retry policy implements exponential backoff.

## 5. Concurrency Limit

The concurrency limit is guaranteed by the fixed-size executor and a centralized admission check. The configured `max-concurrency` value is applied to the executor thread pool and also checked when a task is admitted into the RUNNING state. A task is only submitted when the scheduler confirms there is capacity.

In other words:

- N = configured worker count
- only admitted tasks are submitted
- RUNNING count cannot exceed worker capacity
- tasks are only admitted once they are eligible and not terminal

This prevents runaway thread creation and keeps the scheduler easy to reason about.

## 6. Restart Behavior

On application startup, all `RUNNING` tasks are reset to `WAITING` and requeued. This is a deliberate at-least-once recovery policy because the process may have been interrupted mid-execution. For simulated tasks this is acceptable and safe, but real-world side effects should use idempotency keys and checkpoints.

## 7. Scheduling Rule

The scheduler uses a FIFO approach among ready tasks: older tasks are considered first. This keeps the implementation predictable and simple. A downside is that a long-running task can delay shorter tasks that become ready later.

## 8. Invariant

A task must never execute unless all dependencies are `SUCCEEDED`.

The invariant is enforced centrally in the scheduler and before task admission. The scheduler asks whether every dependency is successful before moving a task from `WAITING` or `RETRY_WAITING` to `RUNNING`.

## 9. Retry Design

The retry policy uses exponential backoff. A failed task is moved to `RETRY_WAITING` with a computed next run time. Delay grows according to:

`delay = initialDelay * multiplier^(attempt - 1)`

This keeps transient failures from triggering excessive churn while still making re-attempts measurable.

## 10. Failure Propagation

When a dependency permanently fails, all downstream tasks are evaluated and moved to `BLOCKED` if any required parent is failed, blocked, or cancelled. This propagates through the workflow and prevents tasks from waiting forever.

## 11. Cancellation

A waiting or retry-waiting task can be cancelled. A running task can be marked as cancelled when the system chooses to stop scheduling it. Any dependent tasks that require the cancelled task become `BLOCKED` and will not run again.

## 12. Timeout

Execution timeout prevents a worker from being held forever in the case of a hung or overly long simulated task. The task is treated as failed if the simulated work takes longer than the configured window. This preserves concurrency availability and keeps the scheduler responsive.

## 13. Persistence

All task state and dependency relations are stored in PostgreSQL-compatible JPA entities. This avoids losing scheduling state across application restarts. The default local configuration uses H2 for test portability, but the application is designed for PostgreSQL in deployed environments.

## 14. Thread Safety

Race conditions are reduced by:

- a fixed worker pool
- a single scheduler admission path
- optimistic state transitions in the task repository
- narrow transactional updates
- checks that reject terminal or blocked tasks before they can run again

## 15. Failure Scenarios

The engine handles several operational scenarios:

- dependency failure -> downstream tasks become `BLOCKED`
- restart -> `RUNNING` tasks become `WAITING`
- timeout -> task attempt fails and can retry
- cancellation -> task cannot rerun
- invalid dependency -> workflow rejected
- cycle -> workflow rejected at submission time
- database restart -> service recovers state from persistence

## 16. Production Evolution

This project intentionally simulates OCR, malware scanning, and external service calls. The same orchestration engine could later replace simulated tasks with real OCR, document intelligence, malware scanning, or outbound APIs without changing the scheduler logic itself.
