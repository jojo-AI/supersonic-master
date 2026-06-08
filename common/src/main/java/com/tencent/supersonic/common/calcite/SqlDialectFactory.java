package com.tencent.supersonic.common.calcite;

import com.tencent.supersonic.common.pojo.enums.EngineType;
import org.apache.calcite.avatica.util.Casing;
import org.apache.calcite.sql.SqlDialect;
import org.apache.calcite.sql.SqlDialect.Context;
import org.apache.calcite.sql.SqlDialect.DatabaseProduct;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class SqlDialectFactory {

    // JOJO-CK-Test: 通用引擎配置（适用于CLICKHOUSE、MYSQL、H2、STARROCKS，缺少方言支持）
    public static final Context DEFAULT_CONTEXT =
            SqlDialect.EMPTY_CONTEXT.withDatabaseProduct(DatabaseProduct.BIG_QUERY)
                    .withLiteralQuoteString("'").withLiteralEscapedQuoteString("''")
                    .withIdentifierQuoteString("`").withUnquotedCasing(Casing.UNCHANGED)
                    .withQuotedCasing(Casing.UNCHANGED).withCaseSensitive(false);

    // JOJO-CK-Test:新增ClickHouse方言配置
    public static final Context CLICKHOUSE_CONTEXT = SqlDialect.EMPTY_CONTEXT
            .withDatabaseProduct(DatabaseProduct.CLICKHOUSE).withLiteralQuoteString("'")
            .withLiteralEscapedQuoteString("''").withIdentifierQuoteString("`") // ClickHouse推荐反引号
            .withUnquotedCasing(Casing.UNCHANGED).withQuotedCasing(Casing.UNCHANGED)
            .withCaseSensitive(false); // ClickHouse本身大小写不敏感

    // JOJO-CK-Test:新增MySQL方言配置
    public static final Context MYSQL_CONTEXT =
            SqlDialect.EMPTY_CONTEXT.withDatabaseProduct(DatabaseProduct.MYSQL)
                    .withLiteralQuoteString("'").withLiteralEscapedQuoteString("''")
                    .withIdentifierQuoteString("`").withUnquotedCasing(Casing.TO_LOWER)
                    .withQuotedCasing(Casing.UNCHANGED).withCaseSensitive(false);

    public static final Context POSTGRESQL_CONTEXT = SqlDialect.EMPTY_CONTEXT
            .withDatabaseProduct(DatabaseProduct.BIG_QUERY).withLiteralQuoteString("'")
            .withLiteralEscapedQuoteString("''").withUnquotedCasing(Casing.UNCHANGED)
            .withQuotedCasing(Casing.UNCHANGED).withCaseSensitive(false);

    public static final Context HANADB_CONTEXT =
            SqlDialect.EMPTY_CONTEXT.withDatabaseProduct(DatabaseProduct.BIG_QUERY)
                    .withLiteralQuoteString("'").withIdentifierQuoteString("\"")
                    .withLiteralEscapedQuoteString("''").withUnquotedCasing(Casing.UNCHANGED)
                    .withQuotedCasing(Casing.UNCHANGED).withCaseSensitive(true);

    public static final Context PRESTO_CONTEXT = SqlDialect.EMPTY_CONTEXT
            .withDatabaseProduct(DatabaseProduct.PRESTO).withLiteralQuoteString("'")
            .withLiteralEscapedQuoteString("''").withUnquotedCasing(Casing.UNCHANGED)
            .withQuotedCasing(Casing.UNCHANGED).withCaseSensitive(true);

    public static final Context KYUUBI_CONTEXT =
            SqlDialect.EMPTY_CONTEXT.withDatabaseProduct(DatabaseProduct.BIG_QUERY)
                    .withLiteralQuoteString("'").withIdentifierQuoteString("`")
                    .withLiteralEscapedQuoteString("''").withUnquotedCasing(Casing.UNCHANGED)
                    .withQuotedCasing(Casing.UNCHANGED).withCaseSensitive(false);
    private static Map<EngineType, SemanticSqlDialect> sqlDialectMap;

    static {
        sqlDialectMap = new HashMap<>();
        // 新增ClickHouse方言配置
        sqlDialectMap.put(EngineType.CLICKHOUSE, new SemanticSqlDialect(CLICKHOUSE_CONTEXT));
        sqlDialectMap.put(EngineType.MYSQL, new SemanticSqlDialect(DEFAULT_CONTEXT));
        sqlDialectMap.put(EngineType.H2, new SemanticSqlDialect(DEFAULT_CONTEXT));
        sqlDialectMap.put(EngineType.POSTGRESQL, new SemanticSqlDialect(POSTGRESQL_CONTEXT));
        sqlDialectMap.put(EngineType.HANADB, new SemanticSqlDialect(HANADB_CONTEXT));
        sqlDialectMap.put(EngineType.STARROCKS, new SemanticSqlDialect(DEFAULT_CONTEXT));
        sqlDialectMap.put(EngineType.KYUUBI, new SemanticSqlDialect(KYUUBI_CONTEXT));
        sqlDialectMap.put(EngineType.PRESTO, new SemanticSqlDialect(PRESTO_CONTEXT));
        sqlDialectMap.put(EngineType.TRINO, new SemanticSqlDialect(PRESTO_CONTEXT));
    }

    public static SemanticSqlDialect getSqlDialect(EngineType engineType) {
        SemanticSqlDialect semanticSqlDialect = sqlDialectMap.get(engineType);
        if (Objects.isNull(semanticSqlDialect)) {
            return new SemanticSqlDialect(DEFAULT_CONTEXT);
        }
        return semanticSqlDialect;
    }
}
