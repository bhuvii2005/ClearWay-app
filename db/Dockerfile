# ClearWay database image: PostGIS plus pgvector (for the Coach's knowledge base).
# No secrets here: they come from .env at run time.
#
# postgis/postgis:15-3.4 does not ship pgvector. Its base is Debian 11 (bullseye), whose
# PostgreSQL packages have moved to the archive server, so the apt source is rewritten to
# point there. Packages are verified with the official PostgreSQL signing key that the base
# image already holds (fingerprint B97B 0AFC AA1A 47F0 44F2 44A0 7FCC 7D46 ACCC 4CF8);
# nothing is installed unsigned.
FROM postgis/postgis:15-3.4

# Exact package version, so rebuilds are repeatable. Change it on purpose, not by accident.
ARG PGVECTOR_VERSION=0.8.6-1.pgdg11+1

RUN set -eux; \
    keyring=/usr/local/share/keyrings/postgres.gpg.asc; \
    test -s "$keyring"; \
    echo "deb [ signed-by=$keyring ] https://apt-archive.postgresql.org/pub/repos/apt/ bullseye-pgdg main $PG_MAJOR" \
        > /etc/apt/sources.list.d/pgdg.list; \
    apt-get update; \
    apt-get install -y --no-install-recommends "postgresql-$PG_MAJOR-pgvector=$PGVECTOR_VERSION"; \
    rm -rf /var/lib/apt/lists/*
