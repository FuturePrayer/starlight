package cn.suhoan.starlight.entity;

/**
 * Git 自动同步调度类型。
 * <p>不直接暴露 Cron，改为提供有限的预设选项，降低用户配置门槛。</p>
 */
public enum GitScheduleType {
    MANUAL_ONLY,
    EVERY_30_MINUTES,
    HOURLY,
    DAILY,
    WEEKLY
}

