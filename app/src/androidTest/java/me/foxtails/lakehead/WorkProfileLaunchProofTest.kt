package me.foxtails.lakehead

import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.LauncherAppEntry
import me.foxtails.lakehead.core.model.ProfileId
import me.foxtails.lakehead.platform.launcherapps.LauncherAppsActivityLauncher
import me.foxtails.lakehead.platform.launcherapps.LauncherAppsAppCatalogue
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A4 device proof: one launchable component shared by personal and work profiles is proven to be
 * two distinct profile-aware identities, launched through its work [ProfileId], and verified in
 * current resumed-state evidence. An inaccessible profile token must not launch or fall back to
 * the caller's profile. The test performs read-only inspection plus one benign launch; it changes
 * no packages, roles, or profiles.
 */
@RunWith(AndroidJUnit4::class)
class WorkProfileLaunchProofTest {
    @Test
    fun sharedComponentLaunchesInWorkProfileAndInaccessibleProfileTokenIsRejected() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertEquals(
            "Instrumentation must execute ProtoLaunch's target UID",
            context.applicationInfo.uid,
            Process.myUid(),
        )

        // Wake and reveal the display so resumed-state evidence is meaningful.
        runShell("input keyevent KEYCODE_WAKEUP")
        runShell("wm dismiss-keyguard")

        val evidence = WorkProfileDeviceEvidence()
        assertNull(
            "Token matcher must reject uid-style tokens like u0a123",
            evidence.strictUserToken("ActivityRecord{1f u0a123 com.example/.Main t1}"),
        )
        assertEquals(110, evidence.strictUserToken("ActivityRecord{1f u110 com.example/.Main t1}"))
        assertEquals(11, evidence.strictUserToken("ActivityRecord{1f u11 com.example/.Main t1}"))

        runCatalogueAndLaunchProof(context, instrumentation, evidence)
    }

    private suspend fun runCatalogueAndLaunchProof(
        context: android.content.Context,
        instrumentation: android.app.Instrumentation,
        evidence: WorkProfileDeviceEvidence,
    ) {
        val userManager = context.getSystemService(UserManager::class.java)
        val personalUser = Process.myUserHandle()
        val accessibleUsers = userManager.userProfiles
        val tokenByUser = accessibleUsers.associateWith { user ->
            ProfileId(userManager.getSerialNumberForUser(user).toString())
        }
        val userByToken = tokenByUser.entries.associate { (user, token) -> token to user }
        val accessibleNonPersonalUsers = accessibleUsers.filter { it != personalUser }
        assumeTrue(
            "No work profile is available; skipping the A4 managed-profile proof",
            accessibleNonPersonalUsers.isNotEmpty(),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val catalogue = LauncherAppsAppCatalogue(context.applicationContext, scope)
        try {
            instrumentation.runOnMainSync { catalogue.start() }
            val apps = loadCatalogue(catalogue)
            val profiles = resolveCatalogueProfiles(
                apps = apps,
                userByToken = userByToken,
                personalUser = personalUser,
                accessibleNonPersonalUsers = accessibleNonPersonalUsers,
                evidence = evidence,
            )
            val selected = selectSharedComponent(apps, profiles)
            val personalEntry = apps.first {
                it.identity.profileId == profiles.personalProfileId &&
                    it.identity.component == selected
            }
            val workEntry = apps.first {
                it.identity.profileId == profiles.workProfileId && it.identity.component == selected
            }
            val personalIdentity = personalEntry.identity
            val workIdentity = workEntry.identity
            assertSharedIdentities(personalIdentity, workIdentity)

            val launcher = LauncherAppsActivityLauncher(context.applicationContext)
            val resumedEvidence = proveWorkProfileLaunch(
                launcher = launcher,
                workIdentity = workIdentity,
                component = selected,
                personalToken = personalUser.hashCode(),
                workToken = profiles.workUser.hashCode(),
                evidence = evidence,
            )
            proveInaccessibleTokenHasNoFallback(
                launcher = launcher,
                workIdentity = workIdentity,
                component = selected,
                personalToken = personalUser.hashCode(),
                evidence = evidence,
            )
            println(
                "WorkProfileLaunchProof summary: profiles=${profiles.profileIds.map { it.value }} " +
                    "personal=${profiles.personalProfileId}->${userByToken.getValue(profiles.personalProfileId)} " +
                    "hashToken=u${personalUser.hashCode()} managed=false " +
                    "work=${profiles.workProfileId}->${profiles.workUser} " +
                    "hashToken=u${profiles.workUser.hashCode()} managed=true " +
                    "selected=$selected resumedEvidence='${resumedEvidence.trim()}' " +
                    "personalFallbackAfterLaunch=false " +
                    "inaccessibleToken($INACCESSIBLE_PROFILE_TOKEN)=false " +
                    "personalFallbackAfterInaccessibleCall=false",
            )
        } finally {
            instrumentation.runOnMainSync { catalogue.stop() }
            scope.cancel()
        }
    }

    private suspend fun loadCatalogue(catalogue: LauncherAppsAppCatalogue): List<LauncherAppEntry> =
        withTimeout(CATALOGUE_TIMEOUT_MS) { catalogue.apps.first { it.isNotEmpty() } }

    private fun resolveCatalogueProfiles(
        apps: List<LauncherAppEntry>,
        userByToken: Map<ProfileId, UserHandle>,
        personalUser: UserHandle,
        accessibleNonPersonalUsers: List<UserHandle>,
        evidence: WorkProfileDeviceEvidence,
    ): CatalogueProfiles {
        val entriesByProfile = apps.groupBy { it.identity.profileId }
        val profileIds = entriesByProfile.keys.sortedBy { it.value }
        profileIds.forEach { token ->
            assertTrue(
                "Catalogue ProfileId $token must resolve to an accessible profile " +
                    "UserHandle; mapped tokens were ${userByToken.keys}",
                token in userByToken,
            )
        }
        val personalTokens = profileIds.filter { userByToken.getValue(it) == personalUser }
        assertEquals(
            "Exactly one catalogue profile identity must map to Process.myUserHandle() " +
                "(=$personalUser); profileIds=$profileIds",
            1,
            personalTokens.size,
        )
        val personalProfileId = personalTokens.single()
        assertEquals(
            "Personal profile must map to Process.myUserHandle()",
            personalUser,
            userByToken.getValue(personalProfileId),
        )

        val missingAccessibleUsers = accessibleNonPersonalUsers.filter { accessibleUser ->
            tokenByProfile(userByToken, accessibleUser) !in profileIds
        }
        assertTrue(
            "Every accessible non-personal profile must be represented in catalogue ProfileIds; " +
                "missing=${missingAccessibleUsers.map { it to tokenByProfile(userByToken, it) }} " +
                "catalogueProfileIds=$profileIds",
            missingAccessibleUsers.isEmpty(),
        )
        val records = evidence.parseUserInfoRecords(evidence.userDump())
        val managedCandidate = accessibleNonPersonalUsers.map { user ->
            tokenByProfile(userByToken, user) to user
        }.firstNotNullOfOrNull { (profileId, user) ->
            records.firstOrNull { record ->
                record.androidUserId == user.hashCode() &&
                    record.serialNo == profileId.value &&
                    record.type == MANAGED_PROFILE_TYPE &&
                    record.parentId == personalUser.hashCode()
            }?.let { profileId to user }
        }
        val (workProfileId, workUser) = managedCandidate ?: throw AssertionError(
            "Accessible non-personal profile(s) exist, but no managed candidate matched exact " +
                "Android user id, serial ProfileId, Type=$MANAGED_PROFILE_TYPE, and " +
                "parentId=${personalUser.hashCode()}. Candidates=" +
                accessibleNonPersonalUsers.map { user ->
                    "user=$user androidUserId=${user.hashCode()} " +
                        "profileId=${tokenByProfile(userByToken, user).value}"
                } + "; parsed UserInfo fields=${records.map { it.fieldsSummary() }}",
        )
        assertNotEquals(personalUser, workUser)
        return CatalogueProfiles(
            profileIds = profileIds,
            entriesByProfile = entriesByProfile,
            personalProfileId = personalProfileId,
            workProfileId = workProfileId,
            workUser = workUser,
        )
    }

    private fun selectSharedComponent(
        apps: List<LauncherAppEntry>,
        profiles: CatalogueProfiles,
    ): ComponentIdentity {
        val personalComponents = profiles.entriesByProfile.getValue(profiles.personalProfileId)
            .map { it.identity.component }.toSet()
        val workComponents = profiles.entriesByProfile.getValue(profiles.workProfileId)
            .map { it.identity.component }.toSet()
        val commonComponents = (personalComponents intersect workComponents)
            .sortedWith(compareBy({ it.packageName }, { it.className }))
        if (commonComponents.isEmpty()) {
            throw AssertionError(
                "No common component exists between personal profile ${profiles.personalProfileId} " +
                    "and work profile ${profiles.workProfileId}. Personal inventory " +
                    "(${personalComponents.size}): ${personalComponents.render()} ; work inventory " +
                    "(${workComponents.size}): ${workComponents.render()}",
            )
        }
        // Deterministic selection: the lexicographically smallest common component.
        val selected = commonComponents.first()
        assertTrue(apps.any {
            it.identity.profileId == profiles.personalProfileId && it.identity.component == selected
        })
        return selected
    }

    private fun assertSharedIdentities(personalIdentity: AppIdentity, workIdentity: AppIdentity) {
        assertEquals(
            "Shared component data must be equal across profiles",
            personalIdentity.component,
            workIdentity.component,
        )
        assertNotEquals(
            "The two AppIdentity values must differ by ProfileId",
            personalIdentity.profileId,
            workIdentity.profileId,
        )
        assertNotEquals("Personal and work AppIdentity values must be distinct", personalIdentity, workIdentity)
    }

    private fun proveWorkProfileLaunch(
        launcher: LauncherAppsActivityLauncher,
        workIdentity: AppIdentity,
        component: ComponentIdentity,
        personalToken: Int,
        workToken: Int,
        evidence: WorkProfileDeviceEvidence,
    ): String {
        assertNotEquals(personalToken, workToken)
        val preLaunchDump = evidence.activityDump()
        evidence.verifyExactComponentTokenConsistency(preLaunchDump, component, personalToken, workToken)
        val preLaunchResumed = evidence.currentResumedRecords(preLaunchDump)
        preLaunchResumed.forEach { println("WorkProfileLaunchProof pre-launch resumed: ${it.raw}") }
        println(
            "WorkProfileLaunchProof pre-launch exact-component state: " +
                preLaunchResumed.filter { it.component == component }.map { it.raw },
        )
        assertNull(
            "Cannot prove a causal work-profile launch: exact component $component is already " +
                "current-resumed under work user u$workToken before launch; resumed state lines=" +
                preLaunchResumed.map { it.raw },
            preLaunchResumed.firstOrNull { it.component == component && it.userToken == workToken },
        )
        assertTrue(
            "Work-profile entry must launch in its own profile: $workIdentity",
            launcher.launch(workIdentity),
        )
        val postLaunchDump = pollForCurrentResumed(component, personalToken, workToken, evidence)
        val resumed = evidence.currentResumedRecords(postLaunchDump)
        val workRecord = resumed.firstOrNull { it.component == component && it.userToken == workToken }
            ?: throw AssertionError(
                "No current ResumedActivity:/Resumed: line names the exact component $component " +
                    "under u$workToken within ${EVIDENCE_TIMEOUT_MS}ms of the launch; current resumed " +
                    "state lines were: ${resumed.map { it.raw }}",
            )
        assertNull(
            "No current resumed line may name the exact component under the personal user " +
                "u$personalToken; current resumed state lines were: ${resumed.map { it.raw }}",
            resumed.firstOrNull { it.component == component && it.userToken == personalToken },
        )
        println("WorkProfileLaunchProof resumed evidence: ${workRecord.raw}")
        return workRecord.raw
    }

    private fun proveInaccessibleTokenHasNoFallback(
        launcher: LauncherAppsActivityLauncher,
        workIdentity: AppIdentity,
        component: ComponentIdentity,
        personalToken: Int,
        evidence: WorkProfileDeviceEvidence,
    ) {
        val inaccessibleIdentity = workIdentity.copy(
            profileId = ProfileId(INACCESSIBLE_PROFILE_TOKEN),
        )
        val beforeDump = evidence.activityDump()
        val beforeRecords = evidence.exactComponentActivityRecords(beforeDump, component)
        val baselinePersonalKeys = beforeRecords.filter { it.userToken == personalToken }.map { it.key }.toSet()
        println("WorkProfileLaunchProof pre-inaccessible exact-component records: ${beforeRecords.map { it.raw }}")

        assertFalse(
            "An inaccessible/unknown profile token must not launch and must not fall back to the " +
                "caller's profile: $inaccessibleIdentity",
            launcher.launch(inaccessibleIdentity),
        )
        val settleDeadline = System.currentTimeMillis() + INACCESSIBLE_TOKEN_SETTLE_MS
        var afterDump: String
        do {
            afterDump = evidence.activityDump()
            val resumed = evidence.currentResumedRecords(afterDump)
            assertNull(
                "An inaccessible profile token must not resume exact component $component under " +
                    "personal user u$personalToken; current resumed state lines=${resumed.map { it.raw }}",
                resumed.firstOrNull { it.component == component && it.userToken == personalToken },
            )
            if (System.currentTimeMillis() >= settleDeadline) break
            Thread.sleep(minOf(EVIDENCE_POLL_INTERVAL_MS, settleDeadline - System.currentTimeMillis()))
        } while (true)

        val afterRecords = evidence.exactComponentActivityRecords(afterDump, component)
        val newPersonalRecords = afterRecords.filter {
            it.userToken == personalToken && it.key !in baselinePersonalKeys
        }
        assertTrue(
            "The inaccessible-token call must not create a new personal-user ActivityRecord for " +
                "exact component $component. Baseline personal keys=$baselinePersonalKeys; new " +
                "records=${newPersonalRecords.map { it.raw }}; all post-call exact-component " +
                "records=${afterRecords.map { it.raw }}",
            newPersonalRecords.isEmpty(),
        )
        println("WorkProfileLaunchProof post-inaccessible exact-component records: ${afterRecords.map { it.raw }}")
    }

    private fun pollForCurrentResumed(
        component: ComponentIdentity,
        personalToken: Int,
        workToken: Int,
        evidence: WorkProfileDeviceEvidence,
    ): String {
        val deadline = System.currentTimeMillis() + EVIDENCE_TIMEOUT_MS
        var dump = ""
        while (System.currentTimeMillis() <= deadline) {
            dump = evidence.activityDump()
            val resumed = evidence.currentResumedRecords(dump)
            assertNull(
                "launch(...) must not resume the personal instance of the exact component " +
                    "$component; current resumed state lines were: ${resumed.map { it.raw }}",
                resumed.firstOrNull { it.component == component && it.userToken == personalToken },
            )
            if (resumed.any { it.component == component && it.userToken == workToken }) return dump
            Thread.sleep(EVIDENCE_POLL_INTERVAL_MS)
        }
        return dump
    }

    private fun Set<ComponentIdentity>.render(): List<String> =
        sortedWith(compareBy({ it.packageName }, { it.className }))
            .map { "${it.packageName}/${it.className}" }

    private fun tokenByProfile(userByToken: Map<ProfileId, UserHandle>, user: UserHandle): ProfileId =
        userByToken.entries.single { it.value == user }.key

    private fun WorkProfileDeviceEvidence.UserInfoRecord.fieldsSummary(): String =
        "UserInfo(androidUserId=$androidUserId, serialNo=$serialNo, type=$type, parentId=$parentId)"

    private data class CatalogueProfiles(
        val profileIds: List<ProfileId>,
        val entriesByProfile: Map<ProfileId, List<LauncherAppEntry>>,
        val personalProfileId: ProfileId,
        val workProfileId: ProfileId,
        val workUser: UserHandle,
    )

    private companion object {
        const val INACCESSIBLE_PROFILE_TOKEN = "9223372036854775807"
        const val MANAGED_PROFILE_TYPE = "android.os.usertype.profile.MANAGED"
        const val CATALOGUE_TIMEOUT_MS = 60_000L
        const val EVIDENCE_TIMEOUT_MS = 20_000L
        const val EVIDENCE_POLL_INTERVAL_MS = 2_000L
        const val INACCESSIBLE_TOKEN_SETTLE_MS = 3_000L

        fun runShell(command: String): String {
            val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
            return android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)
                .bufferedReader()
                .use { it.readText() }
        }
    }
}
