# ClearWay web app image: builds the React app, then serves the static files with nginx.

# --- Build ---
FROM node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
# The API address is public (the browser calls it), so it is a build argument, not a secret.
ARG VITE_API_URL=http://localhost:8090
ENV VITE_API_URL=$VITE_API_URL
RUN npm run build

# --- Serve ---
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=15s --timeout=5s --retries=3 \
    CMD wget -qO- http://127.0.0.1/ > /dev/null || exit 1
