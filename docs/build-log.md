# Build log

Problems I ran into while building this and how I fixed them.

## .gitignore was empty

Before my first commit, `git status` was showing `.idea/`, which should have been ignored. Turned out I'd created `.gitignore` in IntelliJ but never actually saved the content, so the file was empty. I rewrote it from the terminal, staged it again and checked `.idea/` was gone.

Since then I run `git status` and `git diff --cached --stat` before every commit.

## Postgres container wouldn't start on port 5432

Kafka and Redis came up fine but Postgres failed with "An attempt was made to access a socket in a way forbidden by its access permissions". The message sounds like a permissions problem, but it wasn't.

`netstat -ano | grep ':5432'` showed something already listening on 5432 (PID 7520), and `tasklist //FI "PID eq 7520"` showed it was `postgres.exe` running as a Windows service. I had a Postgres installed directly on Windows from before, set to start automatically on boot, and I'd forgotten about it. Windows reports a port held by another process this way.

I mapped the container to host port 5433 instead (`127.0.0.1:5433:5432`). Other containers still connect to `postgres:5432`; only tools on my machine use `localhost:5433`. I also set the Windows Postgres service to Manual so it no longer starts on its own. I kept 5433 even after stopping the service, so the project works whether that service is running or not.