# Java Concurrency Job Queue

Mini Spring Boot backend de hoc concurrency trong Java theo huong job queue backend.

## Hien trang

- `POST /jobs`: tao job moi, luu `PENDING`, roi dua vao queue xu ly nen. Ho tro `priority` (mac dinh 5, cao hon chay truoc) va `delayMs` (job chi san sang sau khoang thoi gian).
- `GET /jobs/{id}`: xem trang thai `PENDING`, `RUNNING`, `SUCCESS`, `FAILED` va `errorMessage` neu co.
- `GET /workers/metrics`: xem tong so job theo trang thai, so worker dang duoc cap va do sau queue (backlog).
- `POST /simulation/run`: tao nhieu producer de bom job, doi den khi tat ca ve terminal state, tra summary (co mode `BURST`, backlog max/avg, throughput).
- `RaceConditionLabService`: bo test minh hoa lost update va fix bang `ReentrantLock`.
- `JobOrchestrationService`: pipeline `validate -> process -> enrich -> finalize` dung `CompletableFuture`.
- `JobProcessingService`: scheduler hai tang — `DelayQueue` cho delayed job + bounded `PriorityBlockingQueue` cho job san sang, `RejectPolicy` `BLOCK`/`FAIL_FAST`, graceful shutdown drain pending job.

## Learning milestones da co

- Milestone 1: race condition lab bang test.
- Milestone 2: worker pool + queue + failure handling co ban.
- Milestone 3: pipeline bat dong bo voi step timeout, pipeline timeout, retry co gioi han.
- Milestone 4: backpressure voi bounded queue + reject policy + load lab (burst, backlog, throughput, graceful shutdown).
- Milestone 5: scheduling — `delayMs` hoan thi job theo thoi gian (`DelayQueue` + dispatcher), `priority` uu tien job (`PriorityBlockingQueue`, FIFO giua cung priority).
- Backlog tiep theo: `docs/roadmap.md` (virtual threads, observability, persistence, distributed).

## Test scenarios dang co

- Happy path job chay den `SUCCESS`.
- Process step fail va enrich step fail.
- Step timeout va whole-pipeline timeout.
- Retry thanh cong cho `flaky-enrich`.
- Simulation voi nhieu producer, mode `BURST`, backlog va throughput.
- Backpressure: queue day thi producer bi block hoac bi reject (`FAIL_FAST`).
- Graceful shutdown: pending job van duoc drain den terminal state.
- Delayed job khong chay truoc deadline, khong bi reject khi ready queue day.
- Priority job chay truoc job thap uu tien, cung priority thi FIFO.
- Race-condition lab cho unsafe counter va locked counter.

## Config chinh

Trong `src/main/resources/application.properties`:

- `app.job-queue.worker-count=2`
- `app.job-queue.default-worker-delay-ms=50`
- `app.job-queue.step-timeout-ms=150`
- `app.job-queue.pipeline-timeout-ms=500`
- `app.job-queue.retry-count=1`
- `app.job-queue.queue-capacity=10`
- `app.job-queue.reject-policy=BLOCK` (`BLOCK`/`FAIL_FAST`)
- `app.job-queue.enqueue-timeout-ms=5000`

## Run locally

```bash
./gradlew bootRun
```

## Run tests

```bash
./gradlew test
```
