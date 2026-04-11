#!/bin/bash
# MinIO Docker 启动脚本
# 数据存储位置: D:\docker\minio\data

docker run -d \
  -p 9000:9000 \
  -p 9001:9001 \
  --name minio-server \
  -e "MINIO_ROOT_USER=minioadmin" \
  -e "MINIO_ROOT_PASSWORD=minioadmin" \
  -v D:/docker/minio/data:/data \
  minio/minio server /data --console-address ":9001"

echo "MinIO 启动成功"
echo "API 端口: http://localhost:9000"
echo "控制台: http://localhost:9001"
echo "用户名: minioadmin"
echo "密码: minioadmin"
