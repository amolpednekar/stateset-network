/**
 *   Copyright 2020, Stateset.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */


package io.stateset.approval

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import java.lang.IllegalArgumentException

// *********
// * Approval State *
// *********

@CordaSerializable
@BelongsToContract(ApprovalContract::class)
data class Approval(val approvalId: String,
                       val approvalName: String,
                       val industry: String,
                       val approvalStatus: ApprovalStatus,
                       val submitter: Party,
                       val approver: Party,
                       override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState, ContractState {


    override val participants: List<AbstractParty> get() = listOf(submitter, approver)


}

@CordaSerializable
enum class ApprovalStatus {
    SUBMITTED, REQUESTED, UNSTARTED, STARTED, INREVIEW, MEDICAL_CHECK, FINANCIAL_CHECK, WORKING, ESCALATED, APPROVED, REJECTED
}



class ApprovalContract : Contract {
    // This is used to identify our contract when building a transaction
    companion object {
        val APPROVAL_CONTRACT_ID = ApprovalContract::class.java.canonicalName
    }

    // Used to indicate the transaction's intent.
    interface Commands : CommandData {

        class CreateApproval: Commands
        class Approve: Commands
        class Reject: Commands


    }


    // A transaction is considered valid if the verify() function of the contract of each of the transaction's input
    // and output states does not throw an exception.
    override fun verify(tx: LedgerTransaction) {
        val approvalInputs = tx.inputsOfType<Approval>()
        val approvalOutputs = tx.outputsOfType<Approval>()
        val approvalCommand = tx.commandsOfType<ApprovalContract.Commands>().single()

        when(approvalCommand.value) {
            is Commands.CreateApproval -> requireThat {
                "no inputs should be consumed" using (approvalInputs.isEmpty())
                // TODO we might allow several jobs to be proposed at once later
                "one output should be produced" using (approvalOutputs.size == 1)

            }


            is Commands.Approve -> requireThat {
                "one input should be produced" using (approvalInputs.size == 1)
                "one output should be produced" using (approvalOutputs.size == 1)

                val approvalInput = approvalInputs.single()
                val approvalOutput = approvalOutputs.single()

                "the input status must be set as started" using (approvalInput.approvalStatus == ApprovalStatus.REQUESTED)
                "the output status should be set as approved" using (approvalOutput.approvalStatus == ApprovalStatus.APPROVED)
                "only the status must change" using (approvalInput.copy(approvalStatus = approvalOutput.approvalStatus) == approvalOutput)

            }


            is Commands.Reject -> requireThat {
                "one input should be produced" using (approvalInputs.size == 1)
                "one output should be produced" using (approvalOutputs.size == 1)

                val approvalInput = approvalInputs.single()
                val approvalOutput = approvalOutputs.single()

                "the input status must be set as in effect" using (approvalInput.approvalStatus == ApprovalStatus.REQUESTED)
                "the output status should be set as renewed" using (approvalOutput.approvalStatus ==ApprovalStatus.REJECTED)
                "only the status must change" using (approvalInput.copy(approvalStatus = ApprovalStatus.REJECTED) == approvalOutput)


            }


            else -> throw IllegalArgumentException("Unrecognised command.")
        }
    }

}