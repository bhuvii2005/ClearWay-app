# ClearWay database image: PostGIS plus pgvector (for the Coach's knowledge base).
# postgis/postgis does not ship pgvector, so it is installed from the PostgreSQL apt
# repository. This base image runs on Debian 11 (bullseye), whose PostgreSQL packages now
# live on the archive server, so the apt source is pointed there first.
# No secrets here: they come from .env at run time.
FROM postgis/postgis:15-3.4

RUN sed -i 's|http://apt.postgresql.org/pub/repos/apt/|https://apt-archive.postgresql.org/pub/repos/apt/|' \
        /etc/apt/sources.list.d/pgdg.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends postgresql-15-pgvector \
    && rm -rf /var/lib/apt/lists/*
