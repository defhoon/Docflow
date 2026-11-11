# DocFlow Frontend 운영 이미지 (#27, ADR-0007). 빌드 컨텍스트: frontend/
# BACKEND_URL은 rewrite 대상이 빌드 시 고정되므로 빌드 인자로 받는다(예: http://backend.railway.internal:8080).
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG BACKEND_URL
RUN test -n "$BACKEND_URL" || (echo "BACKEND_URL build argument is required" >&2; exit 1)
ENV BACKEND_URL=$BACKEND_URL NEXT_TELEMETRY_DISABLED=1
RUN npm run build

FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 HOSTNAME=0.0.0.0 PORT=3000
COPY --from=build --chown=node:node /app/.next/standalone ./
COPY --from=build --chown=node:node /app/.next/static ./.next/static
USER node
EXPOSE 3000
CMD ["node", "server.js"]
