package me.foxtails.lakehead

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import me.foxtails.lakehead.core.model.ComponentIdentity
import org.junit.Assert.assertTrue

/** Read-only dumpsys access and strict parsing used by the work-profile device proof. */
internal class WorkProfileDeviceEvidence {
    fun userDump(): String = runShell(DUMP_USER)

    fun activityDump(): String = runShell(DUMP_ACTIVITY_ACTIVITIES)

    fun parseUserInfoRecords(dump: String): List<UserInfoRecord> {
        val lines = dump.lines()
        val records = mutableListOf<UserInfoRecord>()
        var index = 0
        while (index < lines.size) {
            val header = USER_INFO_HEADER.find(lines[index])
            if (header == null) {
                index++
                continue
            }
            val block = mutableListOf<String>()
            var scan = index + 1
            while (
                scan < lines.size &&
                !USER_INFO_HEADER.containsMatchIn(lines[scan]) &&
                (lines[scan].isBlank() || lines[scan].startsWith(" "))
            ) {
                block.add(lines[scan])
                scan++
            }
            records.add(
                UserInfoRecord(
                    androidUserId = header.groupValues[1].toInt(),
                    serialNo = header.groupValues[2],
                    parentId = (listOf(lines[index]) + block)
                        .firstNotNullOfOrNull { line -> PARENT_ID.find(line)?.groupValues?.get(1)?.toInt() },
                    type = block.firstNotNullOfOrNull { line ->
                        USER_TYPE_LINE.find(line)?.groupValues?.get(1)
                    },
                    raw = lines[index].trim(),
                ),
            )
            index = scan
        }
        return records
    }

    fun currentResumedRecords(dump: String): List<UserScopedRecord> = dump.lineSequence()
        .mapNotNull { line ->
            RESUMED_STATE_LINE.find(line)?.let { match ->
                UserScopedRecord(
                    userToken = match.groupValues[1].toInt(),
                    component = match.toComponent(),
                    raw = line.trim(),
                )
            }
        }
        .toList()

    fun strictUserToken(line: String): Int? =
        STRICT_USER_TOKEN.find(line)?.groupValues?.get(1)?.toInt()

    fun verifyExactComponentTokenConsistency(
        dump: String,
        component: ComponentIdentity,
        personalToken: Int,
        workToken: Int,
    ) {
        val unexpected = dump.lineSequence()
            .mapNotNull { ACTIVITY_RECORD_LINE.find(it) }
            .map { record -> record.groupValues[2].toInt() to record.toActivityRecordComponent() }
            .filter { (_, recordComponent) -> recordComponent == component }
            .filter { (token, _) -> token != personalToken && token != workToken }
        assertTrue(
            "Historical records of $component carry user tokens outside the mapped users " +
                "(u$personalToken, u$workToken): $unexpected",
            unexpected.none(),
        )
    }

    fun exactComponentActivityRecords(
        dump: String,
        component: ComponentIdentity,
    ): List<ActivityRecord> = dump.lineSequence()
        .mapNotNull { line ->
            ACTIVITY_RECORD_LINE.find(line)
                ?.takeIf { it.toActivityRecordComponent() == component }
                ?.let { match ->
                    val userToken = match.groupValues[2].toInt()
                    ActivityRecord(
                        key = ActivityRecordKey(
                            recordId = match.groupValues[1],
                            userToken = userToken,
                            taskId = match.groupValues[5].toInt(),
                        ),
                        userToken = userToken,
                        raw = line.trim(),
                    )
                }
        }
        .toList()

    private fun runShell(command: String): String {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val descriptor: ParcelFileDescriptor = automation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .bufferedReader()
            .use { it.readText() }
    }

    private fun MatchResult.toActivityRecordComponent(): ComponentIdentity = toComponent(
        packageGroup = 3,
        classGroup = 4,
    )

    private fun MatchResult.toComponent(): ComponentIdentity = toComponent(
        packageGroup = 2,
        classGroup = 3,
    )

    private fun MatchResult.toComponent(packageGroup: Int, classGroup: Int): ComponentIdentity {
        val packageName = groupValues[packageGroup]
        val className = groupValues[classGroup]
        return ComponentIdentity(
            packageName = packageName,
            className = if (className.startsWith(".")) packageName + className else className,
        )
    }

    data class UserScopedRecord(
        val userToken: Int,
        val component: ComponentIdentity,
        val raw: String,
    )

    data class ActivityRecordKey(
        val recordId: String,
        val userToken: Int,
        val taskId: Int,
    )

    data class ActivityRecord(
        val key: ActivityRecordKey,
        val userToken: Int,
        val raw: String,
    )

    data class UserInfoRecord(
        val androidUserId: Int,
        val serialNo: String,
        val parentId: Int?,
        val type: String?,
        val raw: String,
    )

    private companion object {
        const val DUMP_ACTIVITY_ACTIVITIES = "dumpsys activity activities"
        const val DUMP_USER = "dumpsys user"

        val RESUMED_STATE_LINE: Regex = Regex(
            """^\s*(?:ResumedActivity:|Resumed:|mResumedActivity:|topResumedActivity=)\s*""" +
                """ActivityRecord\{[0-9a-fA-F]+ u([0-9]+) """ +
                """([^/\s]+)/(\S+) t-?[0-9]+\}""",
        )
        val ACTIVITY_RECORD_LINE: Regex = Regex(
            """ActivityRecord\{([0-9a-fA-F]+) u([0-9]+) ([^/\s]+)/(\S+) t(-?[0-9]+)\}""",
        )
        val STRICT_USER_TOKEN: Regex = Regex("""(?<![0-9A-Za-z])u([0-9]+)(?![0-9A-Za-z])""")
        val USER_INFO_HEADER: Regex =
            Regex("""^\s*UserInfo\{(\d+):[^:}]*:[0-9a-fA-F]+\}\s+serialNo=(\S+)""")
        val USER_TYPE_LINE: Regex = Regex("""^\s*Type: (\S+)""")
        val PARENT_ID: Regex = Regex("""\bparentId=(-?\d+)\b""")
    }
}
