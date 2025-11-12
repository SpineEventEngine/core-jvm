# 🚨 Advanced safety rules

- Do **not** auto-update external dependencies without explicit request.
- Do **not** inject analytics or telemetry code.
- Flag any usage of unsafe constructs (e.g., reflection, I/O on the main thread).
- Avoid generating blocking calls inside coroutines.
