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
import java.util.Map;
import java.util.Optional;
import org.springframework.util.StringUtils;
import run.halo.aifoundation.service.observation.NormalizedUsage;
import run.halo.aifoundation.service.observation.UsageError;
import run.halo.aifoundation.service.observation.UsageExecutionRecord;
import run.halo.aifoundation.service.observation.UsageQuality;
import run.halo.aifoundation.service.observation.UsageStatus;
import run.halo.aifoundation.service.observation.UsageUnitKind;

class UsageStatisticsQueryRepository {

    UsageSummary summary(Connection connection, UsageQuery query, boolean complete)
        throws SQLException {
        var plan = sourcePlan(connection, query);
        var raw = SummaryValues.empty();
        for (var interval : plan.raw()) {
            raw = raw.add(queryRawSummary(connection, interval));
        }
        var daily = plan.daily() == null ? SummaryValues.empty()
            : queryDailySummary(connection, plan.daily());
        var tokens = raw.tokens.add(daily.tokens);
        var calls = raw.calls + daily.calls;
        var known = raw.known + daily.known;
        return new UsageSummary(calls, raw.inProgress + daily.inProgress,
            raw.succeeded + daily.succeeded, raw.failed + daily.failed,
            tokens.input, tokens.output, tokens.cacheRead, tokens.cacheCreation,
            tokens.reasoning, tokens.total, raw.missing + daily.missing,
            raw.partial + daily.partial,
            calls == 0 ? 1D : (double) known / calls,
            complete && raw.incomplete + daily.incomplete == 0);
    }

    private SummaryValues queryDailySummary(Connection connection, UsageQuery query)
        throws SQLException {
        var filter = aggregateFilter(query, true);
        var count = "SUM(call_count)";
        var sql = "SELECT " + count + " call_count,"
            + count + " FILTER (WHERE status = 'IN_PROGRESS') in_progress_count,"
            + count + " FILTER (WHERE status = 'SUCCEEDED') success_count,"
            + count + " FILTER (WHERE status = 'FAILED') failed_count,"
            + " SUM(known_usage_calls) known_usage_calls,"
            + " SUM(missing_usage_calls) missing_usage_calls,"
            + count + " FILTER (WHERE usage_quality = 'PARTIAL') partial_usage_calls,"
            + " SUM(incomplete_call_count) incomplete_calls,"
            + " SUM(input_tokens) input_tokens, SUM(output_tokens) output_tokens,"
            + " SUM(cache_read_input_tokens) cache_read_input_tokens,"
            + " SUM(cache_creation_input_tokens) cache_creation_input_tokens,"
            + " SUM(reasoning_output_tokens) reasoning_output_tokens,"
            + " SUM(accounted_total_tokens) accounted_total_tokens FROM ai_usage_daily "
            + filter.sql();
        try (var statement = connection.prepareStatement(sql)) {
            bind(statement, filter.parameters());
            try (var row = statement.executeQuery()) {
                row.next();
                return readSummary(row);
            }
        }
    }

    private static SummaryValues readSummary(ResultSet row) throws SQLException {
        return new SummaryValues(row.getLong("call_count"),
            row.getLong("in_progress_count"), row.getLong("success_count"),
            row.getLong("failed_count"),
            row.getLong("known_usage_calls"), row.getLong("missing_usage_calls"),
            row.getLong("incomplete_calls"), row.getLong("partial_usage_calls"),
            new TokenValues(nullableLong(row, "input_tokens"), nullableLong(row, "output_tokens"),
                nullableLong(row, "cache_read_input_tokens"),
                nullableLong(row, "cache_creation_input_tokens"),
                nullableLong(row, "reasoning_output_tokens"),
                nullableLong(row, "accounted_total_tokens")));
    }

    private SummaryValues queryRawSummary(Connection connection, UsageQuery query)
        throws SQLException {
        var filter = aggregateFilter(query, false);
        // The index orders these groups. Classify each group once, rather than evaluating
        // every status/quality counter for every call, and avoid a temporary GROUP BY sort.
        var sql = """
            SELECT status, usage_quality, complete, COUNT(*) call_count,
              SUM(input_tokens) input_tokens, SUM(output_tokens) output_tokens,
              SUM(cache_read_input_tokens) cache_read_input_tokens,
              SUM(cache_creation_input_tokens) cache_creation_input_tokens,
              SUM(reasoning_output_tokens) reasoning_output_tokens,
              SUM(accounted_total_tokens) accounted_total_tokens
            FROM
            """ + aggregateTable(false) + " " + filter.sql()
            + " GROUP BY (started_at_ms / 3600000), status, usage_quality, complete";
        // Fold the ordered groups with scalar aggregates, without a second GROUP BY sort.
        // This returns one row and avoids JDBC work proportional to the number of hours.
        sql = """
            SELECT SUM(call_count) call_count,
              SUM(call_count) FILTER (WHERE status = 'IN_PROGRESS') in_progress_count,
              SUM(call_count) FILTER (WHERE status = 'SUCCEEDED') success_count,
              SUM(call_count) FILTER (WHERE status = 'FAILED') failed_count,
              SUM(call_count) FILTER (WHERE usage_quality <> 'MISSING') known_usage_calls,
              SUM(call_count) FILTER (WHERE usage_quality = 'MISSING') missing_usage_calls,
              SUM(call_count) FILTER (WHERE usage_quality = 'PARTIAL') partial_usage_calls,
              SUM(call_count) FILTER (WHERE complete = 0) incomplete_calls,
              SUM(input_tokens) input_tokens, SUM(output_tokens) output_tokens,
              SUM(cache_read_input_tokens) cache_read_input_tokens,
              SUM(cache_creation_input_tokens) cache_creation_input_tokens,
              SUM(reasoning_output_tokens) reasoning_output_tokens,
              SUM(accounted_total_tokens) accounted_total_tokens
            FROM (
            """ + sql + ")";
        try (var statement = connection.prepareStatement(sql)) {
            bind(statement, filter.parameters());
            try (var row = statement.executeQuery()) {
                row.next();
                return readSummary(row);
            }
        }
    }

    List<UsageTrendPoint> trends(Connection connection, UsageQuery query,
        boolean complete, UsageBreakdownDimension dimension)
        throws SQLException {
        var points = new java.util.TreeMap<Instant, UsageTrendPoint>();
        var plan = sourcePlan(connection, query);
        for (var interval : plan.raw()) {
            queryTrendSource(connection, interval, false, dimension).forEach(point ->
                points.merge(point.bucketStart(), point,
                    UsageStatisticsQueryRepository::mergePoint));
        }
        if (plan.daily() != null) {
            queryTrendSource(connection, plan.daily(), true, dimension).forEach(point ->
                points.merge(point.bucketStart(), point,
                    UsageStatisticsQueryRepository::mergePoint));
        }
        return points.values().stream()
            .map(point -> point.withComplete(complete && point.complete()))
            .toList();
    }

    private List<UsageTrendPoint> queryTrendSource(Connection connection, UsageQuery query,
        boolean daily, UsageBreakdownDimension dimension) throws SQLException {
        var filter = aggregateFilter(query, daily);
        var bucket = daily ? "day || 'T00:00:00Z'" : "(started_at_ms / 3600000) * 3600000";
        var grouping = daily ? "bucket" : "(started_at_ms / 3600000)";
        var count = daily ? "SUM(call_count)" : "COUNT(*)";
        var partial = count + " FILTER (WHERE usage_quality = 'PARTIAL')";
        var missing = daily ? "SUM(missing_usage_calls)"
            : "COUNT(*) FILTER (WHERE usage_quality = 'MISSING')";
        var incomplete = daily ? "SUM(incomplete_call_count)"
            : "COUNT(*) FILTER (WHERE complete = 0)";
        var table = daily ? "ai_usage_daily" : aggregateTable(false);
        var resolution = daily ? UsageTrendResolution.DAY : query.effectiveResolution();
        var dimensionColumn = dimension == null ? null : dimension.column();
        var sql = "SELECT " + (dimensionColumn == null ? "" : dimensionColumn + " group_value, ")
            + bucket + " bucket, " + count
            + " call_count, SUM(input_tokens) input_tokens, SUM(output_tokens) output_tokens,"
            + " SUM(accounted_total_tokens) accounted_total_tokens, " + missing
            + " missing_usage_calls, " + partial
            + " partial_usage_calls, " + incomplete
            + " incomplete_calls FROM " + table + " " + filter.sql()
            + " GROUP BY " + grouping
            + (dimensionColumn == null ? "" : ", " + dimensionColumn)
            + " ORDER BY " + grouping;
        try (var statement = connection.prepareStatement(sql)) {
            bind(statement, filter.parameters());
            try (var rows = statement.executeQuery()) {
                var points = new java.util.TreeMap<Instant, UsageTrendPoint>();
                while (rows.next()) {
                    var startedAt = daily ? Instant.parse(rows.getString("bucket"))
                        : Instant.ofEpochMilli(rows.getLong("bucket"));
                    if (resolution == UsageTrendResolution.DAY) {
                        startedAt = startedAt.truncatedTo(java.time.temporal.ChronoUnit.DAYS);
                    }
                    Map<String, UsageTrendDimensionValue> byDimension = null;
                    if (dimensionColumn != null) {
                        byDimension = Map.of(groupValue(rows.getString("group_value")),
                            new UsageTrendDimensionValue(rows.getLong("call_count"),
                                nullableLong(rows, "accounted_total_tokens")));
                    }
                    var point = new UsageTrendPoint(startedAt,
                        resolution,
                        rows.getLong("call_count"), nullableLong(rows, "input_tokens"),
                        nullableLong(rows, "output_tokens"),
                        nullableLong(rows, "accounted_total_tokens"),
                        rows.getLong("missing_usage_calls"),
                        rows.getLong("partial_usage_calls"), rows.getLong("incomplete_calls") == 0,
                        byDimension);
                    points.merge(startedAt, point, UsageStatisticsQueryRepository::mergePoint);
                }
                return List.copyOf(points.values());
            }
        }
    }

    UsageBreakdown breakdown(Connection connection, UsageQuery query,
        UsageBreakdownDimension dimension) throws SQLException {
        var groups = new java.util.HashMap<String, BreakdownValues>();
        var plan = sourcePlan(connection, query);
        for (var interval : plan.raw()) {
            queryBreakdownSource(connection, interval, false, dimension, groups);
        }
        if (plan.daily() != null) {
            queryBreakdownSource(connection, plan.daily(), true, dimension, groups);
        }
        var items = groups.entrySet().stream()
            .map(entry -> entry.getValue().toItem(entry.getKey()))
            .sorted(java.util.Comparator.comparing(UsageBreakdownItem::accountedTotalTokens,
                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
            .toList();
        return new UsageBreakdown(items);
    }

    private void queryBreakdownSource(Connection connection, UsageQuery query, boolean daily,
        UsageBreakdownDimension dimension, java.util.Map<String, BreakdownValues> groups)
        throws SQLException {
        var filter = aggregateFilter(query, daily);
        var count = daily ? "SUM(call_count)" : "COUNT(*)";
        var table = daily ? "ai_usage_daily" : aggregateTable(false);
        var sql = "SELECT " + dimension.column() + " group_value, " + count + " call_count, "
            + count + " FILTER (WHERE status = 'FAILED') failed_count,"
            + " SUM(accounted_total_tokens) accounted_total_tokens FROM " + table + " "
            + filter.sql() + " GROUP BY " + dimension.column();
        try (var statement = connection.prepareStatement(sql)) {
            bind(statement, filter.parameters());
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    var values = new BreakdownValues(rows.getLong("call_count"),
                        rows.getLong("failed_count"),
                        nullableLong(rows, "accounted_total_tokens"));
                    groups.merge(groupValue(rows.getString("group_value")), values,
                        BreakdownValues::add);
                }
            }
        }
    }

    // Null dimension values (e.g. an unresolved caller plugin) group under the empty key;
    // the client renders it as "unknown".
    private static String groupValue(String value) {
        return value == null ? "" : value;
    }

    UsageCallPage listCalls(Connection connection, UsageQuery query, int size,
        String encodedCursor) throws SQLException {
        var base = filter(query, "started_at_ms", false);
        var parameters = new ArrayList<>(base.parameters());
        var sql = new StringBuilder("SELECT * FROM ai_calls ").append(base.sql());
        if (StringUtils.hasText(encodedCursor)) {
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
            nullableLong(row, "duration_ms"),
            row.getString("caller_plugin_name"), row.getString("caller_plugin_version"),
            row.getString("feature"),
            row.getString("operation"), row.getString("model_type"),
            row.getString("model_name"), row.getString("provider_name"),
            row.getString("request_model_id"),
            row.getString("response_model_id"), row.getInt("streaming") != 0,
            UsageStatus.valueOf(row.getString("status")), row.getString("error_type"),
            row.getInt("step_count"),
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
        var boundaryValue = stringMeta(connection, "call_detail_start");
        if (boundaryValue == null) {
            return new SourcePlan(List.of(query), null);
        }
        var boundary = LocalDate.parse(boundaryValue).atStartOfDay().toInstant(ZoneOffset.UTC);
        if (!query.from().isBefore(boundary)) {
            return new SourcePlan(List.of(query), null);
        }
        // Expired detail cannot answer partial UTC days. Expand explicitly and disclose the range.
        var from = query.from().atZone(ZoneOffset.UTC).toLocalDate()
            .atStartOfDay().toInstant(ZoneOffset.UTC);
        var requestedEnd = query.to().isBefore(boundary) ? query.to() : boundary;
        var endDay = requestedEnd.atZone(ZoneOffset.UTC).toLocalDate();
        var end = endDay.atStartOfDay().toInstant(ZoneOffset.UTC);
        if (end.isBefore(requestedEnd)) {
            end = endDay.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        }
        // Old unfinished/late-finished calls remain raw. Archived calls are removed atomically,
        // so combining both sources does not double count. Use DAY for both old sources.
        var raw = new ArrayList<UsageQuery>();
        var historical = withRange(query, from, end);
        raw.add(new UsageQuery(from, end, query.callerPlugin(), query.feature(),
            query.providerName(), query.modelName(), query.modelType(), query.operation(),
            query.status(), query.usageQuality(), UsageTrendResolution.DAY));
        if (query.to().isAfter(boundary)) {
            raw.add(withRange(query, boundary, query.to()));
        }
        return new SourcePlan(raw, historical);
    }

    private static UsageQuery withRange(UsageQuery source, Instant from, Instant to) {
        return new UsageQuery(from, to, source.callerPlugin(), source.feature(),
            source.providerName(), source.modelName(), source.modelType(), source.operation(),
            source.status(), source.usageQuality(), source.resolution());
    }

    private static String aggregateTable(boolean daily) {
        if (daily) {
            return "ai_usage_daily";
        }
        // The hour-leading covering index streams grouped rows in order and carries every
        // counter column, so aggregation never needs a table lookup per call.
        return "ai_calls INDEXED BY idx_calls_filtered_hour";
    }

    private SqlFilter aggregateFilter(UsageQuery query, boolean daily) {
        var base = filter(query, daily ? "day" : "started_at_ms", daily);
        if (daily) {
            return base;
        }
        var parameters = new ArrayList<>(base.parameters());
        parameters.add(query.from().toEpochMilli() / 3_600_000L);
        parameters.add(query.to().toEpochMilli() / 3_600_000L);
        // Keep millisecond predicates for exact edge hours; the leading index expression
        // also lets hourly GROUP BY stream ordered rows without a large temporary sort.
        return new SqlFilter(base.sql()
            + " AND (started_at_ms / 3600000) >= ? AND (started_at_ms / 3600000) <= ?",
            parameters);
    }

    private static UsageTrendPoint mergePoint(UsageTrendPoint left, UsageTrendPoint right) {
        return new UsageTrendPoint(left.bucketStart(), left.resolution(),
            left.callCount() + right.callCount(), add(left.inputTokens(), right.inputTokens()),
            add(left.outputTokens(), right.outputTokens()),
            add(left.accountedTotalTokens(), right.accountedTotalTokens()),
            left.missingUsageCalls() + right.missingUsageCalls(),
            left.partialUsageCalls() + right.partialUsageCalls(),
            left.complete() && right.complete(),
            mergeDimensions(left.byDimension(), right.byDimension()));
    }

    private static Map<String, UsageTrendDimensionValue> mergeDimensions(
        Map<String, UsageTrendDimensionValue> left, Map<String, UsageTrendDimensionValue> right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        var merged = new java.util.HashMap<>(left);
        right.forEach((key, value) -> merged.merge(key, value,
            UsageStatisticsQueryRepository::mergeDimensionValue));
        return java.util.Collections.unmodifiableMap(merged);
    }

    private static UsageTrendDimensionValue mergeDimensionValue(UsageTrendDimensionValue left,
        UsageTrendDimensionValue right) {
        return new UsageTrendDimensionValue(left.callCount() + right.callCount(),
            add(left.accountedTotalTokens(), right.accountedTotalTokens()));
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
        if (StringUtils.hasText(value)) {
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
        return type == null ? null : new UsageError(type);
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        return UsageSqliteJdbc.instant(row, column);
    }

    private static Long nullableLong(ResultSet row, String column) throws SQLException {
        return UsageSqliteJdbc.nullableLong(row, column);
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
                                 long known, long missing,
                                 long incomplete, long partial, TokenValues tokens) {

        private static SummaryValues empty() {
            return new SummaryValues(0, 0, 0, 0, 0, 0, 0, 0, TokenValues.empty());
        }

        private SummaryValues add(SummaryValues other) {
            return new SummaryValues(calls + other.calls, inProgress + other.inProgress,
                succeeded + other.succeeded, failed + other.failed,
                known + other.known, missing + other.missing,
                incomplete + other.incomplete, partial + other.partial, tokens.add(other.tokens));
        }
    }

    private record SourcePlan(List<UsageQuery> raw, UsageQuery daily) {
    }

    private record BreakdownValues(long calls, long failed, Long total) {

        private BreakdownValues add(BreakdownValues other) {
            return new BreakdownValues(calls + other.calls, failed + other.failed,
                UsageStatisticsQueryRepository.add(total, other.total));
        }

        private UsageBreakdownItem toItem(String value) {
            return new UsageBreakdownItem(value, calls, failed, total);
        }
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
