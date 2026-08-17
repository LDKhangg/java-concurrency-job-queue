# Java Concurrency Job Queue

Mini Spring Boot backend de hoc concurrency trong Java theo huong job queue backend.
Day la repo TU HOC: code chi dang o scaffold, ban tu implement tung phase theo
[`docs/roadmap.md`](docs/roadmap.md) — moi phase co **Goal** (tai sao), **Requirements**
(hanh vi phai dat), **Cach lam** (khai niem + API goi y) va **Checklist** de tick.

## Hien trang (scaffold)

- `POST /jobs`: tao job moi (type bat ky), luu vao repository, tra jobId.
- `GET /jobs/{id}`: xem trang thai job (`PENDING`).
- `GET /workers/metrics`: skeleton tra worker count.
- `POST /simulation/run`: skeleton nhan request, tra summary rong.
- Tat ca job dang o `PENDING` — chua co queue, chua co worker, chua co xu ly nen.

## Learning roadmap

| Phase | Chu de | Trang thai |
|---|---|---|
| 1 | Race condition lab | todo |
| 2 | Queue + worker pool | todo |
| 3 | CompletableFuture orchestration | todo |
| 4 | Backpressure & bounded queue | todo |
| 5 | Scheduling & priority | todo |
| 6 | Virtual threads & structured concurrency | todo |
| 7 | Observability | todo |
| 8 (stretch) | Persistence & crash recovery | todo |
| 9 (stretch) | Distributed queue | todo |

Chi tiet (Goal/Requirements/Cach lam/Checklist) o `docs/roadmap.md`.

## Run locally

```bash
./gradlew bootRun
```

## Run tests

```bash
./gradlew test
```