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
        var missing = raw.missing + daily.missing;
        var dataFrom = query.from();
        var dataTo = query.to();
        var resolution = "MILLISECOND";
        var preciseRange = true;
        if (plan.daily() != null) {
            resolution = "DAY";
            dataFrom = plan.daily().from();
            preciseRange = dataFrom.equals(query.from());
            if (plan.daily().to().isAfter(dataTo)) {
                dataTo = plan.daily().to();
                preciseRange = false;
            }
        }
        return new UsageSummary(calls, raw.inProgress + daily.inProgress,
            raw.succeeded + daily.succeeded,
            raw.failed + daily.failed, raw.timedOut + daily.timedOut,
            raw.cancelled + daily.cancelled, raw.abandoned + daily.abandoned,
            tokens.input, tokens.output, tokens.cacheRead, tokens.cacheCreation,
            tokens.reasoning, tokens.total, known, missing, raw.partial + daily.partial,
            calls == 0 ? 1D : (double) (known - raw.partial - daily.partial) / calls,
            preciseRange,
            calls == 0 ? 1D : (double) known / calls,
            complete && raw.incomplete + daily.incomplete == 0,
            resolution, dataFrom, dataTo);
    }

    private SummaryValues queryDailySummary(Connection connection, UsageQuery query)
        throws SQLException {
        var filter = aggregateFilter(query, true);
        var count = "SUM(call_count)";
        var knownValue = "SUM(known_usage_calls)";
        var partialValue = count
            + " FILTER (WHERE usage_quality IN ('PARTIAL', 'ESTIMATED'))";
        var missingValue = "SUM(missing_usage_calls)";
        var incompleteValue = "SUM(incomplete_call_count)";
        var table = "ai_usage_daily";
        var sql = "SELECT " + count + " call_count,"
            + count + " FILTER (WHERE status = 'IN_PROGRESS') in_progress_count,"
            + count + " FILTER (WHERE status = 'SUCCEEDED') success_count,"
            + count + " FILTER (WHERE status = 'FAILED') failed_count,"
            + count + " FILTER (WHERE status = 'TIMED_OUT') timed_out_count,"
            + count + " FILTER (WHERE status = 'CANCELLED') cancelled_count,"
            + count + " FILTER (WHERE status = 'ABANDONED') abandoned_count,"
            + knownValue + " known_usage_calls, " + missingValue
            + " missing_usage_calls, " + partialValue
            + " partial_usage_calls, " + incompleteValue + " incomplete_calls, SUM(input_tokens) input_tokens, SUM(output_tokens) output_tokens,"
            + " SUM(cache_read_input_tokens) cache_read_input_tokens,"
            + " SUM(cache_creation_input_tokens) cache_creation_input_tokens,"
            + " SUM(reasoning_output_tokens) reasoning_output_tokens,"
            + " SUM(accounted_total_tokens) accounted_total_tokens FROM "
            + table + " " + filter.sql();
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
            row.getLong("timed_out_count"), row.getLong("cancelled_count"),
            row.getLong("abandoned_count"),
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
            """ + aggregateTable(query, false) + " " + filter.sql()
            + " GROUP BY (started_at_ms / 3600000), status, usage_quality, complete";
        // Fold the ordered groups with scalar aggregates, without a second GROUP BY sort.
        // This returns one row and avoids JDBC work proportional to the number of hours.
        sql = """
            SELECT SUM(call_count) call_count,
              SUM(call_count) FILTER (WHERE status = 'IN_PROGRESS') in_progress_count,
              SUM(call_count) FILTER (WHERE status = 'SUCCEEDED') success_count,
              SUM(call_count) FILTER (WHERE status = 'FAILED') failed_count,
              SUM(call_count) FILTER (WHERE status = 'TIMED_OUT') timed_out_count,
              SUM(call_count) FILTER (WHERE status = 'CANCELLED') cancelled_count,
              SUM(call_count) FILTER (WHERE status = 'ABANDONED') abandoned_count,
              SUM(call_count) FILTER (WHERE usage_quality <> 'MISSING') known_usage_calls,
              SUM(call_count) FILTER (WHERE usage_quality = 'MISSING') missing_usage_calls,
              SUM(call_count) FILTER (WHERE usage_quality IN ('PARTIAL', 'ESTIMATED')) partial_usage_calls,
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
        boolean complete)
        throws SQLException {
        var points = new java.util.TreeMap<Instant, UsageTrendPoint>();
        var plan = sourcePlan(connection, query);
        for (var interval : plan.raw()) {
            queryTrendSource(connection, interval, false).forEach(point ->
                points.merge(point.bucketStart(), point,
                    UsageStatisticsQueryRepository::mergePoint));
        }
        if (plan.daily() != null) {
            queryTrendSource(connection, plan.daily(), true).forEach(point ->
                points.merge(point.bucketStart(), point,
                    UsageStatisticsQueryRepository::mergePoint));
        }
        return points.values().stream()
            .map(point -> point.withComplete(complete && point.complete()))
            .toList();
    }

    private List<UsageTrendPoint> queryTrendSource(Connection connection, UsageQuery query,
        boolean daily) throws SQLException {
        var filter = aggregateFilter(query, daily);
        var bucket = daily ? "day || 'T00:00:00Z'"
            : trendBucket("started_at_ms", UsageTrendResolution.HOUR);
        var grouping = daily ? "bucket" : "(started_at_ms / 3600000)";
        var count = daily ? "SUM(call_count)" : "COUNT(*)";
        var known = daily ? "SUM(known_usage_calls)"
            : "COUNT(*) FILTER (WHERE usage_quality <> 'MISSING')";
        var partial = count + " FILTER (WHERE usage_quality IN ('PARTIAL', 'ESTIMATED'))";
        var missing = daily ? "SUM(missing_usage_calls)"
            : "COUNT(*) FILTER (WHERE usage_quality = 'MISSING')";
        var incomplete = daily ? "SUM(incomplete_call_count)"
            : "COUNT(*) FILTER (WHERE complete = 0)";
        var table = aggregateTable(query, daily);
        var resolution = daily ? UsageTrendResolution.DAY : query.effectiveResolution();
        var sql = "SELECT " + bucket + " bucket, " + count
            + " call_count, SUM(input_tokens) input_tokens, SUM(output_tokens) output_tokens,"
            + " SUM(accounted_total_tokens) accounted_total_tokens, " + known
            + " known_usage_calls, " + missing + " missing_usage_calls, " + partial
            + " partial_usage_calls, " + incomplete
            + " incomplete_calls FROM " + table + " " + filter.sql()
            + " GROUP BY " + grouping + " ORDER BY " + grouping;
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
                    var point = new UsageTrendPoint(startedAt,
                        resolution,
                        rows.getLong("call_count"), nullableLong(rows, "input_tokens"),
                        nullableLong(rows, "output_tokens"),
                        nullableLong(rows, "accounted_total_tokens"),
                        rows.getLong("known_usage_calls"), rows.getLong("missing_usage_calls"),
                        rows.getLong("partial_usage_calls"), rows.getLong("incomplete_calls") == 0);
                    points.merge(startedAt, point, UsageStatisticsQueryRepository::mergePoint);
                }
                return List.copyOf(points.values());
            }
        }
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

    private static String trendBucket(String timestampColumn, UsageTrendResolution resolution) {
        var width = resolution == UsageTrendResolution.HOUR ? 3_600_000L : 86_400_000L;
        return "(" + timestampColumn + " / " + width + ") * " + width;
    }

    private static String aggregateTable(UsageQuery query, boolean daily) {
        // Aggregates always need these counters. A narrower ordering/filter index can look
        // cheaper to SQLite without ANALYZE statistics but causes a lookup for every call.
        if (daily) {
            return "ai_usage_daily";
        }
        var dimensions = java.util.stream.Stream.of(query.callerPlugin(), query.feature(),
                query.providerName(), query.modelName(), query.modelType(), query.operation())
            .anyMatch(value -> StringUtils.hasText(value));
        return "ai_calls INDEXED BY "
            + (dimensions ? "idx_calls_filtered_hour" : "idx_calls_aggregate_hour");
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
            left.knownUsageCalls() + right.knownUsageCalls(),
            left.missingUsageCalls() + right.missingUsageCalls(),
            left.partialUsageCalls() + right.partialUsageCalls(),
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
        var code = row.getString("error_code");
        if (type != null) {
            return new UsageError(type, code);
        }
        if (code != null) {
            return new UsageError(null, code);
        }
        return null;
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
                                 long incomplete, long partial, TokenValues tokens) {

        private static SummaryValues empty() {
            return new SummaryValues(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, TokenValues.empty());
        }

        private SummaryValues add(SummaryValues other) {
            return new SummaryValues(calls + other.calls, inProgress + other.inProgress,
                succeeded + other.succeeded,
                failed + other.failed, timedOut + other.timedOut,
                cancelled + other.cancelled, abandoned + other.abandoned,
                known + other.known, missing + other.missing, incomplete + other.incomplete, partial + other.partial, tokens.add(other.tokens));
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
