package com.owlcoders.chitti

import com.owlcoders.chitti.services.NotificationFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationFilterTest {

    @Test
    fun testKeywords() {
        // English
        assertTrue(NotificationFilter.shouldProcess("Don't forget to submit the assignment."))
        assertTrue(NotificationFilter.shouldProcess("The due date is near."))
        assertTrue(NotificationFilter.shouldProcess("Reminder: pay the fee."))
        
        // Hindi
        assertTrue(NotificationFilter.shouldProcess("kal meeting hai"))
        assertTrue(NotificationFilter.shouldProcess("aaj bhej dena"))
        
        // Telugu
        assertTrue(NotificationFilter.shouldProcess("repu class unda?"))
        assertTrue(NotificationFilter.shouldProcess("ivala fee kattu"))
    }

    @Test
    fun testTimePatterns() {
        assertTrue(NotificationFilter.shouldProcess("Let's talk at 10am."))
        assertTrue(NotificationFilter.shouldProcess("Flight is at 14:00."))
        assertTrue(NotificationFilter.shouldProcess("Call me around 2:30 PM"))
    }

    @Test
    fun testDatePatterns() {
        assertTrue(NotificationFilter.shouldProcess("Party on Oct 23"))
        assertTrue(NotificationFilter.shouldProcess("See you on Monday"))
        assertTrue(NotificationFilter.shouldProcess("Deadline is 12/05/2026"))
    }

    @Test
    fun testNegativeCases() {
        assertFalse(NotificationFilter.shouldProcess("hey what's up?"))
        assertFalse(NotificationFilter.shouldProcess("lol that is so funny"))
        assertFalse(NotificationFilter.shouldProcess("how are you doing?"))
        assertFalse(NotificationFilter.shouldProcess("call me later")) // 'later' isn't in keywords right now
        assertFalse(NotificationFilter.shouldProcess("")) // empty string
    }
}
