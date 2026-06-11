# JDBC 驱动目录

将需要连接的数据源的 JDBC 驱动 jar 放在此目录下，应用启动时会通过 PropertiesLauncher 自动加载。

## 构建时自动获取

执行 `mvn package` 后，所有 `provided` 作用域的 JDBC 驱动 jar 会自动拷贝到 `target/drivers/` 目录。

## 手动添加

支持的驱动列表：

| 数据库 | 驱动 jar 关键字 | 驱动类 |
|--------|----------------|--------|
| MySQL / TiDB / OceanBase | `mysql-connector-j` | `com.mysql.cj.jdbc.Driver` |
| MariaDB | `mariadb-java-client` | `org.mariadb.jdbc.Driver` |
| PostgreSQL / Greenplum | `postgresql` | `org.postgresql.Driver` |
| Oracle | `ojdbc8` | `oracle.jdbc.OracleDriver` |
| SQL Server | `mssql-jdbc` | `com.microsoft.sqlserver.jdbc.SQLServerDriver` |
| SQLite | `sqlite-jdbc` | `org.sqlite.JDBC` |
| ClickHouse | `clickhouse-jdbc` | `com.clickhouse.jdbc.ClickHouseDriver` |
| DB2 | `jcc` | `com.ibm.db2.jcc.DB2Driver` |
| Trino | `trino-jdbc` | `io.trino.jdbc.TrinoDriver` |
| Presto | `presto-jdbc` | `com.facebook.presto.jdbc.PrestoDriver` |
| Derby | `derby` | `org.apache.derby.jdbc.EmbeddedDriver` |
| HSQLDB | `hsqldb` | `org.hsqldb.jdbc.JDBCDriver` |
| TDengine | `taos-jdbcdriver` | `com.taosdata.jdbc.TSDBDriver` |
| DuckDB | `duckdb_jdbc` | `org.duckdb.DuckDBDriver` |
| Firebird | `jaybird` | `org.firebirdsql.jdbc.FBDriver` |
| Apache Drill | `drill-jdbc` | `org.apache.drill.jdbc.Driver` |
| Neo4j | `neo4j-jdbc-driver` | `org.neo4j.jdbc.Driver` |
| SAP HANA | `ngdbc` | `com.sap.db.jdbc.Driver` |
| Snowflake | `snowflake-jdbc` | `net.snowflake.client.jdbc.SnowflakeDriver` |
| InfluxDB | `influxdb-client-java` | - |

## 启动方式

```bash
# 默认从 jar 同级目录的 drivers/ 加载
java -jar data-connect-1.0.0.jar

# 指定自定义驱动目录
java -Dloader.path=/opt/db-drivers/ -jar data-connect-1.0.0.jar
```
