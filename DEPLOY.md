# Deploy — agent-assist-portal.demosaiportal.com

Same shape as the existing `/opt/agent-assist/` deploy: the WAR and the built
portal are copied onto the server, and compose builds a thin image around them.

| Piece | Port | Served as |
|-------|------|-----------|
| Backend (`api`) | 9099 | `http://<server>:9099` direct, `https://agent-assist-portal-api.demosaiportal.com` via the host nginx, and the portal's own `/api` |
| Portal (`web`, nginx) | 127.0.0.1:4005 → 80 | `https://agent-assist-portal.demosaiportal.com` via the host nginx |
| Postgres (already on the host) | 5440 | — |

Cloudflare fronts the domains and the **host** nginx (`/etc/nginx/sites-enabled/`)
terminates TLS, exactly as it does for the other apps on this box. The containers
serve plain HTTP; no certificates live inside them.

The portal fetches relative paths (`/api/v1/...`), so nginx serving the bundle
and proxying `/api` to `api:9099` puts both on one origin. **No backend
URL is baked into the frontend build** and CORS never enters the picture.

## 1. Pre-flight (on the server)

```bash
ss -ltnp | grep -E ':(4005|9099)\s'       # must be free, or the containers won't start
ls /etc/letsencrypt/live/                 # certs for BOTH domains present?
dig +short agent-assist-portal.demosaiportal.com
dig +short agent-assist-portal-api.demosaiportal.com
nc -zv 74.225.250.214 6334                # Qdrant reachable (internal RAG lane)
pg_dump -h localhost -p 5440 -U postgres agentassist > ~/agentassist-$(date +%F).sql
```

The `pg_dump` is not optional: this WAR runs the Part 1–4 Liquibase changelogs
and `ddl-auto: update` against that database the first time it boots.

## 2. Build (locally)

```bash
mvn clean package                         # 122 tests, ~1.5 min
cp target/agentassist-0.0.1-SNAPSHOT.war agentassist.war

cd portal && npm ci && npm run build && cd ..
```

## 3. Copy to the server

```bash
scp agentassist.war               root@<server>:/opt/agent-assist/
scp docker-compose.yml Dockerfile root@<server>:/opt/agent-assist/
scp -r nginx                      root@<server>:/opt/agent-assist/
scp -r portal/dist/.              root@<server>:/opt/agent-assist/portal-dist/
```

All configuration is inline in `docker-compose.yml` — there is no `.env`. Two
values ship as placeholders and must be filled in **on the server copy**, so the
live key never enters git:

```bash
cd /opt/agent-assist
vi docker-compose.yml     # OPENAI_API_KEY and JWT_SECRET
```

The old WAR is already backed up in place as `agentassist.war.bak_current`;
keep that habit before overwriting.

`portal-dist/` and `nginx/` are copied **into** the web image at build time, so
after changing either of them the portal needs `docker compose up -d --build web`,
not just a restart.

## 4. Start

```bash
cd /opt/agent-assist
docker compose up -d --build
docker compose logs -f agent-assist      # watch Liquibase + startup
```

## 5. Verify

```bash
curl -s localhost:9099/actuator/health
curl -s  localhost:4005 | head -3                               # the portal's index.html
curl -sI https://agent-assist-portal.demosaiportal.com          # 200 through Cloudflare
curl -s  https://agent-assist-portal.demosaiportal.com/api/v1/admin/config/registry-status
```

Then open the portal in a browser and check the Registry tab loads — that
exercises the DB, the registry caches and the proxy in one go.

## Rollback

```bash
cd /opt/agent-assist
cp agentassist.war.bak_current agentassist.war
docker compose up -d --build agent-assist
```

If the schema needs reverting too, restore the `pg_dump` from step 1.

## Still open at deploy time

- **KB documents need one re-upload** into `agent_assist_docs_openai` via the
  portal — the old mxbai collection is dimension-incompatible, so RAG answers
  will be ungrounded until that is done.
- **Nothing is authenticated.** `SecurityConfig` is `permitAll` and the JWT
  filter is not in the chain, so `http://<server>:9099/api/v1/admin/**` — prompt
  editing, document upload, provider switching — is open to anyone who reaches
  port 9099. Closing it means changing `SecurityConfig`, which is on the
  never-modify list, so it needs an explicit decision.
