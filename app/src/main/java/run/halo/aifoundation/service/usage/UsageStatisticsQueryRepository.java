package run.halo.aifoundation.service.usage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

class UsageStatisticsQueryRepository {

    UsageSummary summary(Connection connection, UsageQuery query, boolean complete)
        throws SQLException {
        var plan = sourcePlan(connection, query);
        var raw = SummaryValues.empty();
        for (var interval : plan.raw()) {
            raw = raw.add(querySummarySource(connection, interval, false));
        }
        var daily = plan.daily() == null ? SummaryValues.empty()
            : querySummarySource(connection, plan.daily(), true);
        var tokens = TokenValues.empty();
        for (var interval : plan.raw()) {
            tokens = tokens.add(queryTokenSource(connection, interval, false));
        }
        if (plan.daily() != null) {
            tokens = tokens.add(queryTokenSource(connection, plan.daily(), true));
        }
        var calls = raw.calls + daily.calls;
        var known = raw.known + daily.known;
        var missing = raw.missing + daily.missing;
        return new UsageSummary(calls, raw.inProgress + daily.inProgress,
            raw.succeeded + daily.succeeded,
            raw.failed + daily.failed, raw.timedOut + daily.timedOut,
            raw.cancelled + daily.cancelled, raw.abandoned + daily.abandoned,
            tokens.input, tokens.output, tokens.cacheRead, tokens.cacheCreation,
            tokens.reasoning, tokens.total, known, missing,
            calls == 0 ? 1D : (double) known / calls,
            complete && raw.incomplete + daily.incomplete == 0,
            plan.daily() != null ? "DAY" : "MILLISECOND", query.from(), query.to());
    }

    private SummaryValues querySummarySource(Connection connection, UsageQuery query, boolean daily)
        throws SQLException {
        var filter = filter(query, daily ? "day" : "started_at_ms", daily);
        var count = daily ? "SUM(call_count)" : "COUNT(*)";
        var statusValue = daily ? "call_count" : "1";
        var knownValue = daily ? "known_usage_calls"
            : "CASE WHEN usage_quality <> 'MISSING' THEN 1 ELSE 0 END";
        var missingValue = daily ? "missing_usage_calls"
            : "CASE WHEN usage_quality = 'MISSING' THEN 1 ELSE 0 END";
        var incompleteValue = daily ? "incomplete_call_count"
            : "CASE WHEN complete = 0 THEN 1 ELSE 0 END";
        var table = daily ? "ai_usage_daily" : "ai_calls";
        var sql = "SELECT " + count + " call_count,"
            + " SUM(CASE WHEN status = 'IN_PROGRESS' THEN " + statusValue
            + " ELSE 0 END) in_progress_count,"
            + " SUM(CASE WHEN status = 'SUCCEEDED' THEN " + statusValue
            + " ELSE 0 END) success_count,"
            + " SUM(CASE WHEN status = 'FAILED' THEN " + statusValue + " ELSE 0 END) failed_count,"
            + " SUM(CASE WHEN status = 'TIMED_OUT' THEN " + statusValue
            + " ELSE 0 END) timed_out_count,"
            + " SUM(CASE WHEN status = 'CANCELLED' THEN " + statusValue
            + " ELSE 0 END) cancelled_count,"
            + " SUM(CASE WHEN status = 'ABANDONED' THEN " + statusValue
            + " ELSE 0 END) abandoned_count,"
            + " SUM(" + knownValue + ") known_usage_calls, SUM(" + missingValue
            + ") missing_usage_calls, SUM(" + incompleteValue + ") incomplete_calls FROM "
            + table + " " + filter.sql();
        try (var statement = connection.prepareStatement(sql)) {
            bind(statement, filter.parameters());
            try (var row = statement.executeQuery()) {
                row.next();
                return new SummaryValues(row.getLong("call_count"),
                    row.getLong("in_progress_count"), row.getLong("success_count"),
                    row.getLong("failed_count"),
                    row.getLong("timed_out_count"), row.getLong("cancelled_count"),
                    row.getLong("abandoned_count"),
                    row.getLong("known_usage_calls"), row.getLong("missing_usage_calls"),
                    row.getLong("incomplete_calls"));
            }
        }
    }

    private TokenValues queryTokenSource(Connection connection, UsageQuery query, boolean daily)
        throws SQLException {
        if (daily) {
            return queryTokenTable(connection, "ai_token_usage_daily",
                "", filter(query, "day", true));
        }
        return queryTokenTable(connection,
            "ai_model_executions e JOIN ai_calls c ON c.id = e.call_id",
            "e.", rawTokenFilter(query, true)).add(queryTokenTable(connection, "ai_calls c",
            "c.", rawTokenFilter(query, false)));
    }

    private TokenValues queryTokenTable(Connection connection, String table, String columnPrefix,
        SqlFilter filter) throws SQLException {
        var sql = "SELECT SUM(" + columnPrefix + "input_tokens) input_tokens, SUM("
            + columnPrefix + "output_tokens) output_tokens, SUM(" + columnPrefix
            + "cache_read_input_tokens) cache_read_input_tokens, SUM(" + columnPrefix
            + "cache_creation_input_tokens) cache_creation_input_tokens, SUM(" + columnPrefix
            + "reasoning_output_tokens) reasoning_output_tokens, SUM(" + columnPrefix
            + "accounted_total_tokens) accounted_total_tokens FROM " + table + " "
            + filter.sql();
        try (var statement = connection.prepareStatement(sql)) {
            bind(statement, filter.parameters());
            try (var row = statement.executeQuery()) {
                row.next();
                return new TokenValues(nullableLong(row, "input_tokens"),
                    nullableLong(row, "output_tokens"),
                    nullableLong(row, "cache_read_input_tokens"),
                    nullableLong(row, "cache_creation_input_tokens"),
                    nullableLong(row, "reasoning_output_tokens"),
                    nullableLong(row, "accounted_total_tokens"));
            }
        }
    }

    private SqlFilter rawTokenFilter(UsageQuery query, boolean execution) {
        var clauses = new ArrayList<String>();
        var parameters = new ArrayList<Object>();
        var fact = execution ? "e" : "c";
        clauses.add(fact + ".started_at_ms >= ?");
        parameters.add(query.from().toEpochMilli());
        clauses.add(fact + ".started_at_ms < ?");
        parameters.add(query.to().toEpochMilli());
        addFilter(clauses, parameters, "c.caller_plugin_name", query.callerPlugin());
        addFilter(clauses, parameters, "c.feature", query.feature());
        addFilter(clauses, parameters, "c.provider_name", query.providerName());
        addFilter(clauses, parameters, "c.model_name", query.modelName());
        addFilter(clauses, parameters, "c.model_type", query.modelType());
        addFilter(clauses, parameters, "c.operation", query.operation());
        addFilter(clauses, parameters, fact + ".status",
            query.status() == null ? null : query.status().name());
        addFilter(clauses, parameters, fact + ".usage_quality",
            query.usageQuality() == null ? null : query.usageQuality().name());
        if (!execution) {
            clauses.add("NOT EXISTS (SELECT 1 FROM ai_model_executions e WHERE e.call_id = c.id)");
        }
        return new SqlFilter("WHERE " + String.join(" AND ", clauses), parameters);
    }

    List<UsageTrendPoint> trends(Connection connection, UsageQuery query,
        boolean complete)
        throws SQLException {
        var points = new java.util.TreeMap<Instant, UsageTrendPoint>();
        var plan = sourcePlan(connection, query);
        for (var interval : plan.raw()) {
            queryTrendSource(connection, interval, false).forEach(point ->
                points.merge(point.bucketStart(), point,
                    UsageStatisticsQueryRepository::mergePoint));
            queryTokenTrendSource(connection, interval, false).forEach(point ->
                points.merge(point.bucketStart(), point,
                    UsageStatisticsQueryRepository::mergePoint));
        }
        if (plan.daily() != null) {
            queryTrendSource(connection, plan.daily(), true).forEach(point ->
                points.put(point.bucketStart(), point));
            queryTokenTrendSource(connection, plan.daily(), true).forEach(point ->
                points.merge(point.bucketStart(), point,
                    UsageStatisticsQueryRepository::mergePoint));
        }
        return points.values().stream()
            .map(point -> point.withComplete(complete && point.complete()))
            .toList();
    }

    private List<UsageTrendPoint> queryTrendSource(Connection connection, UsageQuery query,
        boolean daily) throws SQLException {
        var filter = filter(query, daily ? "day" : "started_at_ms", daily);
        var bucket = daily ? "day || 'T00:00:00Z'"
            : trendBucket("started_at_ms", query.effectiveResolution());
        var count = daily ? "SUM(call_count)" : "COUNT(*)";
        var known = daily ? "SUM(known_usage_calls)"
            : "SUM(CASE WHEN usage_quality <> 'MISSING' THEN 1 ELSE 0 END)";
        var missing = daily ? "SUM(missing_usage_calls)"
            : "SUM(CASE WHEN usage_quality = 'MISSING' THEN 1 ELSE 0 END)";
        var incomplete = daily ? "SUM(incomplete_call_count)"
            : "SUM(CASE WHEN complete = 0 THEN 1 ELSE 0 END)";
        var table = daily ? "ai_usage_daily" : "ai_calls";
        var resolution = daily ? UsageTrendResolution.DAY : query.effectiveResolution();
        var sql = "SELECT " + bucket + " bucket, " + count
            + " call_count, NULL input_tokens, NULL output_tokens,"
            + " NULL accounted_total_tokens, " + known
            + " known_usage_calls, " + missing + " missing_usage_calls, " + incomplete
            + " incomplete_calls FROM " + table + " " + filter.sql()
            + " GROUP BY bucket ORDER BY bucket";
        try (var statement = connection.prepareStatement(sql)) {
            bind(statement, filter.parameters());
            try (var rows = statement.executeQuery()) {
                var points = new ArrayList<UsageTrendPoint>();
                while (rows.next()) {
                    points.add(new UsageTrendPoint(Instant.parse(rows.getString("bucket")),
                        resolution,
                        rows.getLong("call_count"), nullableLong(rows, "input_tokens"),
                        nullableLong(rows, "output_tokens"),
                        nullableLong(rows, "accounted_total_tokens"),
                        rows.getLong("known_usage_calls"), rows.getLong("missing_usage_calls"),
                        rows.getLong("incomplete_calls") == 0));
                }
                return List.copyOf(points);
            }
        }
    }

    private List<UsageTrendPoint> queryTokenTrendSource(Connection connection, UsageQuery query,
        boolean daily) throws SQLException {
        if (daily) {
            return queryTokenTrendTable(connection, "ai_token_usage_daily",
                "day || 'T00:00:00Z'", "", UsageTrendResolution.DAY,
                filter(query, "day", true));
        }
        var resolution = query.effectiveResolution();
        var points = new java.util.TreeMap<Instant, UsageTrendPoint>();
        queryTokenTrendTable(connection,
            "ai_model_executions e JOIN ai_calls c ON c.id = e.call_id",
            trendBucket("e.started_at_ms", resolution),
            "e.", resolution, rawTokenFilter(query, true)).forEach(point ->
            points.merge(point.bucketStart(), point,
                UsageStatisticsQueryRepository::mergePoint));
        queryTokenTrendTable(connection, "ai_calls c",
            trendBucket("c.started_at_ms", resolution),
            "c.", resolution, rawTokenFilter(query, false)).forEach(point ->
            points.merge(point.bucketStart(), point,
                UsageStatisticsQueryRepository::mergePoint));
        return List.copyOf(points.values());
    }

    private List<UsageTrendPoint> queryTokenTrendTable(Connection connection, String table,
        String bucket, String columnPrefix, UsageTrendResolution resolution, SqlFilter filter)
        throws SQLException {
        var sql = "SELECT " + bucket + " bucket, SUM(" + columnPrefix
            + "input_tokens) input_tokens, SUM(" + columnPrefix
            + "output_tokens) output_tokens, SUM(" + columnPrefix
            + "accounted_total_tokens) accounted_total_tokens FROM " + table + " "
            + filter.sql() + " GROUP BY bucket ORDER BY bucket";
        try (var statement = connection.prepareStatement(sql)) {
            bind(statement, filter.parameters());
            try (var rows = statement.executeQuery()) {
                var points = new ArrayList<UsageTrendPoint>();
                while (rows.next()) {
                    points.add(new UsageTrendPoint(Instant.parse(rows.getString("bucket")),
                        resolution, 0,
                        nullableLong(rows, "input_tokens"), nullableLong(rows, "output_tokens"),
                        nullableLong(rows, "accounted_total_tokens"), 0, 0));
                }
                return List.copyOf(points);
            }
        }
    }

    UsageCallPage listCalls(Connection connection, UsageQuery query, int size,
        String encodedCursor) throws SQLException {
        var base = filter(query, "started_at_ms", false);
        var parameters = new ArrayList<>(base.parameters());
        var sql = new StringBuilder("SELECT * FROM ai_calls ").append(base.sql());
        if (encodedCursor != null && !encodedCursor.isBlank()) {
            var cursor = UsageCursor.decode(encodedCursor, query);
            sql.append(" AND (started_at_ms < ? OR (started_at_ms = ? AND id < ?))");
            parameters.add(cursor.startedAt().toEpochMilli());
            parameters.add(cursor.startedAt().toEpochMilli());
            parameters.add(cursor.id());
        }
        sql.append(" ORDER BY started_at_ms DESC, id DESC LIMIT ?");
        parameters.add(size + 1);
        try (var statement = connection.prepareStatement(sql.toString())) {
            bind(statement, parameters);
            try (var rows = statement.executeQuery()) {
                var items = new ArrayList<UsageCallItem>();
                while (rows.next()) {
                    items.add(mapCall(rows));
                }
                String nextCursor = null;
                if (items.size() > size) {
                    items.removeLast();
                    var last = items.getLast();
                    nextCursor = UsageCursor.encode(last.startedAt(), last.id(), query);
                }
                return new UsageCallPage(List.copyOf(items), nextCursor);
            }
        }
    }

    Optional<UsageCallDetail> getCall(Connection connection, String id) throws SQLException {
        var call = findCall(connection, id);
        if (call.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new UsageCallDetail(call.get(), findExecutions(connection, id)));
    }

    private Optional<UsageCallItem> findCall(Connection connection, String id) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT * FROM ai_calls WHERE id = ?")) {
            statement.setString(1, id);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(mapCall(rows)) : Optional.empty();
            }
        }
    }

    private List<UsageExecutionRecord> findExecutions(Connection connection, String callId)
        throws SQLException {
        var sql = """
            SELECT * FROM ai_model_executions WHERE call_id = ?
            ORDER BY started_at_ms, unit_index, attempt_index
            """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, callId);
            try (var rows = statement.executeQuery()) {
                var result = new ArrayList<UsageExecutionRecord>();
                while (rows.next()) {
                    result.add(new UsageExecutionRecord(rows.getString("id"),
                        rows.getString("call_id"), rows.getLong("epoch"),
                        UsageUnitKind.valueOf(rows.getString("unit_kind")),
                        rows.getInt("unit_index"), rows.getInt("attempt_index"),
                        instant(rows, "started_at_ms"), instant(rows, "completed_at_ms"),
                        UsageStatus.valueOf(rows.getString("status")),
                        error(rows), rows.getString("request_model_id"),
                        rows.getString("response_model_id"), usage(rows)));
                }
                return List.copyOf(result);
            }
        }
    }

    private UsageCallItem mapCall(ResultSet row) throws SQLException {
        return new UsageCallItem(row.getString("id"), instant(row, "started_at_ms"),
            instant(row, "completed_at_ms"), nullableLong(row, "duration_ms"),
            row.getString("caller_plugin_name"), row.getString("caller_plugin_version"),
            row.getString("caller_detection_source"), row.getString("feature"),
            row.getString("operation"), row.getString("model_type"),
            row.getString("model_name"), row.getString("provider_name"),
            row.getString("provider_type"), row.getString("request_model_id"),
            row.getString("response_model_id"), row.getInt("streaming") != 0,
            UsageStatus.valueOf(row.getString("status")), row.getString("error_type"),
            row.getString("error_code"), row.getInt("step_count"),
            row.getInt("attempt_count"), row.getInt("missing_execution_count"),
            row.getInt("complete") != 0, usage(row));
    }

    private SqlFilter filter(UsageQuery query, String timeColumn, boolean daily) {
        var clauses = new ArrayList<String>();
        var parameters = new ArrayList<Object>();
        if (daily) {
            var fromDay = query.from().atZone(ZoneOffset.UTC).toLocalDate();
            if (!query.from().equals(fromDay.atStartOfDay().toInstant(ZoneOffset.UTC))) {
                fromDay = fromDay.plusDays(1);
            }
            clauses.add("day >= ?");
            parameters.add(fromDay.toString());
            clauses.add("day < ?");
            parameters.add(query.to().atZone(ZoneOffset.UTC).toLocalDate().toString());
        } else {
            clauses.add(timeColumn + " >= ?");
            parameters.add(query.from().toEpochMilli());
            clauses.add(timeColumn + " < ?");
            parameters.add(query.to().toEpochMilli());
        }
        addFilter(clauses, parameters, "caller_plugin_name", query.callerPlugin());
        addFilter(clauses, parameters, "feature", query.feature());
        addFilter(clauses, parameters, "provider_name", query.providerName());
        addFilter(clauses, parameters, "model_name", query.modelName());
        addFilter(clauses, parameters, "model_type", query.modelType());
        addFilter(clauses, parameters, "operation", query.operation());
        addFilter(clauses, parameters, "status",
            query.status() == null ? null : query.status().name());
        addFilter(clauses, parameters, "usage_quality",
            query.usageQuality() == null ? null : query.usageQuality().name());
        return new SqlFilter("WHERE " + String.join(" AND ", clauses), parameters);
    }

    private SourcePlan sourcePlan(Connection connection, UsageQuery query) throws SQLException {
        var watermarkValue = stringMeta(connection, "rollup_watermark");
        if (watermarkValue == null) {
            return new SourcePlan(List.of(query), null);
        }
        var watermarkEnd = LocalDate.parse(watermarkValue).plusDays(1)
            .atStartOfDay().toInstant(ZoneOffset.UTC);
        if (query.effectiveResolution() == UsageTrendResolution.HOUR) {
            var detailStartValue = stringMeta(connection, "execution_detail_start");
            if (detailStartValue != null) {
                var detailStart = LocalDate.parse(detailStartValue)
                    .atStartOfDay().toInstant(ZoneOffset.UTC);
                if (detailStart.isBefore(watermarkEnd)) {
                    watermarkEnd = detailStart;
                }
            }
        }
        var fromDay = query.from().atZone(ZoneOffset.UTC).toLocalDate();
        var dailyStart = query.from().equals(fromDay.atStartOfDay().toInstant(ZoneOffset.UTC))
            ? query.from() : fromDay.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        var toDay = query.to().atZone(ZoneOffset.UTC).toLocalDate()
            .atStartOfDay().toInstant(ZoneOffset.UTC);
        var dailyEnd = toDay.isBefore(watermarkEnd) ? toDay : watermarkEnd;
        if (!dailyStart.isBefore(dailyEnd)) {
            return new SourcePlan(List.of(query), null);
        }
        var raw = new ArrayList<UsageQuery>(2);
        if (query.from().isBefore(dailyStart)) {
            raw.add(withRange(query, query.from(), dailyStart));
        }
        if (dailyEnd.isBefore(query.to())) {
            raw.add(withRange(query, dailyEnd, query.to()));
        }
        return new SourcePlan(List.copyOf(raw), withRange(query, dailyStart, dailyEnd));
    }

    private static UsageQuery withRange(UsageQuery source, Instant from, Instant to) {
        return new UsageQuery(from, to, source.callerPlugin(), source.feature(),
            source.providerName(), source.modelName(), source.modelType(), source.operation(),
            source.status(), source.usageQuality(), source.resolution());
    }

    private static String trendBucket(String timestampColumn, UsageTrendResolution resolution) {
        return resolution == UsageTrendResolution.HOUR
            ? "strftime('%Y-%m-%dT%H:00:00Z', " + timestampColumn + " / 1000, 'unixepoch')"
            : "strftime('%Y-%m-%dT00:00:00Z', " + timestampColumn + " / 1000, 'unixepoch')";
    }

    private static UsageTrendPoint mergePoint(UsageTrendPoint left, UsageTrendPoint right) {
        return new UsageTrendPoint(left.bucketStart(), left.resolution(),
            left.callCount() + right.callCount(), add(left.inputTokens(), right.inputTokens()),
            add(left.outputTokens(), right.outputTokens()),
            add(left.accountedTotalTokens(), right.accountedTotalTokens()),
            left.knownUsageCalls() + right.knownUsageCalls(),
            left.missingUsageCalls() + right.missingUsageCalls(),
            left.complete() && right.complete());
    }

    private static Long add(Long left, Long right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return Math.addExact(left, right);
    }

    private static void addFilter(List<String> clauses, List<Object> parameters, String column,
        String value) {
        if (value != null && !value.isBlank()) {
            clauses.add(column + " = ?");
            parameters.add(value);
        }
    }

    private static void bind(PreparedStatement statement, List<Object> parameters)
        throws SQLException {
        for (int i = 0; i < parameters.size(); i++) {
            statement.setObject(i + 1, parameters.get(i));
        }
    }

    private static NormalizedUsage usage(ResultSet row) throws SQLException {
        return new NormalizedUsage(nullableLong(row, "input_tokens"),
            nullableLong(row, "output_tokens"), nullableLong(row, "cache_read_input_tokens"),
            nullableLong(row, "cache_creation_input_tokens"),
            nullableLong(row, "reasoning_output_tokens"),
            nullableLong(row, "provider_total_tokens"),
            nullableLong(row, "accounted_total_tokens"),
            UsageQuality.valueOf(row.getString("usage_quality")));
    }

    private static UsageError error(ResultSet row) throws SQLException {
        var type = row.getString("error_type");
        var code = row.getString("error_code");
        return type == null && code == null ? null : new UsageError(type, code);
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        var value = nullableLong(row, column);
        return value == null ? null : Instant.ofEpochMilli(value);
    }

    private static Long nullableLong(ResultSet row, String column) throws SQLException {
        var value = row.getLong(column);
        return row.wasNull() ? null : value;
    }

    private static String stringMeta(Connection connection, String key) throws SQLException {
        try (var statement = connection.prepareStatement(
            "SELECT value FROM ai_statistics_meta WHERE key = ?")) {
            statement.setString(1, key);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        }
    }


    private record SqlFilter(String sql, List<Object> parameters) {
    }

    private record SummaryValues(long calls, long inProgress, long succeeded, long failed,
                                 long timedOut,
                                 long cancelled, long abandoned, long known, long missing,
                                 long incomplete) {

        private static SummaryValues empty() {
            return new SummaryValues(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        private SummaryValues add(SummaryValues other) {
            return new SummaryValues(calls + other.calls, inProgress + other.inProgress,
                succeeded + other.succeeded,
                failed + other.failed, timedOut + other.timedOut,
                cancelled + other.cancelled, abandoned + other.abandoned,
                known + other.known, missing + other.missing, incomplete + other.incomplete);
        }
    }

    private record SourcePlan(List<UsageQuery> raw, UsageQuery daily) {
    }

    private record TokenValues(Long input, Long output, Long cacheRead, Long cacheCreation,
                               Long reasoning, Long total) {

        private static TokenValues empty() {
            return new TokenValues(null, null, null, null, null, null);
        }

        private TokenValues add(TokenValues other) {
            return new TokenValues(
                UsageStatisticsQueryRepository.add(input, other.input),
                UsageStatisticsQueryRepository.add(output, other.output),
                UsageStatisticsQueryRepository.add(cacheRead, other.cacheRead),
                UsageStatisticsQueryRepository.add(cacheCreation, other.cacheCreation),
                UsageStatisticsQueryRepository.add(reasoning, other.reasoning),
                UsageStatisticsQueryRepository.add(total, other.total));
        }
    }
}
