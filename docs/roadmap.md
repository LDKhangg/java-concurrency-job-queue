# Java Concurrency Job Queue Roadmap

## Current status

- [x] Phase 1: Race condition lab
- [x] Phase 2: In-memory queue + fixed worker pool
- [x] Phase 3: CompletableFuture orchestration
- [x] Phase 4: Backpressure & bounded queue
- [x] Phase 5: Scheduling & priority
- [ ] Phase 6: Virtual threads & structured concurrency
- [ ] Phase 7: Observability
- [ ] Phase 8 (stretch): Persistence & crash recovery
- [ ] Phase 9 (stretch): Distributed queue

## Phase 1: Race Condition Lab

Goal: tai hien lost update va co 1 ban fix ro rang de so sanh.

Issue checklist:

- [x] Viet test cho unsafe counter bi lost update.
- [x] Viet test cho locked counter giu du expected count.
- [x] Dong goi race lab thanh service rieng de de hoc va de doc.

## Phase 2: Queue + Worker Pool

Goal: dua app tu scaffold `PENDING` thanh backend co xu ly nen that su.

Issue checklist:

- [x] Them fixed worker pool duoc config bang properties.
- [x] Dung queue trong memory de worker pull job.
- [x] Cap nhat trang thai job `PENDING -> RUNNING -> SUCCESS/FAILED`.
- [x] Tra metrics worker count qua API.
- [x] Simulation endpoint bom nhieu job va doi ket qua terminal.

## Phase 3: CompletableFuture Orchestration

Goal: cho moi job di qua pipeline nhieu buoc thay vi 1 ham xu ly don.

Issue checklist:

- [x] Tach processing thanh `validate -> process -> enrich -> finalize`.
- [x] Dung `CompletableFuture.supplyAsync` va chaining de compose flow.
- [x] Them timeout cho tung step va toan pipeline.
- [x] Them retry co gioi han cho enrich/process step.
- [x] Viet test cho timeout, exception propagation, retry success/failure.

## Phase 4: Backpressure & Bounded Queue

Goal: hoc cach queue gioi han suc chua tao backpressure, va load lab
producer-consumer hoan chinh (burst, backlog, throughput, graceful shutdown).

Issue checklist:

- [x] Doi `LinkedBlockingQueue` khong gioi han thanh bounded queue theo `app.job-queue.queue-capacity`.
- [x] Them `RejectPolicy` (`BLOCK`/`FAIL_FAST`): BLOCK cho producer cho den khi queue con cho,
      FAIL_FAST nem `QueueFullException` de job nam lai `PENDING`.
- [x] Them config `app.job-queue.enqueue-timeout-ms` cho viec cho queue con cho.
- [x] Simulation them mode `BURST` (producer bom het toc do) de thay backpressure hoat dong.
- [x] Simulation do backlog (queue depth max/avg) va throughput (jobs/sec) trong report.
- [x] Graceful shutdown test: con pending job van duoc drain den terminal state.
- [x] Test bounded queue: capacity, block khi day, FAIL_FAST nem exception.

Bai hoc chinh:

- Unbounded queue = memory bomb khi producer nhanh hon consumer.
- Backpressure la cach cho producer tu dieu tiet toc do theo consumer.
- `RejectPolicy` la quyet dinh kinh doanh: cho doi, tu choi, hay lam mat job.

## Phase 5: Scheduling & Priority

Goal: them kha nang hoan thi job theo thoi gian va theo do uu tien.

Issue checklist:

- [x] Them `delayMs` vao `POST /jobs` de tao delayed job (dung `DelayQueue` cho workers).
- [x] Them `priority` vao `POST /jobs` de uu tien job quan trong hon (dung `PriorityBlockingQueue`).
- [x] Worker pool pull theo thu tu delay -> priority.
- [ ] Metrics cho delayed job dang cho va priority distribution.
- [x] Test: delayed job khong chay truoc deadline, priority job chay truoc.

Bai hoc chinh:

- `DelayQueue` = queue ma phan tu chi "chin" sau mot khoang thoi gian, dispatcher
  chuyen job het han sang queue san sang.
- `PriorityBlockingQueue` = queue co thu tu, higher priority duoc pull truoc,
  kem sequence number de giu FIFO giua cac job cung priority.
- Delayed job khong bi reject khi ready queue day: chung cho o scheduled queue,
  dispatcher se backpressure khi chuyen sang ready queue.
- Ket hop hai queue: mot `DelayQueue` (staging) + mot bounded `PriorityBlockingQueue`
  (san sang) la mo hinh scheduler co ban cua cac job queue that su.

## Phase 6: Virtual Threads & Structured Concurrency

Goal: so sanh mo hinh thread pool voi Java 21 virtual threads, va hoc
`StructuredTaskScope` de quan ly fan-out trong pipeline.

Issue checklist:

- [ ] Worker pool chuyen sang virtual threads (`Executors.newVirtualThreadPerTaskExecutor`).
- [ ] Pipeline dung `StructuredTaskScope` cho cac step co the chay song song.
- [ ] So sanh throughput va resource usage giua platform threads va virtual threads (qua simulation).
- [ ] Test: virtual thread workers xu ly dung so job, pipeline fan-out dung ket qua.

## Phase 7: Observability

Goal: lam queue co the quan sat duoc nhu production: metrics thoi gian thuc va report.

Issue checklist:

- [ ] Tich hop Micrometer + actuator: gauge queue depth, running jobs, backlog.
- [ ] Histogram cho job latency va pipeline step duration.
- [ ] Endpoint report tong hop (backlog theo thoi diem, failed ratio, p95 latency).
- [ ] Test: metrics cap nhat khi job chay, report phan anh trang thai hien tai.

## Phase 8 (stretch): Persistence & Crash Recovery

Goal: hoc the nao queue song sot qua restart voi at-least-once delivery.

Issue checklist:

- [ ] Luu job vao H2 embedded thay vi `ConcurrentHashMap`.
- [ ] Worker claim job voi `RUNNING` + heartbeat de phat hien worker chet.
- [ ] Recovery: khi app restart, job `RUNNING` lau ngay duoc requeue lai.
- [ ] At-least-once: xu ly trung lap bang idempotency key.
- [ ] Test: restart giua chung khong mat job, khong chay 2 lan cung luc.

## Phase 9 (stretch): Distributed Queue

Goal: hoc competing consumers va queue phan tan voi embedded Redis Streams
(khong can Docker).

Issue checklist:

- [ ] Dung embedded Redis trong test scope.
- [ ] `XADD` de push job, consumer groups de chia job cho nhieu consumer.
- [ ] DLQ: job fail nhieu lan duoc dua vao stream rieng `dlq`.
- [ ] So sanh voi single-node queue: throughput, ordering, failover behavior.
- [ ] Test: 2 consumer khong xu ly cung 1 job, pending entries sau consumer chet.