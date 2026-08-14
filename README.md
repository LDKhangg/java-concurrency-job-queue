# Java Concurrency Job Queue

Mini Spring Boot backend de hoc concurrency trong Java theo huong job queue backend.

## Hien trang

- `POST /jobs`: tao job moi, luu `PENDING`, roi dua vao queue xu ly nen.
- `GET /jobs/{id}`: xem trang thai `PENDING`, `RUNNING`, `SUCCESS`, `FAILED` va `errorMessage` neu co.
- `GET /workers/metrics`: xem tong so job theo trang thai va so worker dang duoc cap.
- `POST /simulation/run`: tao nhieu producer de bom job, doi den khi tat ca ve terminal state, roi tra summary.
- `RaceConditionLabService`: bo test minh hoa lost update va fix bang `ReentrantLock`.
- `JobOrchestrationService`: pipeline `validate -> process -> enrich -> finalize` dung `CompletableFuture`.

## Learning milestones da co

- Milestone 1: race condition lab bang test.
- Milestone 2: worker pool + queue + failure handling co ban.
- Milestone 3: pipeline bat dong bo voi step timeout, pipeline timeout, retry co gioi han.
- Backlog tiep theo: `docs/roadmap.md`.

## Test scenarios dang co

- Happy path job chay den `SUCCESS`.
- Process step fail va enrich step fail.
- Step timeout va whole-pipeline timeout.
- Retry thanh cong cho `flaky-enrich`.
- Simulation voi nhieu producer.
- Race-condition lab cho unsafe counter va locked counter.

## Config chinh

Trong `src/main/resources/application.properties`:

- `app.job-queue.worker-count=2`
- `app.job-queue.default-worker-delay-ms=50`
- `app.job-queue.step-timeout-ms=150`
- `app.job-queue.pipeline-timeout-ms=500`
- `app.job-queue.retry-count=1`

## Run locally

```bash
./gradlew bootRun
```

## Run tests

```bash
./gradlew test
```
