package net.corda.bn.states

import net.corda.bn.contracts.MembershipContract
import net.corda.bn.schemas.MembershipStateSchemaV1
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.CordaSerializable
import java.time.Instant

/**
 * Represents a membership on the ledger.
 *
 * @property identity Corda Identity of a member.
 * @property networkId Unique identifier of a Business Network membership belongs to.
 * @property status Status of the state (i.e. PENDING, ACTIVE, SUSPENDED).
 * @property issued Timestamp when the state has been issued.
 * @property modified Timestamp when the state has been modified last time.
 */
@BelongsToContract(MembershipContract::class)
data class MembershipState(
        val identity: Party,
        val networkId: String,
        val status: MembershipStatus,
        val roles: Set<BNRole> = emptySet(),
        val issued: Instant = Instant.now(),
        val modified: Instant = issued,
        override val linearId: UniqueIdentifier = UniqueIdentifier(),
        override val participants: List<AbstractParty>
) : LinearState, QueryableState {

    override fun generateMappedObject(schema: MappedSchema) = when (schema) {
        is MembershipStateSchemaV1 -> MembershipStateSchemaV1.PersistentMembershipState(
                cordaIdentity = identity,
                networkId = networkId,
                status = status
        )
        else -> throw IllegalArgumentException("Unrecognised schema $schema")
    }

    override fun supportedSchemas() = listOf(MembershipStateSchemaV1)

    fun isPending() = status == MembershipStatus.PENDING
    fun isActive() = status == MembershipStatus.ACTIVE
    fun isSuspended() = status == MembershipStatus.SUSPENDED

    private fun permissions() = roles.flatMap { it.permissions }.toSet()
    fun canActivateMembership() = AdminPermission.CAN_ACTIVATE_MEMBERSHIP in permissions()
    fun canSuspendMembership() = AdminPermission.CAN_SUSPEND_MEMBERSHIP in permissions()
    fun canRevokeMembership() = AdminPermission.CAN_REVOKE_MEMBERSHIP in permissions()
    fun canModifyPermissions() = AdminPermission.CAN_MODIFY_PERMISSIONS in permissions()
    fun canModifyMembership() = permissions().isNotEmpty()
}

/**
 * Statuses that membership can go through.
 */
@CordaSerializable
enum class MembershipStatus {
    /**
     * Newly submitted state which hasn't been approved by authorised member yet. Pending members can't transact on the Business Network.
     */
    PENDING,

    /**
     * Active members can transact on the Business Network and modify other memberships if they are authorised.
     */
    ACTIVE,

    /**
     * Suspended members can't transact on the Business Network or modify other memberships. Suspended members can be activated back.
     */
    SUSPENDED
}

@CordaSerializable
open class BNRole(val name: String, val permissions: Set<BNPermission>)

@CordaSerializable
class BNORole : BNRole("BNO", setOf(
        AdminPermission.CAN_ACTIVATE_MEMBERSHIP,
        AdminPermission.CAN_SUSPEND_MEMBERSHIP,
        AdminPermission.CAN_REVOKE_MEMBERSHIP,
        AdminPermission.CAN_MODIFY_PERMISSIONS
))

@CordaSerializable
class MemberRole : BNRole("Member", emptySet())

@CordaSerializable
interface BNPermission

@CordaSerializable
enum class AdminPermission : BNPermission {
    CAN_ACTIVATE_MEMBERSHIP,
    CAN_SUSPEND_MEMBERSHIP,
    CAN_REVOKE_MEMBERSHIP,
    CAN_MODIFY_PERMISSIONS
}