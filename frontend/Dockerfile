FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY default.conf.template /etc/nginx/templates/default.conf.template
# 上游默认指向本机占位，部署时用 -e BACKEND_UPSTREAM=... 覆盖
ENV BACKEND_UPSTREAM=http://127.0.0.1:9000
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
