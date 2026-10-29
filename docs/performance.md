# Performance evidence

These are debug-build measurements from the Pixel 9a `AI` profile using the imported `gemma-4-E2B-it.litertlm` model. They are development baselines, not release-performance claims.

## CPU baseline

Two runs of `make benchmark`:

| Run | Load | First response | Generation |
| --- | ---: | ---: | ---: |
| 1 | 907 ms | 1,536 ms | 1,612 ms |
| 2 | 1,058 ms | 1,647 ms | 1,724 ms |

## GPU comparison

Two runs of `make gpu-benchmark`:

| Run | Load | First response | Generation |
| --- | ---: | ---: | ---: |
| Cold | 53,365 ms | 1,252 ms | 1,343 ms |
| Cached | 5,387 ms | 995 ms | 1,089 ms |

GPU generation is faster after preparation, but CPU startup is more predictable. CPU remains the normal app backend until an explicit backend policy or user setting is added.

## Vision CPU/GPU comparison

Two runs of `make vision-benchmark` using the same generated 512x512 image:

| Run | Backend | Load | TTFT | Generation |
| --- | --- | ---: | ---: | ---: |
| 1 | CPU | 931 ms | 11,467 ms | 12,780 ms |
| 1 | GPU | 17,201 ms | 3,320 ms | 4,505 ms |
| 2 | CPU | 1,542 ms | 11,447 ms | 12,623 ms |
| 2 | GPU | 9,119 ms | 3,779 ms | 5,341 ms |

The GPU vision path was about 2.5–2.8x faster for generation and about 3x faster to the first response, while startup remained roughly 6–18x slower than CPU.

## Measurement boundaries

- Results depend on debug builds, device temperature, model state, and cache state.
- The benchmark harness must never download or commit model binaries.
- New performance claims require the command, model, backend, device profile, and run conditions to be recorded here.
