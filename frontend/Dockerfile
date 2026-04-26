# 第一阶段：构建
FROM node:16 AS builder
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

# 第二阶段：运行 (使用 Nginx)
FROM nginx:stable-alpine
# 复制构建好的文件到 Nginx 目录
COPY --from=builder /app/dist /usr/share/nginx/html
# 复制自定义的 Nginx 配置文件
COPY nginx.conf /etc/nginx/nginx.conf

# 暴露 80 端口
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]