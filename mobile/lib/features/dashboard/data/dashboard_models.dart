/// Dart mirror of backend `DashboardSummaryResponse`.
class DashboardSummary {
  const DashboardSummary({
    required this.activeLeadsCount,
    required this.totalLeadsCount,
    required this.activeDealsCount,
    required this.activeDealsValue,
    required this.weightedPipelineValue,
    required this.totalDealsValue,
    required this.pendingTasksCount,
    required this.overdueTasksCount,
    required this.funnelStages,
  });

  final int activeLeadsCount;
  final int totalLeadsCount;
  final int activeDealsCount;
  final num activeDealsValue;
  final num weightedPipelineValue;
  final num totalDealsValue;
  final int pendingTasksCount;
  final int overdueTasksCount;
  final List<StageSummary> funnelStages;

  factory DashboardSummary.fromJson(Map<String, dynamic> json) {
    int asInt(Object? v) => (v as num?)?.toInt() ?? 0;
    num asNum(Object? v) => (v as num?) ?? 0;
    return DashboardSummary(
      activeLeadsCount: asInt(json['activeLeadsCount']),
      totalLeadsCount: asInt(json['totalLeadsCount']),
      activeDealsCount: asInt(json['activeDealsCount']),
      activeDealsValue: asNum(json['activeDealsValue']),
      weightedPipelineValue: asNum(json['weightedPipelineValue']),
      totalDealsValue: asNum(json['totalDealsValue']),
      pendingTasksCount: asInt(json['pendingTasksCount']),
      overdueTasksCount: asInt(json['overdueTasksCount']),
      funnelStages: (json['funnelStages'] as List? ?? [])
          .map((e) => StageSummary.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }
}

class StageSummary {
  const StageSummary({
    required this.stage,
    required this.count,
    required this.value,
  });

  final String stage;
  final int count;
  final num value;

  factory StageSummary.fromJson(Map<String, dynamic> json) => StageSummary(
    stage: json['stage'] as String? ?? '—',
    count: (json['count'] as num?)?.toInt() ?? 0,
    value: (json['value'] as num?) ?? 0,
  );
}
