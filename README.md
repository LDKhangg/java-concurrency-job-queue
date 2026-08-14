# Java Concurrency Job Queue

Mini Spring Boot backend scaffold de hoc concurrency trong Java theo huong job queue backend.

## V1 scaffold hien tai

- `POST /jobs`
- `GET /jobs/{id}`
- `GET /workers/metrics`
- `POST /simulation/run`

## Run locally

```bash
./gradlew bootRun
```

## Run tests

```bash
./gradlew test
```

## Muc tieu tiep theo

- them race condition lab
- them worker pool that su
- them CompletableFuture pipeline
- them simulation load thuc chien
