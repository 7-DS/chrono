package com.chrono.ssh.core.service

import com.hierynomus.mserref.NtStatus
import com.hierynomus.mssmb2.SMB2MessageCommandCode
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.protocol.transport.TransportException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmbFileShareClientTest {
    @Test
    fun staleSmbPredicateAcceptsExpiredSessionStatuses() {
        assertTrue(isStaleSmbSession(apiError(NtStatus.STATUS_NETWORK_SESSION_EXPIRED)))
        assertTrue(isStaleSmbSession(apiError(NtStatus.STATUS_USER_SESSION_DELETED)))
        assertTrue(isStaleSmbSession(apiError(NtStatus.STATUS_NETWORK_NAME_DELETED)))
    }

    @Test
    fun staleSmbPredicateRejectsAuthAndPathStatuses() {
        assertFalse(isStaleSmbSession(apiError(NtStatus.STATUS_ACCESS_DENIED)))
        assertFalse(isStaleSmbSession(apiError(NtStatus.STATUS_LOGON_FAILURE)))
        assertFalse(isStaleSmbSession(apiError(NtStatus.STATUS_OBJECT_NAME_NOT_FOUND)))
    }

    @Test
    fun staleSmbPredicateAcceptsClosedTransportMessages() {
        assertTrue(isStaleSmbSession(TransportException("Connection reset")))
        assertTrue(isStaleSmbSession(TransportException("Broken pipe")))
        assertTrue(isStaleSmbSession(RuntimeException("wrapped", TransportException("Socket closed"))))
    }

    private fun apiError(status: NtStatus): SMBApiException {
        return SMBApiException(status.value, SMB2MessageCommandCode.SMB2_QUERY_DIRECTORY, null)
    }
}
