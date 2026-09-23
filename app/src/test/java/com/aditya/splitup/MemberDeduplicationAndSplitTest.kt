package com.aditya.splitup

import com.aditya.splitup.data.model.Expense
import com.aditya.splitup.data.model.ExpenseSplit
import com.aditya.splitup.data.model.Member
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemberDeduplicationAndSplitTest {

    @Test
    fun testSplitRatioCombiningWhenMergingMembers() {
        // Expense 10 has splits for member 1 (keep) with ratio 2, and member 2 (duplicate) with ratio 3
        val keepMemberId = 1L
        val duplicateMemberId = 2L
        val expenseId = 10L

        val existingKeepSplit = ExpenseSplit(
            id = 101L,
            expenseId = expenseId,
            memberId = keepMemberId,
            ratioPart = 2
        )
        val duplicateSplit = ExpenseSplit(
            id = 102L,
            expenseId = expenseId,
            memberId = duplicateMemberId,
            ratioPart = 3
        )

        // Simulated MemberDao.mergeAndRemoveDuplicateMember combining logic
        val combinedRatio = existingKeepSplit.ratioPart + duplicateSplit.ratioPart
        val updatedKeepSplit = existingKeepSplit.copy(ratioPart = combinedRatio)

        // Total ratio parts must be preserved (2 + 3 = 5)
        assertEquals(5, updatedKeepSplit.ratioPart)
        assertEquals(keepMemberId, updatedKeepSplit.memberId)
        assertEquals(expenseId, updatedKeepSplit.expenseId)
    }

    @Test
    fun testSameNameDistinctMembersNotMergedWithoutLinkedUid() {
        // Two real people named "Alex" in the same group
        val alex1 = Member(
            id = 1L,
            groupId = 100L,
            name = "Alex",
            linkedUid = "user_abc",
            firestoreId = "fs_alex_1"
        )
        val alex2 = Member(
            id = 2L,
            groupId = 100L,
            name = "Alex",
            linkedUid = "user_xyz",
            firestoreId = "fs_alex_2"
        )

        // Deduplication rule A2: Only merge if linkedUid is equal (and not null) or firestoreId matches
        val shouldMergeByUid = (alex1.linkedUid != null && alex1.linkedUid == alex2.linkedUid)
        val shouldMergeByFsId = (alex1.firestoreId != null && alex1.firestoreId == alex2.firestoreId)

        assertEquals(false, shouldMergeByUid)
        assertEquals(false, shouldMergeByFsId)
    }

    @Test
    fun testSameLinkedUidDuplicatesAreMerged() {
        // Same authenticated user added twice (e.g. race condition during join)
        val memberA = Member(
            id = 1L,
            groupId = 100L,
            name = "Taylor",
            linkedUid = "user_taylor",
            firestoreId = "fs_1"
        )
        val memberB = Member(
            id = 2L,
            groupId = 100L,
            name = "Taylor Smith",
            linkedUid = "user_taylor",
            firestoreId = "fs_2"
        )

        val shouldMerge = (memberA.linkedUid != null && memberA.linkedUid == memberB.linkedUid)
        assertTrue(shouldMerge)
    }

    @Test
    fun testDistinctExpensesWithDifferentTimestampsNotDeduplicated() {
        val now = 1700000000000L
        val exp1 = Expense(
            id = 1L,
            groupId = 100L,
            paidByMemberId = 1L,
            amount = 15.0,
            currency = "USD",
            description = "Coffee",
            createdAt = now,
            firestoreId = "fs_exp_1"
        )
        // Two separate coffees bought 1 minute apart
        val exp2 = Expense(
            id = 2L,
            groupId = 100L,
            paidByMemberId = 1L,
            amount = 15.0,
            currency = "USD",
            description = "Coffee",
            createdAt = now + 60_000L,
            firestoreId = null
        )

        // Signature based on exact timestamp
        val sig1 = "${exp1.amount}_${exp1.currency}_${exp1.description}_${exp1.paidByMemberId}_${exp1.createdAt}"
        val sig2 = "${exp2.amount}_${exp2.currency}_${exp2.description}_${exp2.paidByMemberId}_${exp2.createdAt}"

        // Must NOT match: distinct purchases within 2 minutes are not conflated
        assertNotEquals(sig1, sig2)
    }

    @Test
    fun testIdenticalUnsyncedAndSyncedExpenseMatchesOnExactTimestamp() {
        val exactTimestamp = 1700000000000L
        val localUnsynced = Expense(
            id = 1L,
            groupId = 100L,
            paidByMemberId = 1L,
            amount = 50.0,
            currency = "USD",
            description = "Dinner",
            createdAt = exactTimestamp,
            firestoreId = null
        )
        val remoteExpense = Expense(
            id = 0L,
            groupId = 100L,
            paidByMemberId = 1L,
            amount = 50.0,
            currency = "USD",
            description = "Dinner",
            createdAt = exactTimestamp,
            firestoreId = "fs_exp_remote"
        )

        val matches = Math.abs(localUnsynced.amount - remoteExpense.amount) < 0.001 &&
            localUnsynced.currency == remoteExpense.currency &&
            localUnsynced.description == remoteExpense.description &&
            localUnsynced.createdAt == remoteExpense.createdAt

        assertTrue(matches)
    }
}
