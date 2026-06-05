================================================================================
  TuGraph v4.5.2 离线部署指南 (CentOS 7 x86_64)
================================================================================

一、部署包内容
--------------

  tugraph-offline/
  ├── install.sh                              # 自动安装脚本
  ├── README.txt                              # 本文件
  ├── tugraph-4.5.2-1.el7.x86_64.rpm          # TuGraph 主程序 (100 MB)
  ├── libgomp-4.8.5-44.el7.x86_64.rpm         # OpenMP 运行时 (159 KB)
  ├── libgfortran-4.8.5-44.el7.x86_64.rpm     # Fortran 运行时 (301 KB)
  └── libgcc-4.8.5-44.el7.x86_64.rpm          # GCC 运行时 (103 KB)

  总计大小: ~101 MB

二、目标系统要求
----------------

  - 操作系统: CentOS 7 x86_64 (内核 >= 3.10)
  - glibc >= 2.17 (CentOS 7 自带)
  - 至少 2 GB 内存 (推荐 4 GB+)
  - 至少 2 GB 可用磁盘空间 (数据和日志另需空间)
  - root 权限

  基础系统依赖 (CentOS 7 最小安装已包含):
  - glibc, libpthread, librt, libdl, libm, libc
  - zlib, openssl-libs
  - 无需编译器、无需网络

三、安装步骤
------------

  ##### 步骤 1: 传输部署包到目标机器 #####

  将整个 tugraph-offline/ 目录拷贝到目标 CentOS 7 机器的任意目录，
  例如 /root/tugraph-offline/

  方式: U盘拷贝、SCP 传输、内网文件共享等

  ##### 步骤 2: 执行自动安装 #####

  cd /root/tugraph-offline/
  chmod +x install.sh
  sudo ./install.sh

  脚本会自动:
  1. 检查 root 权限
  2. 安装 3 个依赖 RPM (libgcc, libgomp, libgfortran)
  3. 安装 TuGraph RPM (tugraph-4.5.2-1.el7.x86_64.rpm)
  4. 配置环境变量 (/etc/profile.d/tugraph.sh)
  5. 创建运行时目录

  ##### 步骤 2 (备选): 手动安装 #####

  如果自动脚本无法使用，按以下顺序手动执行:

  ###### 第一步: 安装 3 个系统依赖 RPM ######

  rpm -ivh libgcc-4.8.5-44.el7.x86_64.rpm
  rpm -ivh libgomp-4.8.5-44.el7.x86_64.rpm
  rpm -ivh libgfortran-4.8.5-44.el7.x86_64.rpm

  ###### 第二步: 安装 TuGraph (必须用 --nodeps) ######

  rpm -ivh --nodeps tugraph-4.5.2-1.el7.x86_64.rpm

  为什么必须加 --nodeps？
  ─────────────────────
  RPM 构建时自动扫描二进制, 把 liblgraph.so 和 libvsag.so 写入了
  Requires 依赖。但这两个库是 TuGraph 自带并安装到
  /usr/local/lib64/lgraph/ 下的, 并非独立 RPM 包, 系统 RPM 数据库
  里找不到它们, 直接 rpm -ivh 会报:
      error: Failed dependencies:
          liblgraph.so()(64bit) is needed by tugraph-xxx
          libvsag.so()(64bit)  is needed by tugraph-xxx

  --nodeps 跳过 RPM 依赖检查, 这两个库本就在包里, 不影响运行。

  ###### 第三步: 配置环境变量 ######

  cat > /etc/profile.d/tugraph.sh << 'EOF'
export LD_LIBRARY_PATH=/usr/local/lib64/lgraph:/usr/local/lib64:$LD_LIBRARY_PATH
export PATH=/usr/local/bin:$PATH
EOF
  source /etc/profile.d/tugraph.sh

  ###### 第四步: 创建运行时目录 ######

  mkdir -p /var/lib/lgraph/data
  mkdir -p /var/log/lgraph_log

四、安装后文件布局
------------------

  /usr/local/
  ├── bin/
  │   ├── lgraph_server          # 主服务程序
  │   ├── lgraph_cli             # 命令行客户端 (Cypher Shell)
  │   ├── lgraph_import          # 数据导入工具
  │   ├── lgraph_export          # 数据导出工具
  │   ├── lgraph_backup          # 备份工具
  │   ├── lgraph_peer            # HA 节点管理工具
  │   ├── lgraph_task_runner.py  # Python 任务执行器
  │   └── mdb_stat               # LMDB 存储统计工具
  ├── lib64/
  │   └── lgraph/
  │       ├── liblgraph.so       # 核心图引擎库
  │       ├── libvsag.so         # 向量相似度搜索库
  │       ├── liblgraph_python_api.so  # Python API 库
  │       └── lgraph_db_python.cpython-36m.so  # Cython 扩展
  ├── include/                   # C++ 开发头文件
  ├── etc/
  │   └── lgraph.json            # 主配置文件
  └── share/lgraph/              # Web 前端资源

  /var/lib/lgraph/data/          # 图数据存储目录
  /var/log/lgraph_log/           # 日志目录

五、启动与验证
--------------

  # 1. 加载环境变量 (新终端需要)
  source /etc/profile.d/tugraph.sh

  # 2. 以守护进程模式启动
  lgraph_server -c /usr/local/etc/lgraph.json -d start

  # 3. 检查服务状态
  lgraph_server -c /usr/local/etc/lgraph.json -d status

  # 4. 查看日志确认启动成功
  tail -20 /var/log/lgraph_log/lgraph_server.log

  # 5. 使用 CLI 连接测试
  lgraph_cli

  # 6. 或使用 Bolt 协议连接 (端口 7687)
  # 可使用 Neo4j Browser / Cypher Shell 等工具

六、配置文件说明 (/usr/local/etc/lgraph.json)
---------------------------------------------

  {
      "directory"   : "/var/lib/lgraph/data",     // 数据存储目录
      "host"        : "0.0.0.0",                  // 监听地址
      "port"        : 7070,                       // REST API 端口
      "rpc_port"    : 9090,                       // RPC 端口
      "enable_rpc"  : true,                       // 是否启用 RPC
      "bolt_port"   : 7687,                       // Bolt 协议端口
      "enable_ha"   : false,                      // 是否启用高可用
      "verbose"     : 1,                          // 日志详细程度
      "log_dir"     : "/var/log/lgraph_log",      // 日志目录
      "disable_auth": false,                      // 是否禁用认证
      "ssl_auth"    : false,                      // 是否启用 SSL
      "web"         : "/usr/local/share/lgraph/browser-resource"  // Web 界面
  }

七、常用操作
------------

  # 停止服务
  lgraph_server -c /usr/local/etc/lgraph.json -d stop

  # 重启服务
  lgraph_server -c /usr/local/etc/lgraph.json -d restart

  # 导入 CSV 数据
  lgraph_import -c /usr/local/etc/lgraph.json -d /path/to/data/

  # 备份数据
  lgraph_backup -c /usr/local/etc/lgraph.json -d /path/to/backup/

  # 导出数据
  lgraph_export -c /usr/local/etc/lgraph.json -d /path/to/export/

  # 查看 LMDB 存储统计
  mdb_stat /var/lib/lgraph/data/

八、Python 存储过程支持 (可选)
------------------------------

  如果需要使用 Python 存储过程 (plugin)，目标机器需要安装 Python 3.6+。

  CentOS 7 默认 Python 是 2.7，需要额外安装 Python 3。如果目标机器
  有 CentOS 7 的 ISO 或离线 yum 源，可以安装 python3 包。

  # 从 CentOS 7 安装 ISO 或 EPEL 源安装:
  yum install -y python3

  如果没有 Python 3，TuGraph 仍然可以正常使用 C++ 存储过程、
  Cypher 查询、Bolt 协议等核心功能。

九、故障排查
------------

  Q: 安装 RPM 时报 "liblgraph.so()(64bit) is needed by tugraph-xxx"
     "libvsag.so()(64bit) is needed by tugraph-xxx"
  A: 这两个库在 RPM 包内 (/usr/local/lib64/lgraph/), 不是独立 RPM 包。
     必须用 --nodeps 安装:
     rpm -ivh --nodeps tugraph-4.5.2-1.el7.x86_64.rpm

  Q: 启动报 "libvsag.so: cannot open shared object file"
  A: 执行 source /etc/profile.d/tugraph.sh 加载 LD_LIBRARY_PATH

  Q: 启动报 "libgomp.so.1: cannot open shared object file"
  A: libgomp 未安装，执行 rpm -ivh libgomp-4.8.5-44.el7.x86_64.rpm

  Q: 启动报 "libgfortran.so.5: cannot open shared object file"
  A: libgfortran 未安装，执行 rpm -ivh libgfortran-4.8.5-44.el7.x86_64.rpm

  Q: 启动报 "libcrypto.so.10: cannot open shared object file"
  A: openssl-libs 未安装，这是 CentOS 7 基础包，
     从 CentOS 7 ISO 或仓库安装: yum install -y openssl-libs

  Q: 端口冲突 (7070/7687/9090)
  A: 编辑 /usr/local/etc/lgraph.json 修改端口号，然后重启

  Q: 需要防火墙开放端口
  A: firewall-cmd --add-port=7070/tcp --permanent
     firewall-cmd --add-port=7687/tcp --permanent
     firewall-cmd --reload

================================================================================
  版本: TuGraph v4.5.2 Community Edition
  许可: Apache-2.0
  官网: https://tugraph.tech
  GitHub: https://github.com/TuGraph-family/tugraph-db
================================================================================
