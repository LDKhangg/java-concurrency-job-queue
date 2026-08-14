# Java Concurrency Job Queue Roadmap

## Current status

- [x] Phase 1: Race condition lab
- [x] Phase 2: In-memory queue + fixed worker pool
- [x] Phase 3: CompletableFuture orchestration
- [ ] Phase 4: Producer-consumer load lab hardening

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

Da lam:

- `JobOrchestrationService` gom pipeline async rieng voi thread pool cho pipeline.
- Process/enrich la 2 step co retry co gioi han theo `app.job-queue.retry-count`.
- Loi duoc gan voi ten step de de debug va de hoc luong exception propagation.
- Integration test qua API va unit test service cho timeout, retry, fail path.

## Phase 4: Producer-Consumer Load Lab

Goal: bien simulation thanh mot bai hoc producer-consumer day du hon.

Issue checklist:

- [ ] Them burst mode va ramp mode cho producers.
- [ ] Them metrics backlog theo thoi diem.
- [ ] Them graceful shutdown test khi con pending job.
- [ ] Them so sanh throughput theo worker count.
- [ ] Them bao cao tong ket latency co ban va failed ratio.
