# PlaceholderAPI

安装 PlaceholderAPI 后，IdlePool 自动注册内部扩展 `idlepool`，无需从 eCloud 下载。

## 会话变量

```text
%idlepool_active%
%idlepool_pool%
%idlepool_session_time%
%idlepool_total_time%
%idlepool_next_reward%
%idlepool_earned%
%idlepool_inbox_count%
```

## 统计与排名

```text
%idlepool_daily_time%
%idlepool_weekly_time%
%idlepool_monthly_time%
%idlepool_longest_session%
%idlepool_cycles%
%idlepool_rewards%
%idlepool_rank_daily%
%idlepool_rank_weekly%
%idlepool_rank_monthly%
%idlepool_rank_total%
```

未上榜时排名返回 `0`。

## 活动

```text
%idlepool_event%
%idlepool_progress_multiplier%
```

统计值从异步缓存读取。玩家登录时会预热缓存；首次离线查询如果缓存尚未准备完成，可能先返回 `0`，随后刷新为数据库值。
