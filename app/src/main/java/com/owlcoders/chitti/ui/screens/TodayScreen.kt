package com.owlcoders.chitti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.owlcoders.chitti.db.CapturedEvent
import com.owlcoders.chitti.ui.components.ChittiCard
import com.owlcoders.chitti.ui.theme.CorkboardBrown

@Composable
fun TodayScreen(events: List<CapturedEvent>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CorkboardBrown)
            .padding(16.dp)
    ) {
        Text(
            text = "Today's Chitti",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp, top = 24.dp)
        )
        
        if (events.isEmpty()) {
            Text(text = "No commitments caught today. Desk is clean!")
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(events) { event ->
                    ChittiCard(event = event, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
