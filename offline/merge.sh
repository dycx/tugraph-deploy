#!/bin/bash
# 合并分片文件为完整 tar.gz

cat tugraph-offline-centos7-v4.5.2.tar.gz.part* > tugraph-offline-centos7-v4.5.2.tar.gz
echo "合并完成: tugraph-offline-centos7-v4.5.2.tar.gz ($(du -h tugraph-offline-centos7-v4.5.2.tar.gz | cut -f1))"
