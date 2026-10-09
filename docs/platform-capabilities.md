# Android capability boundaries

| Capability | Android API / access | Planned owner |
| --- | --- | --- |
| Default launcher | HOME intent filter, `RoleManager.ROLE_HOME` request on API 29+ | `:app` entry + platform gateway (slice 0B) |
| Installed activities and profiles | `LauncherApps.getActivityList`, `UserManager` accessible profiles, `LauncherApps.Callback` | `:platform:launcherapps` (0B) |
| App symbols | Adaptive monochrome layer preferred, then adaptive foreground, then legacy fallback; bounds/padding normalization, tinting, bounded `AppIdentity`-keyed snapshot cache off the UI thread, invalidated with catalogue callbacks | `:platform:launcherapps` icon boundary (future `platform:symbols` split only when justified) |
| Launch and shortcuts | `LauncherApps.startMainActivity`, launcher shortcut host access, shortcut query/launch | `:platform:launcherapps` / planned `:platform:shortcuts` |
| Widgets | `AppWidgetHost`, widget ID binding/configuration, hosted `AppWidgetHostView` | `:platform:widgets` (being introduced per `docs/agents/tasks/widgets.md`) |
| Notifications | User-enabled `NotificationListenerService`; capture normalized arrivals/removals, not an imagined complete history | planned `:platform:notifications` |
| Usage history | Optional user-granted usage access for `UsageStatsManager`; own launch history works without it | planned `:platform:usage` |
| Contacts | Runtime `READ_CONTACTS` only when contact search enabled | planned `:platform:contacts` |
| Documents | `ACTION_OPEN_DOCUMENT`/Storage Access Framework user-selected grants; no unrestricted filesystem scan | planned `:platform:documents` |

Slice 0A declares none of these privileged capabilities. Its standard LAUNCHER activity opens the foundation screen but cannot yet be selected as HOME. Add manifest entries and permission prompts alongside the feature that uses them.
