# Tradeoffs

## PostgreSQL vs In-Memory

PostgreSQL was chosen because the assignment requires persistence across restart. An in-memory model would be much easier to implement, but it would lose task state immediately when the process exits.

## Fixed Thread Pool vs Unlimited Threads

A fixed thread pool keeps concurrency predictable and ensures that the service does not exhaust system resources. The downside is that tasks may queue when the worker pool is full.

## FIFO vs Priority Scheduling

FIFO is simpler and easier to reason about. It is also deterministic. The downside is that a long-running task can delay shorter tasks that are ready later.

## Retry vs Immediate Failure

Retries help with transient problems but consume capacity and increase time-to-completion. A retry policy is valuable here because the assignment explicitly asks for configurable retries and backoff.

## At-Least-Once vs Exactly-Once

The project uses at-least-once execution for recovered `RUNNING` tasks. This is a pragmatic choice because exactly-once semantics are expensive and complex when tasks have external side effects. The assignment states that the simulated work has no external side effect, so this is acceptable.

## Polling vs Event Driven

The scheduler uses a polling loop with a short interval. This is straightforward to implement and understand in a take-home project. A production distributed system could use a message broker or event stream to improve responsiveness.

## Simulated Business Work vs Real OCR

The task work is intentionally simulated so the project can focus on orchestration rules instead of external services, credentials, and cloud integrations. Real OCR, malware scanning, or AI APIs would add complexity without improving the core learning objective.
