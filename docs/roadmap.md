# Java Concurrency Job Queue — Learning Roadmap

Repo hoc concurrency trong Java qua viec xay dung mot job queue backend tu scaffold.
Moi phase giai quyet MOT van de concurrency cu the. Doc ky **Goal** (tai sao), lau **Requirements**
(hành vi phai dat), tham khao **Cach lam** (khái niem + API, khong phai code), va tick **Checklist**.

> Nguyen tac: viet test truoc khi viet code. Moi checklist item = mot commit nho.

## Phase 1: Race Condition Lab

**Goal:** tai hien lost update bang test de THAY bang mat race condition la gi, vi sao
`count++` khong an toan trong da luong, va dieu gi lam no bien mat. Day la nen tang de hieu
vi sao moi thu tu sau can toi thread-safe.

**Requirements:**
- [ ] Co mot service chua unsafe counter (`int count++`) va mot locked counter (cung bai toan).
- [ ] Chay N thread, moi thread tang counter M lan:
  - unsafe counter cho ket qua **nho hon** N * M (bi lost update) — dung moi lan chay deu nho hon,
    khong chi thinh thoang.
  - locked counter luon = N * M.
- [ ] Ket qua duoc bao cao ra (vi du report object) de thay con so lost update cu the.

**Cach lam:**
- `Executors.newFixedThreadPool(N)` de tao N worker thread; moi thread chay mot vong lap `M` lan `count++`.
- `CountDownLatch` (hoac `CyclicBarrier`) de dam bao moi thread BAT DAU cung luc va thread chinh
  cho tat ca KET THUC truoc khi assert.
- Vong lap `count++` khong atomic: no la 3 buoc (doc -> cong -> ghi), luong nay chen luong kia thi mat buoc cong.
- `ReentrantLock.lock()` / `unlock()` (hoac `synchronized`) quanh toan bo vong lap = khong con chen.
- Race la non-deterministic: chay lai nhieu lan (hoac `@RepeatedTest`) va tang N, M de tang xac suat
  xuat hien lost update.
- `AtomicInteger` cung la mot cach fix, nhung lab nay nen dung lock de hieu ro khai niem critical section.

## Phase 2: Queue + Worker Pool

**Goal:** job khong duoc xu ly ngay trong HTTP request thread — request se block, latency cao, va
server khong chiu noi load. Thay vao do: request chi ghi job vao hang doi (PENDING) va tra ve ngay;
mot nhom worker thread chay nen pull job ra va xu ly. Day la mo hinh producer-consumer co ban nhat.

**Requirements:**
- [ ] `POST /jobs` tra ve ngay jobId, job o trang thai `PENDING`.
- [ ] Worker pool co so luong cau hinh duoc (properties `app.job-queue.worker-count`).
- [ ] Worker pull job tu queue, cap nhat trang thai dung: `PENDING -> RUNNING -> SUCCESS/FAILED`.
- [ ] `GET /jobs/{id}` phan anh trang thai hien tai (vua gui xong van `PENDING`, luc dang chay la `RUNNING`...).
- [ ] `GET /workers/metrics` tra so luong worker dang su dung.
- [ ] `POST /simulation/run` tao nhieu producer bom job, cho den khi tat ca ve terminal state,
      tra summary (tong, thanh cong, that bai).
- [ ] Khi app tat, worker pool duoc shutdown sach (khong con thread luong co).

**Cach lam:**
- `BlockingQueue` duoc chia se giua cac thread — `LinkedBlockingQueue` la diem bat dau tot
  (luu y: khong gioi han o phase nay, phase 4 se sua).
- `Executors.newFixedThreadPool(workerCount)` + `@PostConstruct` de khoi dong, `@PreDestroy`
  de shutdown. Nho: `ThreadPoolExecutor` yeu cau shutdown tuong minh.
- Worker loop: `while (running) { jobId = queue.poll(timeout); if (jobId != null) process(jobId); }`
  — poll voi timeout thay vi `take()` de worker con co hoi kiem tra `running` khi queue roi.
- Truoc khi xu ly: mark `RUNNING`; sau khi xu ly: mark `SUCCESS` hoac `FAILED` kem errorMessage.
- Simulation: moi producer la mot thread (`new Thread` hoac executor), `CountDownLatch` cho phep
  producer thread bao signal khi gui xong job, va thread chinh cho den khi tat ca job terminal.
- Test JUnit: tao service truc tiep, enqueue, `awaitTerminal` poll trang thai den khi ket thuc.

## Phase 3: CompletableFuture Orchestration

**Goal:** mot job khong con la "mot ham xu ly" ma la pipeline nhieu buoc: `validate -> process ->
enrich -> finalize`. Moi buoc co the fail, co the treo (hang), va co buoc "hay rot" (flaky) can retry.
Hoc cach compose cong viec bat dong bo va dat gioi han thoi gian cho no.

**Requirements:**
- [ ] Job di qua 4 buoc tuan tu; buoc nao nem exception -> job `FAILED` voi errorMessage cua buoc do.
- [ ] Step timeout: buoc chay qua han (vi du `slow-pipeline` job) -> job `FAILED` vi timeout.
- [ ] Pipeline timeout: tong thoi gian ca pipeline qua han -> job `FAILED`.
- [ ] Retry co gioi han cho process/enrich (vi du type `flaky-enrich` fail lan 1, thanh cong lan 2;
      fail lien tuc -> `FAILED` sau dung so lan retry).
- [ ] Test: happy path, fail tung buoc, step timeout, pipeline timeout, retry thanh cong / that bai.

**Cach lam:**
- `CompletableFuture.supplyAsync(step, executor)` + chaining `thenApplyAsync` de noi cac buoc —
  moi buoc nhan ket qua buoc truoc.
- Timeout: `orTimeout(duration)` (Java 9+) tra ve future bi `TimeoutException` neu qua han, hoac
  `completeOnTimeout` de tra gia tri mac dinh. Can biet: orTimeout chi bao truoc future ma thoi,
  buoc dang chay van tiep tuc — co the `cancel` pipeline khi timeout.
- Retry: vong lap `n` lan, moi lan `supplyAsync` lai buoc do, toi khi thanh cong hoac het luot;
  do vao pipeline bang `thenCompose`/`exceptionally`.
- Pipeline dung `CompletableFuture.allOf` neu co buoc chay song song (nang cao).
- Executor rieng cho pipeline (khong dung common pool cua CompletableFuture trong production).

## Phase 4: Backpressure & Bounded Queue

**Goal:** producer nhanh hon consumer la ban quen; queue khong gioi han = memory bomb (queue ngay
cang lon, OOM). Can gioi han suc chua queue va quyet dinh producer phai lam gi khi day:
block cho den khi con cho (BACKPRESSURE), hoac tu choi nhanh (FAIL_FAST). Khi nao dung cai nao la
quyet dinh kinh doanh: yeu cau khong mat job thi BLOCK, yeu cau latency thap thi FAIL_FAST.

**Requirements:**
- [ ] Queue co capacity cau hinh duoc (`app.job-queue.queue-capacity`); khi day:
  - `BLOCK`: producer cho den khi co cho, voi timeout (`app.job-queue.enqueue-timeout-ms`),
    het timeout thi nem `QueueFullException`.
  - `FAIL_FAST`: nem `QueueFullException` ngay.
- [ ] Job bi reject khong bi mat: van o `PENDING` trong repository, errorMessage ghi ro queue full.
- [ ] Simulation them mode `BURST`: producer bom het toc do, khong delay — de thay backpressure
      hoat dong that su.
- [ ] Simulation report them: `rejectedCount`, `backlogMax`, `backlogAvg`, `throughputJobsPerSec`.
- [ ] Graceful shutdown: khi tat app, pending job trong queue van duoc xu ly het (drain) truoc khi
      worker dung — chi ngung nhan job moi.
- [ ] Test: queue day -> block/timeout/reject; shutdown con pending job thi van ve terminal state.

**Cach lam:**
- `ArrayBlockingQueue(capacity)` — bounded, co cac method khac nhau cho moi chien luoc:
  `put()` (block vo han), `offer(e, timeout, unit)` (block co han), `offer(e)` (khong block).
- `RejectPolicy` enum {BLOCK, FAIL_FAST} + switch trong `enqueue`.
- Do backlog: `queue.size()` duoc sample dinh ky (mot daemon thread sample moi ~10ms, ghi max/avg).
- Throughput: tong job gui / thoi gian troi qua (`System.nanoTime`).
- Graceful shutdown: `pool.shutdown()` (dung nhan viec moi) + `awaitTermination(10s)` cho viec
  con lai + `shutdownNow()` chi khi het kien nhan. Worker loop phai dung dieu kien
  `while (running || !queue.isEmpty())` de van drain phan con lai sau khi running=false.
- Luu y hoc thuat: `LinkedBlockingQueue` khong gioi han la vi du kinh dien cua
  "queue cho producer tu do bom" — so sanh no voi ArrayBlockingQueue khi viet report.

## Phase 5: Scheduling & Priority

**Goal:** khong phai job nao cung san sang chay ngay. Job `delayMs` phai cho den dung gio (retry sau,
xuat ban theo lich, cleanup...). Job `priority` cao phai chen len truoc job thap (job VIP, payment...).
Hoc hai cau truc du lieu concurrency chuyen dung: `DelayQueue` va `PriorityBlockingQueue`.

**Requirements:**
- [ ] `POST /jobs` nhan `delayMs`: job chi chuyen sang `RUNNING` sau delayMs, khong bao gio som hon.
- [ ] `POST /jobs` nhan `priority`: job priority cao hon duoc pull truoc. Cung priority -> FIFO
      (job gui truoc chay truoc) — priority KHONG duoc pha vo tinh on dinh FIFO.
- [ ] Delayed job dang cho KHONG bi reject khi ready queue day (no chua "vao hang doi" ma).
- [ ] `GET /jobs/{id}` tra kem `priority` va `delayMs`.
- [ ] Test: delayed job khong RUNNING truoc deadline; priority cao chay truoc; cung priority FIFO;
      delayed job giu duoc thu tu uu tien khi den luot (dispatcher dua no vao head queue).
- [ ] (Co the de lai cho phase 7) Metrics: so delayed job dang cho, phan bo priority.

**Cach lam:**
- `DelayQueue<E>`: phan tu phai implement `Delayed` (ke thua `Comparable<Delayed>`):
  `getDelay(TimeUnit)` tra `readyAtNanos - System.nanoTime()`; `compareTo` so sanh `readyAtNanos`
  (cung gia tri thi so sanh them sequence de on dinh). `take()` chi tra phan tu da "chin".
- `PriorityBlockingQueue<E>` + `Comparator` rieng: so sanh `priority` Giam dan, cung priority thi
  so sanh `sequence` Tang dan (sequence = `AtomicLong.incrementAndGet()` khi enqueue).
- Kien truc hai tang: `scheduledQueue` (DelayQueue) + `readyQueue` (bounded PriorityBlockingQueue).
  `enqueue(job)` co delay -> vao scheduledQueue; khong delay -> vao readyQueue.
- `Semaphore(readyQueueCapacity)` de giu cho ready queue bounded (cung khai niem phase 4).
- Dispatcher thread: loop `poll(200ms)` tu scheduledQueue -> khi phan tu chin -> `semaphore.acquire()`
  (backpressure o day, delayed job khong bao gio reject) -> dua vao readyQueue.
- Worker pull tu readyQueue, xong `semaphore.release()`.
- Vong doi trong `stopWorkers()`: phai interrupt ca dispatcher lan workers, va dispatcher
  `while (running || !scheduledQueue.isEmpty())` de van chuyen het delayed job dang cho.
- Doc them: vi sao khong chi dung `PriorityBlockingQueue` cho ca delay (DelayQueue xu ly thoi gian
  chuan hon: khong can wakeup dinh ky).

## Phase 6: Virtual Threads & Structured Concurrency

**Goal:** platform thread dat (1MB stack, chiem OS resource) nen so luong gioi han ~nghin.
Virtual thread (Java 21) la thread cua JVM, re, co the tao hang trieu. Job queue la bai toan
I/O-bound nen la noi hoan hao de so sanh. `StructuredTaskScope` day cach quan ly vong doi task
con co to chuc (start -> join -> chiu loi) thay vi "fire and forget".

**Requirements:**
- [ ] Worker pool chuyen sang virtual threads, van xu ly dung so job va dung thu tu trang thai.
- [ ] Pipeline co buoc fan-out: mot so buoc co the chay song song, dung `StructuredTaskScope`
      gom ket qua cac nhanh, neu mot nhanh fail thi ca pipeline fail (hoac lay ket qua dau tien).
- [ ] Simulation so sanh: cung config, throughput virtual threads vs platform threads
      (dung workerCount cao, vi du 50-200) — viet ket qua nhan xet vao README hoac docs.
- [ ] Test: virtual worker xu ly dung so job; fan-out gom dung ket qua; exception propagation dung.

**Cach lam:**
- `Executors.newVirtualThreadPerTaskExecutor()` — moi task mot virtual thread moi, khong can pool;
  hoac `Thread.ofVirtual().name("worker-", 0).start(runnable)`.
- Virtual thread bi block (sleep, IO, lock) thi JVM nho thread ben duoi — dung de thay ro hieu qua
  khi worker sleep nhieu.
- `StructuredTaskScope.ShutdownOnFailure` (fail nhanh: fork cac nhanh, join, neu loi thi huy cac
  nhanh con lai) va `ShutdownOnSuccess` (tra ket qua dau tien) — `try-with-resources` de scope
  tu dong close.
- Khong dung `ThreadLocal` trong virtual thread (JVM canh bao) — tim hieu `ScopedValue` (preview).
- So sanh: chay simulation cung so job voi workerCount cao, do elapsed time — ghi bang so lieu.

## Phase 7: Observability

**Goal:** queue trong production khong the "nhin vao code" de biet chuyen gi xay ra. Can so do
thoi gian thuc: queue dang sau bao nhieu, bao nhieu job dang chay, latency bao nhieu, ty le fail.
Micrometer + actuator la cach chuan cua Spring Boot.

**Requirements:**
- [ ] Micrometer gauge: queue depth (backlog), so job dang `RUNNING`, so job `PENDING` cho schedule.
- [ ] Histogram: latency toan bo job (submit -> terminal) va latency tung step cua pipeline.
- [ ] Endpoint report tong hop: backlog hien tai, failed ratio, p95 latency (vi du `GET /report`).
- [ ] Test: sau khi enqueue + xu ly xong, metrics phan anh dung (gauge tang/giam, timer co du lieu).

**Cach lam:**
- `io.micrometer:micrometer-registry-prometheus` + `spring-boot-starter-actuator` -> co san
  `GET /actuator/prometheus`.
- `MeterRegistry.gauge("job.queue.depth", queue, BlockingQueue::size)` — gauge la ham doc tu
  nguon thuc, khong phai bien tinh.
- `Timer.builder("job.latency").publishPercentileHistogram()` / `publishPercentiles(0.5, 0.95)`
  de co p50/p95; `DistributionSummary` cho do dai queue (hoac gauge la du).
- Time step pipeline: wrap tung buoc voi `Timer.Sample.start(registry)` + `stop(timer)`.
- Report endpoint doc truc tiep tu registry (`registry.find(...)`) hoac tu repositories
  (failed ratio = failed / total).

## Phase 8 (stretch): Persistence & Crash Recovery

**Goal:** `ConcurrentHashMap` mat het khi restart — job queue that su phai song sot crash va
khong xu ly trung. Hoc at-least-once delivery: "gu lai duoc" (requeue) + "khong gay hai khi trung"
(idempotency).

**Requirements:**
- [ ] Job luu vao H2 embedded (JPA) thay vi in-memory map; restart khong mat job da nhan.
- [ ] Worker claim job theo kieu lease: danh `RUNNING` + heartbeat timestamp; heartbeat cap nhat
      dinh ky khi worker con song.
- [ ] Recovery: khi app khoi dong (hoac scheduler quet dinh ky), job `RUNNING` nhung heartbeat
      lau (worker chet) duoc requeue ve `PENDING`.
- [ ] At-least-once: neu cung mot job duoc xu ly 2 lan, ket qua khong nhan doi (idempotency key).
- [ ] Test: mo phong crash (dung service giua chung, khoi dong lai) -> job duoc requeue va hoan tat;
      xu ly trung lap khong nhan doi.

**Cach lam:**
- `spring-boot-starter-data-jpa` + `spring.datasource.url=jdbc:h2:mem:...` (file: `jdbc:h2:file:./data/jobdb`
  neu muon restart that su giu du lieu).
- Entity `Job` + `@Version` (optimistic lock) hoac cot `lease_owner`/`lease_expires_at` de dam bao
  hai worker khong cung claim mot job — SELECT ... FOR UPDATE / UPDATE ... WHERE status='PENDING'
  la kieu claim atomic.
- Heartbeat: worker (hoac scheduler) cap nhat `heartbeat_at` moi vai giay cho job dang RUNNING.
- Recovery: `@Scheduled(fixedRate=...)` quet `RUNNING` co `heartbeat_at < now - threshold`
  -> set ve PENDING (hoac vao queue lai) — do la requeue.
- Idempotency: ung dung tinh ket qua (vi du `processJob` ghi ket qua vao JobResult) truoc khi
  danh SUCCESS; neu chay lai thi ghi de cung ket qua — xu ly trung la "viet lai", khong tao them tac dung phu.
- Test "restart": tao instance moi cua repository/use-case tren cung DB file, chay lai flow.

## Phase 9 (stretch): Distributed Queue

**Goal:** mot process khong phai la production — can nhieu consumer (nhieu instance) chia viec,
khong ai xu ly trung, job fail nhieu lan di ve DLQ. Redis Streams la cau truc du lieu sinh ra
cho bai toan nay, va embedded Redis cho phep hoc khong can Docker.

**Requirements:**
- [ ] Redis Streams thay the queue trong memory: producer `XADD`, consumer group voi nhieu consumer.
- [ ] 2 consumer cung group khong bao gio nhan cung mot entry (XREADGROUP tra entry chua XACK).
- [ ] Consumer xu ly xong phai `XACK`; consumer chet giua chung -> entry nam trong pending list,
      consumer khac (hoac cung group) `XCLAIM`/`XAUTOCLAIM` duoc.
- [ ] DLQ: job fail qua N lan bi dua sang stream `dlq` (kep ID + so lan fail).
- [ ] Test: 2 consumer xu ly phan chia khong trung; pending entries sau khi consumer "chet"
      duoc claim va xu ly; fail N lan -> DLQ.

**Cach lam:**
- Embedded Redis cho test: thu vien embedded redis (vi du `com.github.codemonstur:embedded-redis`)
  chay trong JVM, khong can Docker; ket noi bang Lettuce (`spring-data-redis`).
- Stream commands: `XADD queue * payload` ; `XGROUP CREATE queue group1 0` ; `XREADGROUP GROUP
  group1 consumer1 COUNT 1 STREAMS queue >` ; `XACK queue group1 id` ; `XPENDING` / `XAUTOCLAIM`
  de recovery ; `XDEL` hoac `XADD dlq` khi fail.
- Consumer loop: XREADGROUP BLOCK -> xu ly -> XACK; catch loi -> dem so lan fail (vd luu `attempt`
  trong field cua entry) -> >= N thi XADD sang dlq roi XACK.
- So sanh voi single-node queue: khong giu thu tu tuyet doi giua cac consumer, throughput cao hon
  (chia phan), ordering chi dam bao trong tung consumer — ghi nhan xet vao docs.
- Chay "2 instance" = tao 2 consumer loop voi ten consumer khac nhau tren cung group, trong cung JVM test.

---

## Tong ket concept theo phase

| Phase | Cau truc du lieu / API | Bai hoc chinh |
|---|---|---|
| 1 | Thread, ReentrantLock, CountDownLatch | critical section, lost update |
| 2 | BlockingQueue, Executors, @PostConstruct | producer-consumer, lifecycle |
| 3 | CompletableFuture, orTimeout | pipeline bat dong bo, retry |
| 4 | ArrayBlockingQueue, Semaphore, shutdown | backpressure, bounded buffer |
| 5 | DelayQueue, PriorityBlockingQueue, Delayed | scheduling, priority + FIFO |
| 6 | VirtualThread, StructuredTaskScope | thread re, structured concurrency |
| 7 | Micrometer, actuator | observability, gauge vs timer |
| 8 | H2 + JPA, @Version, heartbeat | persistence, at-least-once |
| 9 | Redis Streams, consumer groups | distributed queue, DLQ |
