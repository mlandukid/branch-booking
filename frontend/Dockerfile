# syntax=docker/dockerfile:1

# ---- build stage: compiles the Angular app ----
FROM node:25-alpine AS build
WORKDIR /app
ENV NG_CLI_ANALYTICS=false

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---- runtime stage: unprivileged nginx serving the static files ----
FROM nginxinc/nginx-unprivileged:stable-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/ > /dev/null || exit 1